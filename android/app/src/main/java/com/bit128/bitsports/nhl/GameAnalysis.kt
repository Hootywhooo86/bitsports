package com.bit128.bitsports.nhl

import kotlin.math.roundToInt

// Turns a play-by-play into the things people care about: goals with full info,
// per-period stats, and shot locations. Shootout attempts are not goals.
object GameAnalysis {
    private val SHOT_TYPES = setOf("shot-on-goal", "goal")
    private val PP_PENALTIES = setOf("MIN", "MAJ", "BEN")

    fun goals(d: GameDetail): List<GoalEvent> = d.plays
        .filter { it.type == "goal" && it.periodType != "SO" }
        .map { p ->
            val det = p.details
            val isHome = p.teamId == d.game.home.id
            fun person(key: String) = if (det.has(key)) d.roster[det.optInt(key)] else null
            fun total(key: String) = if (det.has(key)) det.optInt(key) else null
            val assists = listOf(1, 2).mapNotNull { n ->
                person("assist${n}PlayerId")?.let { it to total("assist${n}PlayerTotal") }
            }
            GoalEvent(
                gameId = d.game.id,
                eventId = p.eventId,
                teamAbbrev = if (isHome) d.game.home.abbrev else d.game.away.abbrev,
                scorer = person("scoringPlayerId") ?: "Unknown",
                scorerTotal = total("scoringPlayerTotal"),
                assists = assists,
                strength = strength(p.situationCode, isHome),
                shotType = p.shotType,
                goalie = person("goalieInNetId"),
                period = p.period,
                periodType = p.periodType,
                timeInPeriod = p.timeInPeriod,
                awayScore = det.optInt("awayScore"),
                homeScore = det.optInt("homeScore"),
                clipUrl = det.optString("highlightClipSharingUrl").ifBlank { null },
            )
        }

    /** situationCode digits: away goalie, away skaters, home skaters, home goalie. */
    fun strength(situationCode: String?, scoringIsHome: Boolean): Strength {
        val c = situationCode?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: return Strength.EV
        val ag = c[0].digitToInt(); val askaters = c[1].digitToInt(); val hskaters = c[2].digitToInt(); val hg = c[3].digitToInt()
        val oppGoalie = if (scoringIsHome) ag else hg
        if (oppGoalie == 0) return Strength.EN
        val ownGoalie = if (scoringIsHome) hg else ag
        // An extra attacker for a pulled goalie isn't a power play.
        val own = (if (scoringIsHome) hskaters else askaters) - (if (ownGoalie == 0) 1 else 0)
        val opp = if (scoringIsHome) askaters else hskaters
        return when {
            own > opp -> Strength.PP
            own < opp -> Strength.SH
            else -> Strength.EV
        }
    }

    fun periodStats(d: GameDetail, period: Int): PeriodStats {
        val ps = d.plays.filter { it.period == period && it.periodType != "SO" }
        val awayId = d.game.away.id
        val homeId = d.game.home.id
        fun count(type: Set<String>, team: Int) = ps.count { it.type in type && it.teamId == team }
        val faceoffs = ps.filter { it.type == "faceoff" }
        fun foPct(team: Int) = if (faceoffs.isEmpty()) 0 else (faceoffs.count { it.teamId == team } * 100.0 / faceoffs.size).roundToInt()
        // A penalty's owner is the penalized team; the other side gets the power play.
        val penalties = ps.filter { it.type == "penalty" && it.details.optString("typeCode") in PP_PENALTIES }
        val periodGoals = goals(d).filter { it.period == period }
        fun pp(team: Int, abbrev: String): String {
            val chances = penalties.count { it.teamId != null && it.teamId != team }
            val scored = periodGoals.count { it.teamAbbrev == abbrev && it.strength == Strength.PP }
            return "$scored/$chances"
        }
        return PeriodStats(
            period = period,
            periodType = ps.firstOrNull()?.periodType ?: if (period > 3) "OT" else "REG",
            awayShots = count(SHOT_TYPES, awayId),
            homeShots = count(SHOT_TYPES, homeId),
            awayGoals = count(setOf("goal"), awayId),
            homeGoals = count(setOf("goal"), homeId),
            awayHits = count(setOf("hit"), awayId),
            homeHits = count(setOf("hit"), homeId),
            awayPp = pp(awayId, d.game.away.abbrev),
            homePp = pp(homeId, d.game.home.abbrev),
            awayFaceoffPct = foPct(awayId),
            homeFaceoffPct = foPct(homeId),
        )
    }

    fun shots(d: GameDetail): List<ShotMark> = d.plays
        .filter { it.type in SHOT_TYPES && it.periodType != "SO" && it.x != null && it.y != null }
        .map { p ->
            val shooterId = if (p.details.has("scoringPlayerId")) p.details.optInt("scoringPlayerId") else p.details.optInt("shootingPlayerId", -1)
            ShotMark(
                teamAbbrev = if (p.teamId == d.game.home.id) d.game.home.abbrev else d.game.away.abbrev,
                x = p.x!!, y = p.y!!,
                isGoal = p.type == "goal",
                period = p.period,
                shooter = d.roster[shooterId],
            )
        }
}
