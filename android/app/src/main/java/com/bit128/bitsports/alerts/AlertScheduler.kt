package com.bit128.bitsports.alerts

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bit128.bitsports.PrefsStore
import com.bit128.bitsports.TeamsStore
import com.bit128.bitsports.alertPrefs
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.NhlTeams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Checks today's NHL schedule for the teams you follow and sets two alarms per game:
 * the 30-minute heads-up and the start of live watching 15 minutes before puck drop.
 * Runs when the app opens, every morning at 09:00, and after the phone restarts.
 */
object AlertScheduler {
    private const val ACTION_PREGAME = "com.bit128.bitsports.PREGAME"
    private const val ACTION_WATCH = "com.bit128.bitsports.WATCH"
    private const val ACTION_DAILY = "com.bit128.bitsports.DAILY"
    private const val EXTRA_GAME = "gameId"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun followedAbbrevs(context: Context): Set<String> =
        TeamsStore(context).load().mapNotNull { NhlTeams.match(it.team)?.abbrev }.toSet()

    fun refresh(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try {
                scheduleDaily(app)
                val followed = followedAbbrevs(app)
                if (followed.isEmpty()) return@launch
                val now = Instant.now()
                DirectNhlSource().scoreboard()
                    .filter { (it.away.abbrev in followed || it.home.abbrev in followed) && !it.state.isOver && it.scheduleOk }
                    .forEach { g ->
                        val pregameAt = g.startUtc.minus(Duration.ofMinutes(30))
                        val watchAt = g.startUtc.minus(Duration.ofMinutes(15))
                        if (pregameAt.isAfter(now)) alarm(app, ACTION_PREGAME, g.id, pregameAt)
                        else AlarmReceiver.pregame(app, g)
                        if (watchAt.isAfter(now)) alarm(app, ACTION_WATCH, g.id, watchAt)
                        else if (!WatchStore(app).skippedTonight(g.id)) GameWatchService.start(app, g.id)
                    }
            } catch (_: Exception) {
                // Offline: the next app open or the morning check tries again.
            } finally {
                onDone()
            }
        }
    }

    private fun pending(context: Context, action: String, gameId: Long) = PendingIntent.getBroadcast(
        context, (action + gameId).hashCode(),
        Intent(context, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_GAME, gameId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun alarm(context: Context, action: String, gameId: Long, at: Instant) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, action, gameId)
        val ms = at.toEpochMilli()
        if (canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, ms, 60_000L, pi)
    }

    private fun scheduleDaily(context: Context) {
        val zone = ZoneId.systemDefault()
        var next = ZonedDateTime.now(zone).with(LocalTime.of(9, 0))
        if (!next.isAfter(ZonedDateTime.now(zone))) next = next.plusDays(1)
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending(context, ACTION_DAILY, 0))
    }

    private fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** One honest sentence when alerts can't work as promised, or null when all is well. */
    fun problem(context: Context): String? = when {
        !AlertNotifier.canPost(context) -> "Allow notifications so bitsports can tell you about goals."
        !canExact(context) -> "Allow alarms & reminders so the 30-minute warning arrives on time."
        else -> null
    }

    fun needsExactAlarmSetting(context: Context) = AlertNotifier.canPost(context) && !canExact(context)

    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val gameId = intent.getLongExtra(EXTRA_GAME, -1L)
            val done = goAsync()
            when (intent.action) {
                ACTION_DAILY -> refresh(context) { done.finish() }
                ACTION_WATCH -> {
                    scope.launch {
                        if (!WatchStore(context).skippedTonight(gameId) && !GameWatchService.start(context, gameId)) {
                            runCatching { DirectNhlSource().scoreboard().firstOrNull { it.id == gameId } }.getOrNull()
                                ?.let { AlertNotifier.followNudge(context, it) }
                        }
                        done.finish()
                    }
                }
                ACTION_PREGAME -> scope.launch {
                    runCatching { DirectNhlSource().scoreboard().firstOrNull { it.id == gameId } }.getOrNull()?.let { pregame(context, it) }
                    done.finish()
                }
                else -> done.finish()
            }
        }

        companion object {
            fun pregame(context: Context, game: Game) {
                val store = WatchStore(context)
                val prefs = PrefsStore(context).load()
                AlertNotifier.ensureChannels(context)
                val (alert, next) = AlertPlanner.pregame(store.state(game.id), game, Instant.now(), prefs.alertPrefs())
                store.save(next)
                alert?.let { AlertNotifier.post(context, it, game, prefs, emptyList()) }
            }
        }
    }

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                val done = goAsync()
                refresh(context) { done.finish() }
            }
        }
    }
}
