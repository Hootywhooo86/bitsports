package com.bit128.bitsports.fantasy

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bit128.bitsports.alerts.AlertLog
import com.bit128.bitsports.alerts.AlertNotifier
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.Injuries
import com.bit128.bitsports.nhl.NhlSource
import com.bit128.bitsports.nhl.StatLine
import com.bit128.bitsports.ui.FantasyReportLeds
import com.bit128.bitsports.ui.LedScenes
import com.bit128.bitsports.ui.PixelArt
import com.bit128.bitsports.ui.HockeyBoard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Keeps an eye on every player on your fantasy lists, whoever they play for, with the app closed:
 *
 *   before puck drop  "Out tonight" if one of them is scratched or on the injury list
 *   during the game   an alert each time one of them scores, sets one up or gets the win
 *   after the last    the Fantasy Report: each list's night as an LED animation
 *
 * Runs on alarms: every 3 minutes while one of their games is on, around puck drop for the
 * scratch check, and from the morning schedule (AlertScheduler) otherwise.
 */
object FantasyWatch {
    private const val ACTION = "com.bit128.bitsports.FANTASY"
    private const val LIVE_MS = 3 * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pending = goAsync()
            scope.launch { try { pulse(context.applicationContext) } finally { pending.finish() } }
        }
    }

    /** From the morning schedule, app open and list changes. */
    fun schedule(context: Context) {
        val app = context.applicationContext
        scope.launch { pulse(app) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("bitsports.fantasywatch", Context.MODE_PRIVATE)

    private fun alarm(context: Context, at: Instant) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(context, 4_128, Intent(context, Receiver::class.java).setAction(ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (AlertScheduler.canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), 60_000L, pi)
    }

    suspend fun pulse(context: Context, source: NhlSource = DirectNhlSource()) = lock.withLock {
        val store = FantasyStore(context)
        val players = store.allPlayers()
        if (players.isEmpty()) return@withLock
        val games = try { source.scoreboard() } catch (e: Exception) { alarm(context, Instant.now().plusMillis(LIVE_MS)); return@withLock }
        val teams = players.map { it.team }.toSet()
        val mine = games.filter { it.away.abbrev in teams || it.home.abbrev in teams }
        if (mine.isEmpty()) return@withLock
        val now = Instant.now()
        val day = LocalDate.now().toString()
        val p = prefs(context)

        // Before puck drop: scratches and the injury list.
        val soon = mine.filter { !it.state.isLive && !it.state.isOver && Duration.between(now, it.startUtc).toMinutes() in -5..75 }
        if (soon.isNotEmpty()) checkOut(context, source, soon, day)

        // During and after: the box scores.
        val lines = HashMap<Int, StatLine>()
        for (g in mine.filter { it.state.isLive || it.state.isOver }) {
            try { lines.putAll(source.boxScore(g.id)) } catch (e: Exception) { }
        }
        val scoring = store.scoring()
        val e = p.edit()
        for (pl in players) {
            val l = lines[pl.id] ?: continue
            val key = "snap.${pl.id}.${l.gameId}"
            val before = p.getString(key, null)?.split(",")?.map { it.toIntOrNull() ?: 0 }
            e.putString(key, "${l.goals},${l.assists},${if (l.decision == "W") 1 else 0}")
            val game = mine.firstOrNull { it.id == l.gameId }
            // First look at a game already under way: catch up quietly.
            if (before == null) continue
            val what = buildList {
                repeat((l.goals - before[0]).coerceAtLeast(0)) { add("GOAL") }
                if (l.assists > before[1]) add(if (l.assists - before[1] > 1) "${l.assists - before[1]} ASSISTS" else "ASSIST")
                if (pl.isGoalie && l.decision == "W" && before.getOrElse(2) { 0 } == 0) add(if (l.goalsAgainst == 0) "SHUTOUT" else "WIN")
            }
            if (what.isEmpty() || game == null) continue
            val row = FantasyNight.Row(pl, game, l, scoring.points(l, pl.isGoalie))
            val headline = what.first()
            val title = "${pl.lastName}: ${what.joinToString(" + ").lowercase().replaceFirstChar { it.uppercase() }}!"
            val text = "${FantasyNight.statText(l, pl.isGoalie)} · ${Scoring.fmt(row.points)} fantasy pts tonight"
            AlertNotifier.fantasy(context, ("fa" + pl.id + l.gameId + l.goals + l.assists).hashCode(), title, text,
                LedScenes.Scene(listOf(FantasyReportLeds.alert(row, headline), FantasyReportLeds.player(row, mvp = false)),
                    listOf(LedScenes.strip("${pl.lastName.uppercase()} $headline", PixelArt.GOLD), LedScenes.strip(Scoring.fmt(row.points) + " PTS", HockeyBoard.WHITE))),
                loud = headline == "GOAL" || headline == "SHUTOUT")
        }
        e.apply()

        // After the last game with one of your players: the Fantasy Report, once a night.
        if (mine.all { it.state.isOver } && p.getString("reported", null) != day && lines.isNotEmpty()) {
            val label = FantasyReportLeds.dateLabel(LocalDate.now())
            for (list in store.lists().filter { it.players.isNotEmpty() }) {
                val night = FantasyNight.night(list, mine, lines, scoring)
                if (night.rows.none { it.line != null }) continue
                val mvp = night.mvp
                AlertNotifier.fantasy(context, ("fr" + list.id + day).hashCode(),
                    "Fantasy Report · ${list.name}: ${Scoring.fmt(night.total)} pts",
                    mvp?.let { "MVP ${it.player.lastName}, ${Scoring.fmt(it.points)} pts (${FantasyNight.statText(it.line, it.player.isGoalie)})" } ?: "A quiet night.",
                    LedScenes.Scene(FantasyReportLeds.frames(night, label), listOf(
                        LedScenes.strip("FANTASY REPORT", PixelArt.GOLD),
                        LedScenes.strip("${list.name.uppercase()} ${Scoring.fmt(night.total)} PTS", HockeyBoard.WHITE),
                    )))
            }
            p.edit().putString("reported", day).putString("report.pending", day).apply()
            AlertLog(context).sent("Fantasy Report")
        }

        // Next look: every 3 minutes while a game's on or about to start; at the next warm-up otherwise.
        val live = mine.any { it.state.isLive } || soon.isNotEmpty()
        val nextStart = mine.filter { !it.state.isLive && !it.state.isOver }.minOfOrNull { it.startUtc }
        when {
            live -> alarm(context, now.plusMillis(LIVE_MS))
            nextStart != null -> alarm(context, maxOf(nextStart.minus(Duration.ofMinutes(75)), now.plusMillis(LIVE_MS)))
        }
    }

    /** Whether tonight's report is waiting to be shown in the app (once). */
    fun pendingReport(context: Context): String? = prefs(context).getString("report.pending", null)
    fun reportShown(context: Context) = prefs(context).edit().remove("report.pending").apply()

    private suspend fun checkOut(context: Context, source: NhlSource, soon: List<Game>, day: String) {
        val p = prefs(context)
        val players = FantasyStore(context).allPlayers()
        val injuries = try { source.injuries() } catch (e: Exception) { emptyList() }
        for (g in soon) {
            val scratches = try { (source.extras(g.id).let { it.away.scratches + it.home.scratches }) } catch (e: Exception) { emptyList() }
            for (pl in players.filter { it.team == g.away.abbrev || it.team == g.home.abbrev }) {
                val key = "out.$day.${pl.id}"
                if (p.getBoolean(key, false)) continue
                val inj = Injuries.find(injuries, pl)?.takeIf { it.status.contains("Out", true) || it.status.contains("Reserve", true) || it.status.contains("Day", true) }
                val scratched = Injuries.scratched(scratches, pl)
                if (inj == null && !scratched) continue
                val title = if (scratched) "${pl.lastName} is scratched tonight" else "${pl.lastName}: ${inj!!.status}"
                val text = listOfNotNull("${pl.team} ${if (g.home.abbrev == pl.team) "vs" else "@"} ${if (g.home.abbrev == pl.team) g.away.abbrev else g.home.abbrev}",
                    inj?.comment).joinToString(" · ")
                AlertNotifier.fantasy(context, ("fo" + pl.id + day).hashCode(), title, text,
                    LedScenes.Scene(listOf(FantasyReportLeds.out(pl, if (scratched) "SCRATCHED" else inj!!.status.uppercase())),
                        listOf(LedScenes.strip("OUT ${pl.lastName.uppercase()}", HockeyBoard.LIVE))))
                p.edit().putBoolean(key, true).apply()
            }
        }
    }
}
