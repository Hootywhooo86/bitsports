package com.bit128.bitsports.overlay

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import com.bit128.bitsports.MainActivity
import com.bit128.bitsports.PrefsStore
import com.bit128.bitsports.R
import com.bit128.bitsports.alerts.AlertLog
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.OddsHistory
import com.bit128.bitsports.ui.GoalMoments
import com.bit128.bitsports.ui.GoalSpecial
import com.bit128.bitsports.ui.HockeyBoard
import com.bit128.bitsports.ui.OddsMeter
import com.bit128.bitsports.ui.TeamInk
import com.bit128.bitsports.widget.LedBitmaps
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The bitsports scoreboard floating over whatever you're watching the game in (CBC Gem,
 * Sportsnet+, any app): the LED jumbotron, live, with the odds strip under it. Goals take over
 * the overlay with your goal moment for a few seconds. Drag it anywhere; tap to switch size;
 * double-tap to close.
 */
class OverlayService : Service() {
    companion object {
        private const val EXTRA_GAME = "game"
        private const val ACTION_STOP = "com.bit128.bitsports.overlay.STOP"
        private const val CHANNEL = "overlay"
        private const val NOTIFICATION_ID = 7_128

        private val _showing = MutableStateFlow<Long?>(null)
        /** The game the overlay is showing, or null. */
        val showing: StateFlow<Long?> = _showing

        fun allowed(context: Context) = Settings.canDrawOverlays(context)

        /** Android's own page for "Display over other apps" for bitsports. */
        fun permissionIntent(context: Context) = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun start(context: Context, gameId: Long): Boolean {
            if (!allowed(context)) return false
            context.startForegroundService(Intent(context, OverlayService::class.java).putExtra(EXTRA_GAME, gameId))
            return true
        }

        fun stop(context: Context) {
            context.startService(Intent(context, OverlayService::class.java).setAction(ACTION_STOP))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var root: LinearLayout? = null
    private var board: ImageView? = null
    private var odds: ImageView? = null
    private var params: WindowManager.LayoutParams? = null
    private var big = false
    private var gameId = 0L
    private var detail: GameDetail? = null
    private var seenGoals: Set<Int>? = null
    private var takeover: Job? = null
    private var loop: Job? = null
    private var ticker: Job? = null
    private val wm by lazy { getSystemService(WindowManager::class.java) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        val id = intent?.getLongExtra(EXTRA_GAME, 0L) ?: 0L
        if (id == 0L || !allowed(this)) { stopSelf(); return START_NOT_STICKY }
        foreground("Starting…")
        if (root == null) addWindow()
        if (id != gameId) {
            gameId = id; detail = null; seenGoals = null
            loop?.cancel(); loop = scope.launch { poll() }
        }
        _showing.value = id
        return START_NOT_STICKY
    }

    // ---------- the window ----------

    @SuppressLint("ClickableViewAccessibility")
    private fun addWindow() {
        val density = resources.displayMetrics.density
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; alpha = 0.95f }
        val b = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER }
        val o = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; visibility = View.GONE }
        col.addView(b, LinearLayout.LayoutParams(width(), LinearLayout.LayoutParams.WRAP_CONTENT))
        col.addView(o, LinearLayout.LayoutParams(width(), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = (2 * density).toInt() })
        val p = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; x = (12 * density).toInt(); y = (96 * density).toInt() }
        // Drag to move; tap for big / small; double-tap to close.
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0
        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean { big = !big; resize(); return true }
            override fun onDoubleTap(e: MotionEvent): Boolean { stopSelf(); return true }
        })
        col.setOnTouchListener { _, e ->
            gestures.onTouchEvent(e)
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = p.x; startY = p.y }
                MotionEvent.ACTION_MOVE -> { p.x = startX + (e.rawX - downX).toInt(); p.y = startY + (e.rawY - downY).toInt(); wm.updateViewLayout(col, p) }
            }
            true
        }
        wm.addView(col, p)
        root = col; board = b; odds = o; params = p
    }

    private fun width(): Int = ((if (big) 340 else 210) * resources.displayMetrics.density).toInt()

    private fun resize() {
        listOfNotNull(board, odds).forEach { v -> v.layoutParams = v.layoutParams.apply { width = width() } }
        root?.let { r -> params?.let { wm.updateViewLayout(r, it) } }
    }

    // ---------- live ----------

    private suspend fun poll() {
        val source = DirectNhlSource()
        while (currentCoroutineContext().isActive) {
            val d = try { withContext(Dispatchers.IO) { source.playByPlay(gameId) } } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            if (d != null) {
                val goals = GameAnalysis.goals(d)
                val prev = seenGoals
                seenGoals = goals.mapTo(HashSet()) { it.eventId }
                detail = d
                drawBoard()
                startTicker()
                foreground("${d.game.away.abbrev} ${d.game.away.score ?: 0}–${d.game.home.score ?: 0} ${d.game.home.abbrev} · ${HockeyBoard.clockText(d.game)}")
                if (prev != null) goals.filter { it.eventId !in prev }.lastOrNull()?.let { celebrate(it, goals, d) }
                updateOdds(source, d)
                // A few minutes after the final, it tidies itself away.
                if (d.game.state.isOver) { delay(5 * 60_000); stopSelf(); return }
            }
            delay(if (d?.game?.state?.isLive == true) d.freshInMs ?: 20_000 else 60_000)
        }
    }

    private fun drawBoard(frame: Int = 0) {
        val d = detail ?: return
        if (takeover?.isActive == true) return
        val input = HockeyBoard.input(d.game, PrefsStore(this).load().accent.toInt())
        board?.setImageBitmap(LedBitmaps.render(HockeyBoard.build(input, frame), cell = 4))
    }

    /** Power plays, the Zamboni and the heartbeat move: redraw ~8 times a second while they do. */
    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            var f = 0
            while (isActive) {
                val d = detail
                if (d != null && HockeyBoard.input(d.game, 0).animated) drawBoard(f++)
                delay(120)
            }
        }
    }

    /** Your goal moment, big, over the video for about three seconds. */
    private fun celebrate(g: GoalEvent, goals: List<GoalEvent>, d: GameDetail) {
        val prefs = PrefsStore(this).load()
        val special = GoalSpecial.of(g, goals)
        val home = g.teamAbbrev == d.game.home.abbrev
        val before = if (home) "${g.awayScore}-${g.homeScore - 1}" else "${(g.awayScore - 1).coerceAtLeast(0)}-${g.homeScore}"
        takeover?.cancel()
        takeover = scope.launch {
            val wasBig = big
            big = true; resize()
            for (f in 0 until GoalMoments.FRAMES) {
                val grid = GoalMoments.build(prefs.goalStyle, g.teamAbbrev, g.scorer, g.scorerTotal, f, special, before, "${g.awayScore}-${g.homeScore}")
                board?.setImageBitmap(LedBitmaps.render(grid, cell = 4))
                delay(120)
            }
            big = wasBig; resize()
            takeover = null
            drawBoard()
        }
    }

    private suspend fun updateOdds(source: DirectNhlSource, d: GameDetail) {
        val prefs = PrefsStore(this).load()
        if (!prefs.showOdds || d.game.state.isOver) { odds?.visibility = View.GONE; return }
        val o = try { withContext(Dispatchers.IO) { source.oddsFor(listOf(d.game))[d.game.id] } } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        val share = o?.let { OddsHistory.share(it) }
        if (share == null) { odds?.visibility = View.GONE; return }
        val (ai, hi) = TeamInk.pair(d.game.away.abbrev, d.game.home.abbrev)
        odds?.setImageBitmap(LedBitmaps.render(OddsMeter.strip(d.game.away.abbrev, d.game.home.abbrev, ai, hi, share), cell = 4))
        odds?.visibility = View.VISIBLE
    }

    // ---------- housekeeping ----------

    private fun foreground(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Scoreboard overlay", NotificationManager.IMPORTANCE_MIN))
        val stop = PendingIntent.getService(this, 1, Intent(this, OverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_puck)
            .setContentTitle("Scoreboard overlay")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Close overlay", stop).build())
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(NOTIFICATION_ID, n)
        } catch (e: Exception) {
            AlertLog(this).failed("Overlay couldn't start", e)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        _showing.value = null
        super.onDestroy()
    }
}
