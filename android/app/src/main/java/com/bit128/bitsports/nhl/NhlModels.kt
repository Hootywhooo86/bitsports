package com.bit128.bitsports.nhl

import org.json.JSONObject
import java.time.Instant

// One normalized shape for the NHL feed (api-web.nhle.com). Everything downstream —
// scores, the live screen, alerts — reads these, never raw JSON.

enum class GameState { FUT, PRE, LIVE, CRIT, OFF, FINAL;
    val isLive get() = this == LIVE || this == CRIT
    val isOver get() = this == OFF || this == FINAL
}

data class TeamLine(val id: Int, val abbrev: String, val score: Int?, val sog: Int?)

data class Clock(
    val period: Int,
    val periodType: String,
    val timeRemaining: String,
    val inIntermission: Boolean,
    val running: Boolean,
)

data class Game(
    val id: Long,
    val state: GameState,
    val scheduleOk: Boolean,
    val startUtc: Instant,
    val away: TeamLine,
    val home: TeamLine,
    val clock: Clock?,
)

data class Play(
    val eventId: Int,
    val type: String,
    val period: Int,
    val periodType: String,
    val timeInPeriod: String,
    val teamId: Int?,
    val x: Int?,
    val y: Int?,
    val shotType: String?,
    val situationCode: String?,
    val details: JSONObject,
)

data class GameDetail(
    val game: Game,
    val plays: List<Play>,
    val roster: Map<Int, String>,
    val threeStars: List<String>,
    val fetchedAt: Instant,
    val homeDefendingSide: String? = null,
)

enum class Strength { EV, PP, SH, EN }

data class GoalEvent(
    val gameId: Long,
    val eventId: Int,
    val teamAbbrev: String,
    val scorer: String,
    val scorerTotal: Int?,
    val assists: List<Pair<String, Int?>>,
    val strength: Strength,
    val shotType: String?,
    val goalie: String?,
    val period: Int,
    val periodType: String,
    val timeInPeriod: String,
    val awayScore: Int,
    val homeScore: Int,
    val clipUrl: String?,
)

data class PeriodStats(
    val period: Int,
    val periodType: String,
    val awayShots: Int,
    val homeShots: Int,
    val awayGoals: Int,
    val homeGoals: Int,
    val awayHits: Int,
    val homeHits: Int,
    val awayPp: String,
    val homePp: String,
    val awayFaceoffPct: Int,
    val homeFaceoffPct: Int,
)

data class ShotMark(
    val teamAbbrev: String,
    val x: Int,
    val y: Int,
    val isGoal: Boolean,
    val period: Int,
    val shooter: String?,
)

data class NhlTeam(
    val abbrev: String,
    val city: String,
    val nickname: String,
    val colors: Pair<Long, Long>,
    val aliases: List<String> = emptyList(),
) {
    val fullName get() = "$city $nickname"
}
