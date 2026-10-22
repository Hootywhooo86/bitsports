package com.bit128.bitsports.widget

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
import com.bit128.bitsports.R
import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.alerts.AlertNotifier
import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.NhlTeams
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

/**
 * Fills both widgets. Fed three ways: the 30-minute widget tick (fetches), the app's own
 * scoreboard poll, and the game watcher every 15 s while a followed game is live.
 */
object Widgets {
    private const val LIVE_RED = 0xFFFF4D6D.toInt()
    private const val INK = 0xFFFFFFFF.toInt()
    private const val MUTED = 0xFF8C8C8C.toInt()
    private const val DIM = 0xFF6B6B6B.toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /** Last play-by-play per game, so a scoreboard-only update can still redraw the board. */
    private val details = java.util.concurrent.ConcurrentHashMap<Long, GameDetail>()

    private fun ids(context: Context, cls: Class<*>): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))

    fun any(context: Context) = ids(context, TeamWidget::class.java).isNotEmpty() || ids(context, RinkWidget::class.java).isNotEmpty()

    /** Fetches today's games (and shots for the rink widgets) and redraws everything. */
    fun refresh(context: Context, pending: android.content.BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        scope.launch {
            try {
                if (!any(app)) return@launch
                val source = DirectNhlSource()
                val games = runCatching { source.scoreboard() }.getOrNull()
                if (games == null) { drawAll(app, null); return@launch }
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
    }

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
        return listOfNotNull(clock, shots).joinToString(" · ")
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
                v.setTextViewText(R.id.shots, if (g.away.sog != null) "SHOTS ${g.away.sog}–${g.home.sog}" else "")
            }
        }
        val shots = d?.let { GameAnalysis.shots(it) }.orEmpty()
        val only = if (cfg.heat == "mine") team else null
        val grid = RinkHeat.grid(shots, awayAbbrev ?: "", only)
        v.setImageViewBitmap(R.id.board, RinkArt.render(grid, awayAbbrev?.let { NhlTeams.byAbbrev(it) }, homeAbbrev?.let { NhlTeams.byAbbrev(it) }, cfg.board == "solid"))
        return v
    }
}
