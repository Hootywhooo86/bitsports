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
 * Runs when the app opens, every morning at 09:00 (every 30 minutes until it gets through), after
 * the phone restarts and after an update. None of it needs the app open: the watcher starts from
 * the alarm, and if Android blocks that, a once-a-minute alarm checks the game and posts alerts.
 */
object AlertScheduler {
    private const val ACTION_PREGAME = "com.bit128.bitsports.PREGAME"
    private const val ACTION_WATCH = "com.bit128.bitsports.WATCH"
    private const val ACTION_DAILY = "com.bit128.bitsports.DAILY"
    private const val ACTION_PULSE = "com.bit128.bitsports.PULSE"
    private const val PULSE_MS = 60_000L
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
                if (followed.isEmpty()) {
                    AlertLog(app).scheduled("No NHL team followed")
                    return@launch
                }
                val now = Instant.now()
                val mine = DirectNhlSource().scoreboard()
                    .filter { (it.away.abbrev in followed || it.home.abbrev in followed) && !it.state.isOver && it.scheduleOk }
                if (mine.isEmpty()) AlertLog(app).scheduled("No game left today for ${followed.joinToString(", ")}")
                mine.forEach { g ->
                    val pregameAt = g.startUtc.minus(Duration.ofMinutes(30))
                    val watchAt = g.startUtc.minus(Duration.ofMinutes(15))
                    AlertLog(app).scheduled(
                        "${g.away.abbrev} @ ${g.home.abbrev}: " +
                            if (watchAt.isAfter(now)) "watching starts ${java.time.format.DateTimeFormatter.ofPattern("h:mm a").format(watchAt.atZone(ZoneId.systemDefault()))}"
                            else "on now",
                    )
                    if (pregameAt.isAfter(now)) alarm(app, ACTION_PREGAME, g.id, pregameAt)
                    else AlarmReceiver.pregame(app, g)
                    if (watchAt.isAfter(now)) alarm(app, ACTION_WATCH, g.id, watchAt)
                    else if (!WatchStore(app).skippedTonight(g.id) && !GameWatchService.start(app, g.id)) pulseSoon(app, g.id)
                }
            } catch (e: Exception) {
                AlertLog(app).failed("Couldn't load today's schedule", e)
                // Offline: try again in 30 minutes rather than waiting for tomorrow or an app open.
                // (This replaces the 09:00 alarm; that run puts it back.)
                alarm(app, ACTION_DAILY, 0, Instant.now().plus(Duration.ofMinutes(30)))
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

    /**
     * The backup for when Android won't let the watcher start from the background: check the game
     * once a minute from an alarm and post the same alerts, until the watcher can take over.
     */
    private fun pulseSoon(context: Context, gameId: Long) = alarm(context, ACTION_PULSE, gameId, Instant.now().plusMillis(PULSE_MS))

    private suspend fun pulse(context: Context, gameId: Long) {
        val store = WatchStore(context)
        if (GameWatchService.isWatching(gameId) || store.skippedTonight(gameId)) return
        val d = try {
            DirectNhlSource().playByPlay(gameId)
        } catch (e: Exception) {
            pulseSoon(context, gameId)
            return
        }
        val prefs = PrefsStore(context).load()
        if (!AlertNotifier.canPost(context)) {
            AlertLog(context).failed("Notifications are off, holding alerts until they're allowed", null)
            if (!AlertPlanner.shouldStopWatching(d.game, Instant.now())) pulseSoon(context, gameId)
            return
        }
        AlertLog(context).checked("${d.game.away.abbrev} ${d.game.away.score ?: 0}-${d.game.home.score ?: 0} ${d.game.home.abbrev} (backup check)")
        val (alerts, next) = AlertPlanner.step(store.state(d.game.id), d, prefs.alertPrefs())
        store.save(next)
        AlertNotifier.ensureChannels(context)
        val goals = com.bit128.bitsports.nhl.GameAnalysis.goals(d)
        alerts.forEach { AlertNotifier.post(context, it, d.game, prefs, goals) }
        com.bit128.bitsports.widget.Widgets.push(context, d)
        if (AlertPlanner.shouldStopWatching(d.game, Instant.now())) return
        // An exact alarm may start a foreground service, so each pulse tries to hand over to the watcher.
        if (!GameWatchService.start(context, gameId)) pulseSoon(context, gameId)
    }

    private fun scheduleDaily(context: Context) {
        val zone = ZoneId.systemDefault()
        var next = ZonedDateTime.now(zone).with(LocalTime.of(9, 0))
        if (!next.isAfter(ZonedDateTime.now(zone))) next = next.plusDays(1)
        val am = context.getSystemService(AlarmManager::class.java)
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending(context, ACTION_DAILY, 0))
    }

    fun canExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    /** One honest sentence when alerts can't work as promised, or null when all is well. */
    fun problem(context: Context): String? = when {
        !AlertNotifier.canPost(context) -> "Allow notifications so bitsports can tell you about goals."
        !canExact(context) -> "Allow alarms & reminders so live game alerts can start on time."
        Build.VERSION.SDK_INT >= 28 && context.getSystemService(android.app.ActivityManager::class.java).isBackgroundRestricted ->
            "bitsports' battery is set to Restricted, so Android stops it watching games."
        !goalsChannelOn(context) -> "Goal alerts are switched off in Android's notification settings."
        else -> null
    }

    private fun goalsChannelOn(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val sound = PrefsStore(context).load().goalSound
        val id = if (sound) AlertNotifier.CH_GOALS_HORN else AlertNotifier.CH_GOALS_QUIET
        val ch = context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(id) ?: return true
        return ch.importance != android.app.NotificationManager.IMPORTANCE_NONE
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
                            pulseSoon(context, gameId)
                        }
                        done.finish()
                    }
                }
                ACTION_PULSE -> scope.launch {
                    try { pulse(context, gameId) } finally { done.finish() }
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
