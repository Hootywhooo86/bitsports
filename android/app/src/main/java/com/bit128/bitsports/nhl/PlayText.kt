package com.bit128.bitsports.nhl

// Plain words for each play in the feed, newest first on the Plays tab.
object PlayText {
    /** Plays worth a line; the rest (period-start, delayed-penalty, game-end bookkeeping) are skipped. */
    private val SHOWN = setOf(
        "goal", "shot-on-goal", "missed-shot", "blocked-shot", "hit", "faceoff", "penalty",
        "giveaway", "takeaway", "stoppage", "period-end",
    )

    data class Line(val play: Play, val teamAbbrev: String?, val text: String)

    fun feed(d: GameDetail, limit: Int = 60): List<Line> = d.plays
        .filter { it.type in SHOWN && it.periodType != "SO" }
        .takeLast(limit)
        .reversed()
        .map { p -> Line(p, team(d, p.teamId), describe(d, p)) }

    private fun team(d: GameDetail, id: Int?) = when (id) {
        null -> null
        d.game.home.id -> d.game.home.abbrev
        d.game.away.id -> d.game.away.abbrev
        else -> null
    }

    fun describe(d: GameDetail, p: Play): String {
        val det = p.details
        fun who(key: String) = if (det.has(key)) d.roster[det.optInt(key)] ?: "?" else null
        val shot = p.shotType?.let { " ($it)" }.orEmpty()
        return when (p.type) {
            "goal" -> "GOAL! ${who("scoringPlayerId") ?: "Unknown"}$shot" +
                listOfNotNull(who("assist1PlayerId"), who("assist2PlayerId")).takeIf { it.isNotEmpty() }
                    ?.joinToString(", ", prefix = ", from ").orEmpty()
            "shot-on-goal" -> "Shot by ${who("shootingPlayerId") ?: "?"}$shot" + (who("goalieInNetId")?.let { ", saved by $it" }.orEmpty())
            "missed-shot" -> "${who("shootingPlayerId") ?: "Shot"} misses" + reason(det.optString("reason"))
            "blocked-shot" -> "${who("blockingPlayerId") ?: "Someone"} blocks ${who("shootingPlayerId") ?: "a shot"}"
            "hit" -> "${who("hittingPlayerId") ?: "?"} hits ${who("hitteePlayerId") ?: "?"}"
            "faceoff" -> "${who("winningPlayerId") ?: "?"} wins the draw vs ${who("losingPlayerId") ?: "?"}"
            "penalty" -> {
                val mins = det.optInt("duration", 2)
                val what = det.optString("descKey").replace('-', ' ').ifBlank { "penalty" }
                "${who("committedByPlayerId") ?: who("servedByPlayerId") ?: "Bench"}: $mins min, $what" +
                    (who("drawnByPlayerId")?.let { " (drawn by $it)" }.orEmpty())
            }
            "giveaway" -> "Giveaway by ${who("playerId") ?: "?"}"
            "takeaway" -> "Takeaway by ${who("playerId") ?: "?"}"
            "stoppage" -> "Stoppage" + reason(det.optString("reason"))
            "period-end" -> "End of the period"
            else -> p.type.replace('-', ' ')
        }
    }

    private fun reason(r: String) = if (r.isBlank()) "" else " · " + r.replace('-', ' ')
}
