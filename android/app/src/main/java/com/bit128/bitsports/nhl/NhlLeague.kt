package com.bit128.bitsports.nhl

import org.json.JSONArray
import org.json.JSONObject

// League-wide hockey: standings (standings/now) and stat leaders (skater- and goalie-stats-leaders).

data class StandingRow(
    val abbrev: String,
    val name: String,
    val conference: String,
    val division: String,
    val divisionRank: Int,
    val conferenceRank: Int,
    val leagueRank: Int,
    val wildCardRank: Int,
    val gamesPlayed: Int,
    val wins: Int,
    val losses: Int,
    val otLosses: Int,
    val points: Int,
    val goalDiff: Int,
    val l10: String,
    val streak: String?,
) {
    val record get() = "$wins-$losses-$otLosses"
}

data class Leader(val name: String, val number: Int?, val team: String, val pos: String, val value: String)

data class LeaderBoard(val label: String, val leaders: List<Leader>)

object NhlLeagueParser {
    fun standings(json: String): List<StandingRow> =
        (JSONObject(json).optJSONArray("standings") ?: JSONArray()).let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { t ->
                StandingRow(
                    abbrev = text(t, "teamAbbrev"),
                    name = text(t, "teamName"),
                    conference = t.optString("conferenceName"),
                    division = t.optString("divisionName"),
                    divisionRank = t.optInt("divisionSequence"),
                    conferenceRank = t.optInt("conferenceSequence"),
                    leagueRank = t.optInt("leagueSequence"),
                    wildCardRank = t.optInt("wildcardSequence"),
                    gamesPlayed = t.optInt("gamesPlayed"),
                    wins = t.optInt("wins"),
                    losses = t.optInt("losses"),
                    otLosses = t.optInt("otLosses"),
                    points = t.optInt("points"),
                    goalDiff = t.optInt("goalDifferential"),
                    l10 = "${t.optInt("l10Wins")}-${t.optInt("l10Losses")}-${t.optInt("l10OtLosses")}",
                    streak = t.optString("streakCode").ifBlank { null }?.let { it + t.optInt("streakCount") },
                )
            }
        }

    private val SKATER = linkedMapOf("points" to "Points", "goals" to "Goals", "assists" to "Assists")
    private val GOALIE = linkedMapOf("wins" to "Goalie wins", "savePctg" to "Save %", "goalsAgainstAverage" to "Goals against avg")

    fun leaders(skaters: String?, goalies: String?): List<LeaderBoard> {
        fun boards(json: String?, cats: Map<String, String>): List<LeaderBoard> {
            val o = json?.let(::JSONObject) ?: return emptyList()
            return cats.mapNotNull { (key, label) ->
                val a = o.optJSONArray(key) ?: return@mapNotNull null
                LeaderBoard(label, (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { p ->
                    Leader(
                        name = "${text(p, "firstName")} ${text(p, "lastName")}".trim(),
                        number = if (p.has("sweaterNumber")) p.optInt("sweaterNumber") else null,
                        team = p.optString("teamAbbrev"),
                        pos = p.optString("position"),
                        value = value(key, p.opt("value")),
                    )
                })
            }
        }
        return boards(skaters, SKATER) + boards(goalies, GOALIE)
    }

    private fun value(key: String, v: Any?): String {
        val d = (v as? Number)?.toDouble() ?: return v?.toString() ?: "–"
        return when (key) {
            "savePctg" -> ".%03d".format((d * 1000).toInt())
            "goalsAgainstAverage" -> "%.2f".format(d)
            else -> d.toInt().toString()
        }
    }

    private fun text(o: JSONObject, key: String): String =
        o.optJSONObject(key)?.optString("default").orEmpty().ifBlank { o.optString(key).takeIf { !it.startsWith("{") }.orEmpty() }
}
