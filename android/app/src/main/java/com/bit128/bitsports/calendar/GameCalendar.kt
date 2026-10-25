package com.bit128.bitsports.calendar

import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.nhl.NhlRadio
import com.bit128.bitsports.nhl.ScheduleGame

/**
 * What a game looks like on your calendar, from your team's side. Pure, so titles and details
 * are tested; BitcalSync sends them to Bitcal.
 *
 *   coming up   "Oilers vs Hurricanes" at home, "Oilers @ Flames" away
 *   over        "Oilers 6–5 Hurricanes · W (SO)"
 *   postponed   "PPD · Oilers vs Hurricanes"
 */
object GameCalendar {
    /** About how long an NHL game takes, puck drop to final horn. */
    const val LENGTH_MS = (2 * 60 + 40) * 60_000L

    data class Entry(val gameId: Long, val title: String, val description: String, val location: String?, val startMs: Long, val endMs: Long)

    /** Regular season and playoffs; preseason games are skipped. */
    fun wanted(g: ScheduleGame) = g.gameType == 2 || g.gameType == 3

    fun entry(s: ScheduleGame, mine: String, customRadio: String? = null): Entry {
        val g = s.game
        val home = g.home.abbrev == mine
        val us = AlertCopy.nick(mine)
        val them = AlertCopy.nick(if (home) g.away.abbrev else g.home.abbrev)
        val playoff = if (s.gameType == 3) "Playoffs · " else ""
        val title = when {
            !g.scheduleOk -> "PPD · $us ${if (home) "vs" else "@"} $them"
            g.state.isOver -> {
                val ours = (if (home) g.home.score else g.away.score) ?: 0
                val theirs = (if (home) g.away.score else g.home.score) ?: 0
                val result = if (ours > theirs) "W" else if (s.lastPeriodType == "OT" || s.lastPeriodType == "SO") "OTL" else "L"
                val extra = s.lastPeriodType?.takeIf { it == "OT" || it == "SO" }?.let { " ($it)" }.orEmpty()
                "$playoff$us $ours–$theirs $them · $result$extra"
            }
            else -> "$playoff$us ${if (home) "vs" else "@"} $them"
        }
        val radio = NhlRadio.forTeam(mine, customRadio)
        val description = listOfNotNull(
            s.tv.takeIf { it.isNotEmpty() }?.let { "TV: ${it.joinToString(", ")}" },
            if (radio.official || !customRadio.isNullOrBlank()) "Radio: ${radio.name} · ${radio.url}" else null,
            "Live scores, alerts and the full game in bitsports.",
        ).joinToString("\n")
        val start = g.startUtc.toEpochMilli()
        return Entry(g.id, title, description, s.venue, start, start + LENGTH_MS)
    }

    /** Every wanted game for the teams you follow, once each: a game between two of yours is listed from the first. */
    fun plan(seasons: Map<String, List<ScheduleGame>>, customRadio: String? = null): List<Entry> {
        val seen = HashSet<Long>()
        return seasons.flatMap { (abbrev, games) ->
            games.filter(::wanted).filter { seen.add(it.game.id) }.map { entry(it, abbrev, customRadio) }
        }.sortedBy { it.startMs }
    }
}
