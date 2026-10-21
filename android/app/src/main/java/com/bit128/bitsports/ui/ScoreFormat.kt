package com.bit128.bitsports.ui

import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.nhl.Game
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Score-line words and the order games appear in. Same rules as the web page. */
object ScoreFormat {
    private val TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun status(g: Game, zone: ZoneId = ZoneId.systemDefault()): String {
        val c = g.clock
        return when {
            !g.scheduleOk -> "POSTPONED"
            g.state.isOver -> when {
                c?.periodType == "SO" -> "FINAL/SO"
                c?.periodType == "OT" || (c?.period ?: 0) > 3 -> "FINAL/OT"
                else -> "FINAL"
            }
            g.state.isLive && c != null -> {
                val p = AlertPlanner.periodLabel(c.period, c.periodType).uppercase()
                if (c.inIntermission) "INT · $p" else "LIVE · $p ${c.timeRemaining}"
            }
            else -> TIME.format(g.startUtc.atZone(zone)).replace(' ', ' ')
        }
    }

    fun order(games: List<Game>, followed: Set<String>): List<Game> = games.sortedWith(
        compareBy<Game>(
            { if (it.away.abbrev in followed || it.home.abbrev in followed) 0 else 1 },
            { when { it.state.isLive -> 0; it.state.isOver -> 2; else -> 1 } },
            { it.startUtc },
        )
    )
}
