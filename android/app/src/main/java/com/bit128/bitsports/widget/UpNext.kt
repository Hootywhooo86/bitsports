package com.bit128.bitsports.widget

import android.content.Context
import com.bit128.bitsports.nhl.Clock
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.TeamLine
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A team's next game, for the widgets on days it doesn't play: "UP NEXT · FRI 7:00 PM vs Flames".
 * On game day the widget switches to the game itself. Kept in the app's storage too, so a widget
 * redraw after the phone restarts still has it without going online.
 */
object UpNext {
    /** "TONIGHT 7:00 PM", "TOMORROW 7:00 PM", "FRI 7:00 PM", or "NOV 18 7:00 PM" a week or more out. */
    fun whenText(start: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = start.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        val time = DateTimeFormatter.ofPattern("h:mm a", Locale.CANADA).format(start.atZone(zone)).uppercase().replace(".", "")
        val days = Duration.between(today.atStartOfDay(), day.atStartOfDay()).toDays()
        val d = when {
            days <= 0L -> "TONIGHT"
            days == 1L -> "TOMORROW"
            days < 7L -> DateTimeFormatter.ofPattern("EEE", Locale.CANADA).format(day).uppercase().replace(".", "")
            else -> DateTimeFormatter.ofPattern("MMM d", Locale.CANADA).format(day).uppercase().replace(".", "")
        }
        return "$d $time"
    }

    /** "in 2 days", "tomorrow", "in 5h". */
    fun inText(start: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val days = Duration.between(now.atZone(zone).toLocalDate().atStartOfDay(), start.atZone(zone).toLocalDate().atStartOfDay()).toDays()
        val hours = Duration.between(now, start).toHours()
        return when {
            days <= 0L -> "in ${hours.coerceAtLeast(0)}h"
            days == 1L -> "tomorrow"
            else -> "in $days days"
        }
    }

    /** Whether [g] is today (local), i.e. game day. */
    fun isGameDay(g: Game, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean =
        g.startUtc.atZone(zone).toLocalDate() == now.atZone(zone).toLocalDate()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("bitsports.upnext", Context.MODE_PRIVATE)

    fun save(context: Context, team: String, g: Game) {
        prefs(context).edit().putString(team, listOf(g.id, g.startUtc.toEpochMilli(), g.away.id, g.away.abbrev, g.home.id, g.home.abbrev).joinToString("|"))
            .putLong("$team.at", System.currentTimeMillis()).apply()
    }

    /** The saved next game, if it's still ahead. */
    fun load(context: Context, team: String, now: Instant = Instant.now()): Game? {
        val p = prefs(context).getString(team, null)?.split("|")?.takeIf { it.size == 6 } ?: return null
        val start = Instant.ofEpochMilli(p[1].toLongOrNull() ?: return null)
        if (start.isBefore(now.minusSeconds(4 * 3600))) return null
        return Game(p[0].toLong(), GameState.FUT, true, start, TeamLine(p[2].toInt(), p[3], null, null), TeamLine(p[4].toInt(), p[5], null, null), null as Clock?)
    }

    /** When it was last looked up, to refetch a few times a day (times and dates can move). */
    fun fetchedAt(context: Context, team: String): Long = prefs(context).getLong("$team.at", 0L)
}
