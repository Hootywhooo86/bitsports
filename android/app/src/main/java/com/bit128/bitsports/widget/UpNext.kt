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

/**
 * A team's next game, for the widgets on days it doesn't play: "UP NEXT · FRI 7:00 PM vs Flames".
 * On game day the widget switches to the game itself. Kept in the app's storage too, so a widget
 * redraw after the phone restarts still has it without going online.
 */
object UpNext {
    val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    private val MONTHS = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")

    /** "TONIGHT 7:00 PM", "TOMORROW 7:00 PM", "FRI 7:00 PM", or "NOV 18 7:00 PM" a week or more out. */
    fun whenText(start: Instant, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val day = start.atZone(zone).toLocalDate()
        val today = now.atZone(zone).toLocalDate()
        // Spelled out by hand, so it reads the same on every phone and every Java version.
        val t = start.atZone(zone)
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        val time = "%d:%02d %s".format(java.util.Locale.ROOT, h, t.minute, if (t.hour < 12) "AM" else "PM")
        val days = Duration.between(today.atStartOfDay(), day.atStartOfDay()).toDays()
        val d = when {
            days <= 0L -> "TONIGHT"
            days == 1L -> "TOMORROW"
            days < 7L -> DAYS[day.dayOfWeek.value - 1]
            else -> "${MONTHS[day.monthValue - 1]} ${day.dayOfMonth}"
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

    /** "LAST W 4-2 VAN": the team's latest result, from its side. */
    fun lastText(team: String, g: Game): String {
        val home = g.home.abbrev == team
        val us = (if (home) g.home.score else g.away.score) ?: 0
        val them = (if (home) g.away.score else g.home.score) ?: 0
        return "LAST ${if (us > them) "W" else "L"} $us-$them ${if (home) g.away.abbrev else g.home.abbrev}"
    }

    fun saveLast(context: Context, team: String, text: String) = prefs(context).edit().putString("$team.last", text).apply()
    fun loadLast(context: Context, team: String): String? = prefs(context).getString("$team.last", null)

    fun saveMeeting(context: Context, team: String, opp: String, gameId: Long, text: String) =
        prefs(context).edit().putString("$team.meet.$opp.$gameId", text).apply()
    fun loadMeeting(context: Context, team: String, opp: String, gameId: Long): String? = prefs(context).getString("$team.meet.$opp.$gameId", null)

    /** When it was last looked up, to refetch a few times a day (times and dates can move). */
    fun fetchedAt(context: Context, team: String): Long = prefs(context).getLong("$team.at", 0L)
}
