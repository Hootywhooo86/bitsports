package com.bit128.bitsports.ai

import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.nhl.Strength

// Builds the prompt for an AI period recap. The model sees only this period's real
// events and stats, and is told to invent nothing.
object RecapPrompt {
    private const val SYSTEM =
        "You are the bitsports hockey recap writer. Write a punchy, fun recap of one period for a fan, in 3-4 sentences. " +
            "Only use facts listed in the message: never add players, numbers, or events that are not there. " +
            "No emojis, no hashtags, no headline."

    fun build(d: GameDetail, period: Int): Pair<String, String> {
        val away = NhlTeams.byAbbrev(d.game.away.abbrev)?.fullName ?: d.game.away.abbrev
        val home = NhlTeams.byAbbrev(d.game.home.abbrev)?.fullName ?: d.game.home.abbrev
        val name = { abbrev: String -> if (abbrev == d.game.home.abbrev) home else away }
        val stats = GameAnalysis.periodStats(d, period)
        val label = AlertPlanner.periodLabel(period, stats.periodType)
        val goals = GameAnalysis.goals(d)
        val scoreAfter = goals.lastOrNull { it.period <= period }
        val periodGoals = goals.filter { it.period == period }
        val penalties = d.plays.filter { it.period == period && it.type == "penalty" }

        val user = buildString {
            appendLine("Game: $away at $home")
            appendLine("Period: $label")
            appendLine("Score after the $label: $away ${scoreAfter?.awayScore ?: 0}, $home ${scoreAfter?.homeScore ?: 0}")
            appendLine("Shots on goal: $away ${stats.awayShots}, $home ${stats.homeShots}")
            appendLine("Hits: $away ${stats.awayHits}, $home ${stats.homeHits}")
            appendLine("Faceoffs won: $away ${stats.awayFaceoffPct}%, $home ${stats.homeFaceoffPct}%")
            appendLine("Power play goals/chances: $away ${stats.awayPp}, $home ${stats.homePp}")
            if (periodGoals.isEmpty()) appendLine("No goals this period.")
            else {
                appendLine("Goals this period:")
                periodGoals.forEach { g ->
                    val assists = if (g.assists.isEmpty()) "unassisted" else "assists " + g.assists.joinToString(" and ") { it.first }
                    appendLine("- ${g.timeInPeriod} ${name(g.teamAbbrev)}: ${g.scorer} (${strengthText(g.strength)}, ${g.shotType ?: "shot"}, $assists)")
                }
            }
            if (penalties.isNotEmpty()) {
                appendLine("Penalties this period:")
                penalties.forEach { p ->
                    val who = d.roster[p.details.optInt("committedByPlayerId", -1)] ?: "a player"
                    val what = p.details.optString("descKey").replace('-', ' ')
                    appendLine("- ${p.timeInPeriod} $who, ${p.details.optInt("duration")} min for $what")
                }
            }
        }.trim()
        return SYSTEM to user
    }

    fun strengthText(s: Strength) = when (s) {
        Strength.EV -> "even strength"
        Strength.PP -> "power play"
        Strength.SH -> "shorthanded"
        Strength.EN -> "empty net"
    }
}
