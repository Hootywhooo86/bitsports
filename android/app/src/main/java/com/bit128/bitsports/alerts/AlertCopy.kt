package com.bit128.bitsports.alerts

import com.bit128.bitsports.ai.RecapPrompt
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.nhl.PeriodStats
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Every alert's words, in one place so they can be tested.
object AlertCopy {
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun nick(abbrev: String) = NhlTeams.byAbbrev(abbrev)?.nickname ?: abbrev

    fun goalTitle(g: GoalEvent, game: Game): String {
        val scoringIsHome = g.teamAbbrev == game.home.abbrev
        val mine = if (scoringIsHome) g.homeScore else g.awayScore
        val theirs = if (scoringIsHome) g.awayScore else g.homeScore
        return "GOAL! ${nick(g.teamAbbrev)} $mine–$theirs"
    }

    fun goalText(g: GoalEvent): String {
        val total = g.scorerTotal?.let { " ($it)" }.orEmpty()
        return "${g.scorer}$total · ${RecapPrompt.strengthText(g.strength)} · ${AlertPlanner.periodLabel(g.period, g.periodType)} ${g.timeInPeriod}"
    }

    fun goalDetail(g: GoalEvent): String = buildString {
        appendLine(goalText(g))
        appendLine(
            if (g.assists.isEmpty()) "Unassisted"
            else "Assists: " + g.assists.joinToString(", ") { (name, total) -> name + (total?.let { " ($it)" } ?: "") }
        )
        g.shotType?.let { appendLine("Shot: $it") }
        g.goalie?.let { appendLine("On $it") }
    }.trim()

    fun replayTitle(g: GoalEvent) = "▶ Watch ${g.scorer}'s goal"

    private fun scoreLine(game: Game, away: Int, home: Int) =
        "${nick(game.away.abbrev)} $away, ${nick(game.home.abbrev)} $home"

    fun periodTitle(stats: PeriodStats, label: String, game: Game, goals: List<GoalEvent>): String {
        val after = goals.lastOrNull { it.period <= stats.period }
        return "End of $label · ${scoreLine(game, after?.awayScore ?: 0, after?.homeScore ?: 0)}"
    }

    fun periodText(s: PeriodStats) =
        "Shots ${s.awayShots}–${s.homeShots} · PP ${s.awayPp} / ${s.homePp} · Hits ${s.awayHits}–${s.homeHits} · Faceoffs ${s.awayFaceoffPct}%–${s.homeFaceoffPct}%"

    fun finalTitle(game: Game, shootout: Boolean): String {
        val tag = when {
            shootout -> "FINAL/SO"
            game.clock?.periodType == "OT" || (game.clock?.period ?: 0) > 3 -> "FINAL/OT"
            else -> "FINAL"
        }
        return "$tag · ${scoreLine(game, game.away.score ?: 0, game.home.score ?: 0)}"
    }

    fun finalText(threeStars: List<String>) =
        if (threeStars.isEmpty()) "Tap for the full game." else "Stars: " + threeStars.joinToString(", ")

    fun pregameTitle(game: Game) = "${nick(game.away.abbrev)} @ ${nick(game.home.abbrev)} starts in 30 min"

    fun pregameText(game: Game, zone: ZoneId = ZoneId.systemDefault()) =
        "Puck drop " + TIME.format(game.startUtc.atZone(zone)).replace(' ', ' ')

    fun scoreboardLine(game: Game): String {
        val c = game.clock
        return when {
            game.state.isOver -> finalTitle(game, shootout = c?.periodType == "SO").substringBefore(" ·")
            c == null -> ""
            c.inIntermission -> "INT · " + AlertPlanner.periodLabel(c.period, c.periodType)
            else -> AlertPlanner.periodLabel(c.period, c.periodType).uppercase() + " · " + c.timeRemaining
        }
    }
}
