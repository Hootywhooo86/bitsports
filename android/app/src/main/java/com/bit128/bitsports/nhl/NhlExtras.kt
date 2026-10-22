package com.bit128.bitsports.nhl

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// Everything the game screen shows beyond the play-by-play: lineups and box score
// (gamecenter/boxscore), the pregame matchup (gamecenter/landing + right-rail) and
// line changes (stats shiftcharts). Every field is optional because the feed fills
// these in at different times: matchup days before, lineups near puck drop, shifts live.

data class GameExtras(
    val venue: String? = null,
    val tv: List<String> = emptyList(),
    val away: TeamExtras = TeamExtras(),
    val home: TeamExtras = TeamExtras(),
    val seriesAwayWins: Int? = null,
    val seriesHomeWins: Int? = null,
    val leaders: List<LeaderPair> = emptyList(),
    /** Tonight's team stats once the game is on; season stats before it. */
    val gameStats: List<StatPair> = emptyList(),
    val seasonStats: List<StatPair> = emptyList(),
    val officials: List<String> = emptyList(),
    val shifts: List<Shift> = emptyList(),
) {
    val hasLineups get() = away.skaters.isNotEmpty() || home.skaters.isNotEmpty()
}

data class TeamExtras(
    val record: String? = null,
    val last10: String? = null,
    val streak: String? = null,
    /** Oldest first: "W", "L" or "OTL". */
    val recent: List<String> = emptyList(),
    val coach: String? = null,
    val scratches: List<String> = emptyList(),
    val skaters: List<Skater> = emptyList(),
    val goalies: List<GoalieLine> = emptyList(),
    val goalieSeason: List<GoalieSeason> = emptyList(),
    val seasonSkaters: List<Skater> = emptyList(),
)

data class Skater(
    val id: Int,
    val number: Int?,
    val name: String,
    val pos: String,
    val goals: Int = 0,
    val assists: Int = 0,
    val points: Int = 0,
    val sog: Int = 0,
    val hits: Int = 0,
    val plusMinus: Int = 0,
    val toi: String? = null,
    val gamesPlayed: Int? = null,
)

data class GoalieLine(
    val id: Int,
    val number: Int?,
    val name: String,
    val saves: Int,
    val shotsAgainst: Int,
    val toi: String,
    val starter: Boolean,
)

data class GoalieSeason(val name: String, val number: Int?, val record: String, val gaa: Double, val svPct: Double)

data class LeaderPair(val label: String, val awayName: String, val awayValue: String, val homeName: String, val homeValue: String)

/** One compared stat. [awayShare] is 0..1, how much of the bar the away team gets. */
data class StatPair(val label: String, val away: String, val home: String, val awayShare: Float)

data class Shift(val playerId: Int, val teamAbbrev: String, val name: String, val period: Int, val start: Int, val end: Int)

object NhlExtrasParser {
    fun extras(landing: String?, rail: String?, box: String?, shifts: String?): GameExtras {
        val l = landing?.let(::JSONObject)
        val r = rail?.let(::JSONObject)
        val b = box?.let(::JSONObject)
        val awayId = (l ?: b)?.optJSONObject("awayTeam")?.optInt("id")
        val homeId = (l ?: b)?.optJSONObject("homeTeam")?.optInt("id")
        val matchup = l?.optJSONObject("matchup")
        val seasonSkaters = matchup?.optJSONObject("skaterSeasonStats")?.optJSONArray("skaters").objects()
            .map { it to seasonSkater(it) }
        fun team(side: String, id: Int?): TeamExtras {
            val last10 = r?.optJSONObject("last10Record")?.optJSONObject(side)
            val info = r?.optJSONObject("gameInfo")?.optJSONObject(side)
            val players = b?.optJSONObject("playerByGameStats")?.optJSONObject(side)
            return TeamExtras(
                record = l?.optJSONObject(side)?.optString("record")?.ifBlank { null },
                last10 = last10?.optString("record")?.ifBlank { null },
                streak = last10?.let { s -> s.optString("streakType").ifBlank { null }?.let { "$it${s.optInt("streak")}" } },
                recent = last10?.optJSONArray("pastGameResults").objects().map { it.optString("gameResult") }.reversed(),
                coach = info?.optJSONObject("headCoach")?.optString("default")?.ifBlank { null },
                scratches = info?.optJSONArray("scratches").objects().map { "${text(it, "firstName")} ${text(it, "lastName")}".trim() },
                skaters = (players?.optJSONArray("forwards").objects() + players?.optJSONArray("defense").objects()).map(::skater),
                goalies = players?.optJSONArray("goalies").objects().map(::goalie),
                goalieSeason = matchup?.optJSONObject("goalieComparison")?.optJSONObject(side)?.optJSONArray("leaders").objects().map(::goalieSeason),
                seasonSkaters = seasonSkaters.filter { (o, _) -> o.optInt("teamId") == id }.map { it.second }
                    .sortedByDescending { it.points },
            )
        }
        val wins = r?.optJSONObject("seasonSeriesWins")
        val officials = listOf("referees", "linesmen").flatMap { k ->
            r?.optJSONObject("gameInfo")?.optJSONArray(k).objects().map { text(it, "fullName") }
        }.filter { it.isNotBlank() }
        return GameExtras(
            venue = l?.optJSONObject("venue")?.optString("default")?.ifBlank { null },
            tv = l?.optJSONArray("tvBroadcasts").objects().map { it.optString("network") }.filter { it.isNotBlank() }.distinct(),
            away = team("awayTeam", awayId),
            home = team("homeTeam", homeId),
            seriesAwayWins = wins?.optInt("awayTeamWins"),
            seriesHomeWins = wins?.optInt("homeTeamWins"),
            leaders = matchup?.optJSONObject("skaterComparison")?.optJSONArray("leaders").objects().mapNotNull(::leader),
            gameStats = r?.optJSONArray("teamGameStats").objects().mapNotNull(::gameStat),
            seasonStats = seasonStats(r?.optJSONObject("teamSeasonStats")),
            officials = officials,
            shifts = shifts?.let(::shifts).orEmpty(),
        )
    }

    fun shifts(json: String): List<Shift> = JSONObject(json).optJSONArray("data").objects()
        .filter { it.optInt("typeCode") == 517 && !it.isNull("startTime") && !it.isNull("endTime") }
        .mapNotNull { o ->
            val start = seconds(o.optString("startTime")) ?: return@mapNotNull null
            val end = seconds(o.optString("endTime")) ?: return@mapNotNull null
            Shift(
                playerId = o.optInt("playerId"),
                teamAbbrev = o.optString("teamAbbrev"),
                name = "${o.optString("firstName")} ${o.optString("lastName")}".trim(),
                period = o.optInt("period"),
                start = start,
                end = end,
            )
        }

    /** "12:34" → 754. */
    fun seconds(mmss: String?): Int? {
        val parts = mmss?.split(":") ?: return null
        if (parts.size != 2) return null
        val m = parts[0].toIntOrNull() ?: return null
        val s = parts[1].toIntOrNull() ?: return null
        return m * 60 + s
    }

    private fun skater(o: JSONObject) = Skater(
        id = o.optInt("playerId"),
        number = o.optIntOrNull("sweaterNumber"),
        name = text(o, "name"),
        pos = o.optString("position"),
        goals = o.optInt("goals"),
        assists = o.optInt("assists"),
        points = o.optInt("points"),
        sog = o.optInt("sog"),
        hits = o.optInt("hits"),
        plusMinus = o.optInt("plusMinus"),
        toi = o.optString("toi").ifBlank { null },
    )

    private fun seasonSkater(o: JSONObject) = Skater(
        id = o.optInt("playerId"),
        number = o.optIntOrNull("sweaterNumber"),
        name = text(o, "name"),
        pos = o.optString("position"),
        goals = o.optInt("goals"),
        assists = o.optInt("assists"),
        points = o.optInt("points"),
        sog = o.optInt("shots"),
        hits = o.optInt("hits"),
        plusMinus = o.optInt("plusMinus"),
        toi = o.optString("avgTimeOnIce").ifBlank { null },
        gamesPlayed = o.optInt("gamesPlayed"),
    )

    private fun goalie(o: JSONObject) = GoalieLine(
        id = o.optInt("playerId"),
        number = o.optIntOrNull("sweaterNumber"),
        name = text(o, "name"),
        saves = o.optInt("saves"),
        shotsAgainst = o.optInt("shotsAgainst"),
        toi = o.optString("toi", "00:00"),
        starter = o.optBoolean("starter"),
    )

    private fun goalieSeason(o: JSONObject) = GoalieSeason(
        name = text(o, "name"),
        number = o.optIntOrNull("sweaterNumber"),
        record = o.optString("record"),
        gaa = o.optDouble("gaa", 0.0),
        svPct = o.optDouble("savePctg", 0.0),
    )

    private fun leader(o: JSONObject): LeaderPair? {
        val a = o.optJSONObject("awayLeader") ?: return null
        val h = o.optJSONObject("homeLeader") ?: return null
        val label = when (val c = o.optString("category")) {
            "points" -> "Points"
            "goals" -> "Goals"
            "assists" -> "Assists"
            "plusMinus" -> "+/-"
            else -> c.replaceFirstChar { it.uppercase() }
        }
        fun value(p: JSONObject) = if (p.has("value")) p.opt("value")?.toString() ?: "–" else "–"
        return LeaderPair(label, text(a, "name"), value(a), text(h, "name"), value(h))
    }

    private val GAME_STATS = linkedMapOf(
        "sog" to "Shots",
        "faceoffWinningPctg" to "Faceoffs",
        "powerPlay" to "Power play",
        "hits" to "Hits",
        "blockedShots" to "Blocks",
        "pim" to "Penalty min",
        "giveaways" to "Giveaways",
        "takeaways" to "Takeaways",
    )

    private fun gameStat(o: JSONObject): StatPair? {
        val key = o.optString("category")
        val label = GAME_STATS[key] ?: return null
        val a = o.opt("awayValue")
        val h = o.opt("homeValue")
        return if (key == "faceoffWinningPctg") {
            val av = (a as? Number)?.toDouble() ?: 0.0
            val hv = (h as? Number)?.toDouble() ?: 0.0
            StatPair(label, pct(av), pct(hv), share(av, hv))
        } else if (key == "powerPlay") {
            // "1/5": share by goals scored on the power play.
            fun goals(v: Any?) = v?.toString()?.substringBefore("/")?.toDoubleOrNull() ?: 0.0
            StatPair(label, a?.toString() ?: "–", h?.toString() ?: "–", share(goals(a), goals(h)))
        } else {
            val av = (a as? Number)?.toDouble() ?: 0.0
            val hv = (h as? Number)?.toDouble() ?: 0.0
            StatPair(label, av.toInt().toString(), hv.toInt().toString(), share(av, hv))
        }
    }

    private fun seasonStats(o: JSONObject?): List<StatPair> {
        val a = o?.optJSONObject("awayTeam") ?: return emptyList()
        val h = o.optJSONObject("homeTeam") ?: return emptyList()
        fun pair(label: String, key: String, asPct: Boolean): StatPair {
            val av = a.optDouble(key, 0.0)
            val hv = h.optDouble(key, 0.0)
            val fmt = { v: Double -> if (asPct) pct(v) else String.format(Locale.US, "%.2f", v) }
            return StatPair(label, fmt(av), fmt(hv), share(av, hv))
        }
        return listOf(
            pair("Goals for / game", "goalsForPerGamePlayed", false),
            pair("Goals against / game", "goalsAgainstPerGamePlayed", false),
            pair("Power play", "ppPctg", true),
            pair("Penalty kill", "pkPctg", true),
            pair("Faceoffs", "faceoffWinningPctg", true),
        )
    }

    private fun pct(fraction: Double) = String.format(Locale.US, "%.1f%%", fraction * 100)
    private fun share(a: Double, h: Double) = if (a + h <= 0.0) 0.5f else (a / (a + h)).toFloat()

    private fun text(o: JSONObject, key: String): String =
        o.optJSONObject(key)?.optString("default").orEmpty().ifBlank { o.optString(key).takeIf { !it.startsWith("{") }.orEmpty() }

    private fun JSONObject.optIntOrNull(key: String) = if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/** Who's on the ice right now, worked out from the shift chart. */
object OnIce {
    data class Snapshot(
        val away: List<Shift>,
        val home: List<Shift>,
        val period: Int,
        /** Seconds into the period the shift chart covers. */
        val asOf: Int,
        /** The moment shown, as the game clock reads it ("10:00" left). */
        val clock: String,
        /** The chart is more than ~90s behind the game clock. */
        val lagging: Boolean,
    )

    fun periodLength(gameId: Long, periodType: String): Int {
        val gameType = (gameId / 10_000) % 100 // 2026 02 0163: 02 = regular season, 03 = playoffs
        return if (periodType == "OT" && gameType == 2L) 300 else 1200
    }

    fun snapshot(game: Game, shifts: List<Shift>): Snapshot? {
        val c = game.clock ?: return null
        if (!game.state.isLive || c.inIntermission || c.periodType == "SO") return null
        val remaining = NhlExtrasParser.seconds(c.timeRemaining) ?: return null
        val elapsed = (periodLength(game.id, c.periodType) - remaining).coerceAtLeast(0)
        val inPeriod = shifts.filter { it.period == c.period }
        if (inPeriod.isEmpty()) return null
        val asOf = inPeriod.maxOf { it.end }
        val t = minOf(elapsed, asOf)
        // A change happens at one instant, so the outgoing shift's end equals the next one's start:
        // prefer the shift starting at t, and keep a shift ending at t only if it's the latest data.
        val on = inPeriod.filter { it.start <= t && (it.end > t || it.end == asOf) }
            .groupBy { it.playerId }.map { (_, s) -> s.maxBy { it.start } }
        val away = on.filter { it.teamAbbrev == game.away.abbrev }
        val home = on.filter { it.teamAbbrev == game.home.abbrev }
        if (away.isEmpty() && home.isEmpty()) return null
        return Snapshot(
            away = away,
            home = home,
            period = c.period,
            asOf = asOf,
            clock = (periodLength(game.id, c.periodType) - t).let { "%d:%02d".format(it / 60, it % 60) },
            lagging = elapsed - asOf > 90,
        )
    }
}
