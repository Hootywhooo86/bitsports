package com.bit128.bitsports.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.bit128.bitsports.MainActivity
import com.bit128.bitsports.PrefsStore
import com.bit128.bitsports.R
import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.alerts.AlertNotifier
import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.nhl.Strengths
import com.bit128.bitsports.ui.HeatMap
import com.bit128.bitsports.ui.HockeyBoard
import com.bit128.bitsports.ui.ScoreFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Team score: your team's game today, two big numbers. */
class TeamWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context, goAsync())
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        Widgets.refresh(context, goAsync())
    override fun onDeleted(context: Context, ids: IntArray) = ids.forEach { WidgetPrefs(context).delete(it) }
}

/** Score + heat map: the score over an LED rink of where each team is shooting from. */
class RinkWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context, goAsync())
    override fun onDeleted(context: Context, ids: IntArray) = ids.forEach { WidgetPrefs(context).delete(it) }
}

/** NHL tonight: every game on one LED arena board, followed teams underlined. */
class ScoresWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = Widgets.refresh(context, goAsync())
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        Widgets.refresh(context, goAsync())
}

/**
 * Keeps widgets current without the app open, on the pacing in [WidgetPacing]: with each new copy
 * the NHL publishes (~20s) while a game they show is live, every 15 minutes at the quietest. Re-armed after restarts and updates.
 */
class WidgetTicker : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Widgets.refresh(context, goAsync())
}

/**
 * Fills every widget. Fed three ways: the 30-minute widget tick (fetches), the app's own
 * scoreboard poll, and the game watcher every 15 s while a followed game is live.
 */
object Widgets {
    private const val LIVE_RED = 0xFFFF4D6D.toInt()
    private const val INK = 0xFFFFFFFF.toInt()
    private const val MUTED = 0xFF8C8C8C.toInt()
    private const val DIM = 0xFF6B6B6B.toInt()
    private const val WARN = 0xFFFFD95E.toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Last play-by-play per game, so a scoreboard-only update can still redraw the board. */
    private val details = java.util.concurrent.ConcurrentHashMap<Long, GameDetail>()
    /** When the NHL's next copy of the scoreboard is due, from the last fetch. */
    @Volatile private var freshInMs: Long? = null

    private fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    fun any(context: Context) = ids(context, TeamWidget::class.java).isNotEmpty() || ids(context, RinkWidget::class.java).isNotEmpty() ||
        ids(context, ScoresWidget::class.java).isNotEmpty()

    /** Fetches today's games (and shots for the rink widgets) and redraws everything. */
    fun refresh(context: Context, pending: android.content.BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        scope.launch {
            try {
                if (!any(app)) { cancelTick(app); return@launch }
                val source = DirectNhlSource()
                val paced = runCatching { source.scoreboardPaced() }.getOrNull()
                freshInMs = paced?.second
                val games = paced?.first
                if (games == null) {
                    // One miss (phone waking up, switching networks) shouldn't leave widgets stale: retry soon.
                    drawAll(app, null)
                    tickAt(app, System.currentTimeMillis() + RETRY_MS)
                    return@launch
                }
                val prefs = WidgetPrefs(app)
                ids(app, RinkWidget::class.java).toList().mapNotNull { prefs.teamFor(it)?.let { t -> gameFor(games, t) } }
                    .filter { it.state.isLive || it.state.isOver }
                    .distinctBy { it.id }
                    .forEach { g -> runCatching { source.playByPlay(g.id) }.getOrNull()?.let { details[g.id] = it } }
                drawAll(app, games)
            } finally {
                pending?.finish()
            }
        }
    }

    /** From the app's scoreboard poll: no network here, just redraw. */
    fun pushScoreboard(context: Context, games: List<Game>) {
        if (!any(context)) return
        drawAll(context.applicationContext, games)
    }

    /** From the game watcher: fresh play-by-play for one game. */
    fun push(context: Context, d: GameDetail) {
        if (!any(context)) return
        details[d.game.id] = d
        drawAll(context.applicationContext, null, override = d.game)
    }

    private var lastGames: List<Game> = emptyList()

    @Synchronized
    private fun drawAll(context: Context, games: List<Game>?, override: Game? = null) {
        if (games != null) lastGames = games
        val list = lastGames.map { if (override != null && it.id == override.id) override else it }
            .let { if (override != null && it.none { g -> g.id == override.id }) it + override else it }
        val manager = AppWidgetManager.getInstance(context)
        val prefs = WidgetPrefs(context)
        ids(context, TeamWidget::class.java).forEach { id ->
            val team = prefs.teamFor(id)
            val tall = (manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)) >= 90
            val game = team?.let { gameFor(list, it) }
            manager.updateAppWidget(id, if (tall) teamViews(context, team, game, games == null && list.isEmpty()) else teamRow(context, team, game))
        }
        ids(context, RinkWidget::class.java).forEach { id ->
            val cfg = prefs.load(id)
            val team = prefs.teamFor(id)
            val game = team?.let { gameFor(list, it) }
            manager.updateAppWidget(id, rinkViews(context, team, game, game?.let { details[it.id] }, cfg))
        }
        val followed = AlertScheduler.followedAbbrevs(context)
        val accent = PrefsStore(context).load().accent.toInt().takeIf { it != 0 } ?: INK
        ids(context, ScoresWidget::class.java).forEach { id ->
            manager.updateAppWidget(id, scoresViews(context, manager, id, list, followed, accent, offline = games == null && list.isEmpty()))
        }
        scheduleTick(context, list)
    }

    // ---------- live ticking ----------

    private fun tickIntent(context: Context) = PendingIntent.getBroadcast(
        context, 128, Intent(context, WidgetTicker::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** The games some widget is showing right now. */
    private fun watched(context: Context, games: List<Game>): List<Game> {
        if (ids(context, ScoresWidget::class.java).isNotEmpty()) return games
        val prefs = WidgetPrefs(context)
        val teams = (ids(context, TeamWidget::class.java).toList() + ids(context, RinkWidget::class.java).toList()).mapNotNull { prefs.teamFor(it) }.toSet()
        return games.filter { it.away.abbrev in teams || it.home.abbrev in teams }
    }

    private const val RETRY_MS = 2 * 60_000L

    /**
     * While any widget exists there's always a next refresh: right after each new NHL copy (~20s)
     * while a game it shows is live, every minute in the half hour before puck drop, at puck drop for a game later today,
     * and every 15 minutes otherwise (so finals and tomorrow's schedule turn up without the app).
     */
    private fun scheduleTick(context: Context, games: List<Game>) {
        if (!any(context)) { cancelTick(context); return }
        tickAt(context, WidgetPacing.next(System.currentTimeMillis(), watched(context, games), freshInMs))
    }

    private fun tickAt(context: Context, at: Long) {
        val am = context.getSystemService(AlarmManager::class.java)
        if (AlertScheduler.canExact(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC, at, tickIntent(context))
        else am.setAndAllowWhileIdle(AlarmManager.RTC, at, tickIntent(context))
    }

    private fun cancelTick(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(tickIntent(context))

    fun gameFor(games: List<Game>, team: String): Game? =
        games.filter { it.away.abbrev == team || it.home.abbrev == team }.minByOrNull { if (it.state.isLive) 0 else 1 }

    // ---------- views ----------

    private fun open(context: Context, game: Game?): PendingIntent = if (game != null) AlertNotifier.openGameIntent(context, game.id)
    else PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun crest(v: RemoteViews, c1: Int, c2: Int, abbrev: String?) {
        val t = abbrev?.let { NhlTeams.byAbbrev(it) }
        v.setInt(c1, "setBackgroundColor", (t?.colors?.first ?: 0xFF3A3A3A).toInt())
        v.setInt(c2, "setBackgroundColor", (t?.colors?.second ?: 0xFF6B6B6B).toInt())
    }

    /** "■ LIVE", "FINAL/OT", "INT · 2ND" or the puck-drop time, and its colour. */
    private fun status(g: Game): Pair<String, Int> = when {
        g.state.isLive && g.clock?.inIntermission != true -> "■ LIVE" to LIVE_RED
        g.state.isLive -> "INT" to MUTED
        g.state.isOver -> ScoreFormat.status(g) to MUTED
        else -> "TONIGHT" to MUTED
    }

    private fun clockLine(g: Game): String {
        val c = g.clock
        val shots = if (g.away.sog != null) "Shots ${g.away.sog}–${g.home.sog}" else null
        val clock = when {
            c == null || !g.state.isLive -> null
            c.inIntermission -> "After the " + AlertPlanner.periodLabel(c.period, c.periodType)
            else -> AlertPlanner.periodLabel(c.period, c.periodType) + " " + c.timeRemaining
        }
        return listOfNotNull(clock, Strengths.banner(g)?.text, shots).joinToString(" · ")
    }

    private fun teamViews(context: Context, team: String?, g: Game?, offline: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_team)
        v.setOnClickPendingIntent(android.R.id.background, open(context, g))
        crest(v, R.id.crest_c1, R.id.crest_c2, team)
        val t = team?.let { NhlTeams.byAbbrev(it) }
        v.setTextViewText(R.id.team, (t?.nickname ?: "BITSPORTS").uppercase())
        fun bigLine(text: String, detail: String) {
            v.setViewVisibility(R.id.score_block, View.GONE)
            v.setViewVisibility(R.id.big_line, View.VISIBLE)
            v.setTextViewText(R.id.big_line, text)
            v.setTextViewText(R.id.detail, detail)
        }
        when {
            team == null -> { v.setTextViewText(R.id.status, ""); bigLine("ADD A TEAM", "Follow an NHL team in bitsports") }
            g == null -> { v.setTextViewText(R.id.status, ""); bigLine("NO GAME", if (offline) "Can't reach the NHL feed" else "Nothing on today") }
            !g.state.isLive && !g.state.isOver -> {
                val (s, col) = status(g)
                v.setTextViewText(R.id.status, s); v.setTextColor(R.id.status, col)
                val opp = if (g.home.abbrev == team) "vs " + AlertCopy.nick(g.away.abbrev) else "at " + AlertCopy.nick(g.home.abbrev)
                bigLine(ScoreFormat.status(g), if (g.scheduleOk) opp else "$opp · postponed")
            }
            else -> {
                val home = g.home.abbrev == team
                val mine = (if (home) g.home.score else g.away.score) ?: 0
                val theirs = (if (home) g.away.score else g.home.score) ?: 0
                val opp = if (home) g.away.abbrev else g.home.abbrev
                val (s, col) = status(g)
                v.setTextViewText(R.id.status, s); v.setTextColor(R.id.status, col)
                v.setViewVisibility(R.id.score_block, View.VISIBLE)
                v.setViewVisibility(R.id.big_line, View.GONE)
                v.setTextViewText(R.id.my_score, mine.toString())
                v.setTextViewText(R.id.their_score, theirs.toString())
                // The leading number in full ink, the trailing one dimmed; level is both ink.
                v.setTextColor(R.id.my_score, if (mine >= theirs) INK else DIM)
                v.setTextColor(R.id.their_score, if (theirs >= mine) INK else DIM)
                v.setTextViewText(R.id.my_abbrev, team)
                v.setTextViewText(R.id.their_abbrev, (if (home) "VS " else "@ ") + opp)
                v.setTextViewText(R.id.detail, clockLine(g).ifEmpty { AlertCopy.nick(team) + " vs " + AlertCopy.nick(opp) })
            }
        }
        return v
    }

    private fun teamRow(context: Context, team: String?, g: Game?): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_team_row)
        v.setOnClickPendingIntent(android.R.id.background, open(context, g))
        crest(v, R.id.crest_c1, R.id.crest_c2, team)
        v.setTextViewText(R.id.team, team ?: "BITSPORTS")
        v.setTextColor(R.id.status, MUTED)
        when {
            team == null -> { v.setTextViewText(R.id.score_line, "Add a team"); v.setTextViewText(R.id.status, "") }
            g == null -> { v.setTextViewText(R.id.score_line, "No game"); v.setTextViewText(R.id.status, "") }
            !g.state.isLive && !g.state.isOver -> {
                val opp = if (g.home.abbrev == team) "VS " + g.away.abbrev else "@ " + g.home.abbrev
                v.setTextViewText(R.id.score_line, opp)
                v.setTextViewText(R.id.status, ScoreFormat.status(g))
            }
            else -> {
                val home = g.home.abbrev == team
                val opp = if (home) g.away.abbrev else g.home.abbrev
                v.setTextViewText(R.id.score_line, "${(if (home) g.home.score else g.away.score) ?: 0}–${(if (home) g.away.score else g.home.score) ?: 0} $opp")
                val live = g.state.isLive && g.clock?.inIntermission != true
                v.setTextViewText(R.id.status, if (live) "■ " + clockLine(g).substringBefore(" · ").uppercase() else status(g).first)
                if (live) v.setTextColor(R.id.status, LIVE_RED)
            }
        }
        return v
    }

    private fun scoresViews(
        context: Context, manager: AppWidgetManager, id: Int, games: List<Game>, followed: Set<String>, accent: Int, offline: Boolean,
    ): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_scores)
        v.setOnClickPendingIntent(android.R.id.background, open(context, null))
        val ordered = ScoreFormat.order(games, followed)
        val live = games.count { it.state.isLive }
        v.setTextViewText(R.id.status, when {
            offline -> "OFFLINE"
            live > 0 -> "■ $live LIVE"
            games.isEmpty() -> ""
            else -> "${games.size} GAMES"
        })
        v.setTextColor(R.id.status, if (live > 0) LIVE_RED else MUTED)
        // How many rows fit: the board keeps 128 LEDs across, so its height in LEDs follows the widget's shape.
        val o = manager.getAppWidgetOptions(id)
        val w = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250).coerceAtLeast(110) - 28
        val h = o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180).coerceAtLeast(60) - 52
        val maxGames = ((h.toFloat() / w * ScoreboardLeds.COLS + 2) / ScoreboardLeds.ROW_H).toInt().coerceIn(1, 16)
        val grid = ScoreboardLeds.build(ordered, followed, accent, maxGames)
        v.setImageViewBitmap(R.id.board, LedBitmaps.render(grid))
        v.setTextViewText(R.id.more, if (ordered.size > maxGames) "+${ordered.size - maxGames} more · tap for all" else "")
        return v
    }

    private fun rinkViews(context: Context, team: String?, g: Game?, d: GameDetail?, cfg: WidgetConfig): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_rink)
        v.setOnClickPendingIntent(android.R.id.background, open(context, g))
        val awayAbbrev = g?.away?.abbrev ?: team
        val homeAbbrev = g?.home?.abbrev
        crest(v, R.id.away_c1, R.id.away_c2, awayAbbrev)
        crest(v, R.id.home_c1, R.id.home_c2, homeAbbrev)
        v.setTextViewText(R.id.away_abbrev, awayAbbrev ?: "")
        v.setTextViewText(R.id.home_abbrev, homeAbbrev ?: "")
        v.setViewVisibility(R.id.home_c1, if (homeAbbrev == null) View.GONE else View.VISIBLE)
        v.setViewVisibility(R.id.home_c2, if (homeAbbrev == null) View.GONE else View.VISIBLE)
        when {
            g == null -> {
                v.setTextViewText(R.id.score, if (team == null) "ADD A TEAM" else "NO GAME")
                v.setTextViewText(R.id.status, if (team == null) "Follow an NHL team in bitsports" else "Nothing on today")
                v.setTextColor(R.id.status, MUTED)
                v.setTextViewText(R.id.shots, "")
            }
            !g.state.isLive && !g.state.isOver -> {
                v.setTextViewText(R.id.score, "VS")
                v.setTextViewText(R.id.status, "PUCK DROP " + ScoreFormat.status(g))
                v.setTextColor(R.id.status, MUTED)
                v.setTextViewText(R.id.shots, "")
            }
            else -> {
                v.setTextViewText(R.id.score, "${g.away.score ?: 0}–${g.home.score ?: 0}")
                val live = g.state.isLive && g.clock?.inIntermission != true
                val clock = g.clock?.let { AlertPlanner.periodLabel(it.period, it.periodType).uppercase() + " " + it.timeRemaining }
                v.setTextViewText(R.id.status, when {
                    live -> "■ LIVE " + (clock ?: "")
                    g.state.isLive -> "INT · " + (g.clock?.let { AlertPlanner.periodLabel(it.period, it.periodType).uppercase() } ?: "")
                    else -> ScoreFormat.status(g)
                })
                v.setTextColor(R.id.status, if (live) LIVE_RED else MUTED)
                val banner = Strengths.banner(g)
                v.setTextViewText(R.id.shots, banner?.text ?: if (g.away.sog != null) "SHOTS ${g.away.sog}–${g.home.sog}" else "")
                v.setTextColor(R.id.shots, if (banner != null) WARN else MUTED)
            }
        }
        val shots = d?.let { GameAnalysis.shots(it) }.orEmpty()
        val only = if (cfg.heat == "mine") team else null
        val heat = HeatMap.heat(shots, awayAbbrev ?: "", only)
        val grid = HeatMap.draw(heat, HockeyBoard.colors(awayAbbrev ?: ""), HockeyBoard.colors(homeAbbrev ?: ""))
        v.setImageViewBitmap(R.id.board, LedBitmaps.render(grid, cell = 5, dots = cfg.board != "solid"))
        return v
    }
}

/** When widgets next refresh, from the games they show. Pure, so the pacing is tested. */
object WidgetPacing {
    /** Live without cache timing: the NHL's own refresh period. */
    private const val LIVE_MS = 20_000L
    private const val SOON_MS = 60_000L
    private const val IDLE_MS = 15 * 60_000L

    /** [freshInMs]: when the NHL publishes its next copy; live widgets land just after it, like the app. */
    fun next(now: Long, shown: List<Game>, freshInMs: Long? = null): Long {
        if (shown.any { it.state.isLive }) return now + (freshInMs ?: LIVE_MS)
        val upcoming = shown.filter { !it.state.isOver && it.scheduleOk }.map { it.startUtc.toEpochMilli() }.filter { it > now - 30 * 60_000L }
        val soonest = upcoming.minOrNull()
        return when {
            soonest == null -> now + IDLE_MS
            soonest - now <= 30 * 60_000L -> now + SOON_MS // includes a game that should have started but isn't live yet
            else -> minOf(soonest - 30 * 60_000L, now + IDLE_MS) // wake as the half-hour-before window opens
        }
    }
}
