package com.bit128.bitsports.alerts

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.bit128.bitsports.PrefsStore
import com.bit128.bitsports.ai.AiResult
import com.bit128.bitsports.ai.FamilyAi
import com.bit128.bitsports.ai.RecapPrompt
import com.bit128.bitsports.alertPrefs
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.NhlParser
import com.bit128.bitsports.nhl.NhlSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Watches followed games while they're on: polls every 15s, announces what AlertPlanner decides,
 * and keeps the mini scoreboard up to date. Android requires the ongoing notification for this.
 */
class GameWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val jobs = HashMap<Long, Job>()
    private val source: NhlSource = DirectNhlSource()
    private var foregroundId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val gameId = intent?.getLongExtra(EXTRA_GAME, -1L) ?: -1L
        when (intent?.action) {
            ACTION_STOP -> stopGame(gameId, skipTonight = true)
            ACTION_REPLAY -> if (gameId > 0) watch(gameId, replay = true)
            else -> if (gameId > 0) watch(gameId, replay = false)
        }
        if (jobs.isEmpty()) stopSelf()
        return START_REDELIVER_INTENT
    }

    private fun notificationId(gameId: Long) = gameId.hashCode()

    private fun watch(gameId: Long, replay: Boolean) {
        val store = WatchStore(this)
        if (jobs.containsKey(gameId) || (!replay && store.skippedTonight(gameId))) return
        AlertNotifier.ensureChannels(this)
        // Foreground straight away with a placeholder; the first poll fills it in.
        val placeholder = androidx.core.app.NotificationCompat.Builder(this, AlertNotifier.CH_LIVE)
            .setSmallIcon(com.bit128.bitsports.R.drawable.ic_stat_puck)
            .setContentTitle("Following your game…").setOngoing(true).setSilent(true).build()
        if (foregroundId == null) {
            foregroundId = notificationId(gameId)
            ServiceCompat.startForeground(
                this, foregroundId!!, placeholder,
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
            )
        }
        watching += gameId
        jobs[gameId] = scope.launch { if (replay) replayLoop(gameId) else liveLoop(gameId) }
    }

    private suspend fun liveLoop(gameId: Long) {
        val store = WatchStore(this)
        var last: GameDetail? = null
        var backoffMs = POLL_MS
        while (scope.isActive) {
            try {
                val d = source.playByPlay(gameId)
                last = d
                // Live: check again the moment the NHL publishes its next copy (~every 20s).
                backoffMs = if (d.game.state.isLive) d.freshInMs ?: POLL_MS else POLL_MS
                if (handle(d, store)) break
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AlertLog(this).failed("Couldn't check the game", e)
                // Keep the last good scoreboard, say how old it is, and try again a bit later.
                last?.let { runCatching { updateScoreboard(it, AlertPlanner.staleLabel(it.fetchedAt, Instant.now())) } }
                backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
            }
            delay(backoffMs)
        }
        // Over: the final score goes onto the calendar entry.
        com.bit128.bitsports.calendar.BitcalSync.sync(this, force = true)
        stopGame(gameId, skipTonight = false)
    }

    /** Debug builds: replays a saved real game in fast-forward to try every alert. */
    private suspend fun replayLoop(gameId: Long) {
        val json = runCatching { assets.open("replay-pbp.json").bufferedReader().use { it.readText() } }.getOrNull()
        if (json == null) { stopGame(gameId, false); return }
        val full = NhlParser.playByPlay(json, Instant.now())
        val store = WatchStore(this)
        store.save(WatchState.fresh(full.game.id))
        val stops = full.plays.withIndex().filter { it.value.type in setOf("goal", "period-end") }.map { it.index }
        for (i in stops) {
            val d = full.copy(plays = full.plays.take(i + 1), game = full.game.copy(state = GameState.LIVE), fetchedAt = Instant.now())
            handle(d, store)
            delay(2_000)
        }
        handle(full.copy(fetchedAt = Instant.now()), store)
        stopGame(gameId, false)
    }

    /** One poll's worth of work. Returns true when watching should stop. */
    private suspend fun handle(d: GameDetail, store: WatchStore): Boolean {
        val prefs = PrefsStore(this).load()
        if (!AlertNotifier.canPost(this)) {
            // Nothing counts as announced until Android lets it through: the moment notifications
            // are allowed, the next check catches up.
            AlertLog(this).failed("Notifications are off, holding alerts until they're allowed", null)
            com.bit128.bitsports.widget.Widgets.push(this, d)
            return AlertPlanner.shouldStopWatching(d.game, Instant.now())
        }
        AlertLog(this).checked("${d.game.away.abbrev} ${d.game.away.score ?: 0}-${d.game.home.score ?: 0} ${d.game.home.abbrev} (watcher)")
        val (alerts, next) = AlertPlanner.step(store.state(d.game.id), d, prefs.alertPrefs())
        store.save(next)
        val goals = GameAnalysis.goals(d)
        alerts.forEach { a ->
            AlertNotifier.post(this, a, d.game, prefs, goals)
            if (a is Alert.Goal) LiveHub.emit(a.goal)
            if (a is Alert.PeriodEnd && prefs.alertRecap) writeRecap(d, a, store)
        }
        // A recap is still wanted even when period-stat alerts are off.
        if (prefs.alertRecap && !prefs.alertPeriods) {
            (next.endedPeriods - store.recappedPeriods(d.game.id)).forEach { p ->
                writeRecap(d, Alert.PeriodEnd(GameAnalysis.periodStats(d, p), AlertPlanner.periodLabel(p, GameAnalysis.periodStats(d, p).periodType)), store)
            }
        }
        // The scoreboard and widgets are extras: if either fails, alerts must keep coming.
        try { updateScoreboard(d, null) } catch (e: Exception) { AlertLog(this).failed("Mini scoreboard failed", e) }
        try { com.bit128.bitsports.widget.Widgets.push(this, d) } catch (e: Exception) { AlertLog(this).failed("Widget update failed", e) }
        return AlertPlanner.shouldStopWatching(d.game, Instant.now())
    }

    private fun writeRecap(d: GameDetail, a: Alert.PeriodEnd, store: WatchStore) {
        if (a.stats.period in store.recappedPeriods(d.game.id)) return
        store.markRecapped(d.game.id, a.stats.period)
        scope.launch {
            val (system, user) = RecapPrompt.build(d, a.stats.period)
            when (val r = FamilyAi.generate(this@GameWatchService, system, user)) {
                is AiResult.Ok -> {
                    store.saveRecap(d.game.id, a.stats.period, r.text)
                    AlertNotifier.recap(this@GameWatchService, d.game, a.label, r.text)
                }
                is AiResult.Unavailable -> Unit // no fake recaps; the stats alert already went out
            }
        }
    }

    private fun updateScoreboard(d: GameDetail, stale: String?) {
        val stop = PendingIntent.getService(
            this, d.game.id.toInt(),
            Intent(this, GameWatchService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_GAME, d.game.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = AlertNotifier.scoreboard(this, d.game, stale, stop, GameAnalysis.goals(d).lastOrNull())
        if (AlertNotifier.canPost(this)) {
            try { NotificationManagerCompat.from(this).notify(notificationId(d.game.id), n) } catch (_: SecurityException) { }
        }
    }

    private fun stopGame(gameId: Long, skipTonight: Boolean) {
        if (skipTonight && gameId > 0) WatchStore(this).skipTonight(gameId)
        jobs.remove(gameId)?.cancel()
        watching -= gameId
        if (skipTonight) NotificationManagerCompat.from(this).cancel(notificationId(gameId))
        if (jobs.isEmpty()) {
            ServiceCompat.stopForeground(this, if (skipTonight) ServiceCompat.STOP_FOREGROUND_REMOVE else ServiceCompat.STOP_FOREGROUND_DETACH)
            foregroundId = null
            stopSelf()
        }
    }

    override fun onDestroy() {
        watching.removeAll(jobs.keys)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val POLL_MS = 15_000L
        const val EXTRA_GAME = "gameId"
        private val watching: MutableSet<Long> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        fun isWatching(gameId: Long) = gameId in watching
        fun watchingAny() = watching.isNotEmpty()
        private const val ACTION_STOP = "com.bit128.bitsports.STOP_WATCH"
        private const val ACTION_REPLAY = "com.bit128.bitsports.REPLAY"

        /** Starts watching a game. Returns false when Android won't allow it from the background. */
        fun start(context: Context, gameId: Long): Boolean = runCatching {
            ContextCompat.startForegroundService(context, Intent(context, GameWatchService::class.java).putExtra(EXTRA_GAME, gameId))
        }.isSuccess

        fun stop(context: Context, gameId: Long) {
            context.startService(Intent(context, GameWatchService::class.java).setAction(ACTION_STOP).putExtra(EXTRA_GAME, gameId))
        }

        /** Debug builds only: fast-forward through a saved real game. */
        fun replay(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, GameWatchService::class.java).setAction(ACTION_REPLAY).putExtra(EXTRA_GAME, 1L),
            )
        }
    }
}
