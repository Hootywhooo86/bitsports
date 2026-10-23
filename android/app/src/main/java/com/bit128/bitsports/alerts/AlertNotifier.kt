package com.bit128.bitsports.alerts

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bit128.bitsports.MainActivity
import com.bit128.bitsports.R
import com.bit128.bitsports.UiPrefs
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.ui.LedScenes
import com.bit128.bitsports.widget.LedBitmaps

/** Posts every bitsports game alert. Channel sounds can't change after creation, so the horn has its own channel. */
object AlertNotifier {
    const val CH_LIVE = "live"
    const val CH_GOALS_HORN = "goals_horn"
    const val CH_GOALS_QUIET = "goals_quiet"
    private const val CH_UPDATES = "updates"
    private const val CH_PREGAME = "pregame"
    const val EXTRA_GAME = "openGame"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val horn = Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/${R.raw.goal_horn}")
        val audio = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_LIVE, "Live scoreboard", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The mini scoreboard while your team plays"; setShowBadge(false)
            },
            NotificationChannel(CH_GOALS_HORN, "Goals (with horn)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Every goal in your teams' games, with the 8-bit goal horn"; setSound(horn, audio)
            },
            NotificationChannel(CH_GOALS_QUIET, "Goals (quiet)", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Every goal, without the horn"; setSound(null, null)
            },
            NotificationChannel(CH_UPDATES, "Game updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Goal replays, period stats, AI recaps and finals"
            },
            NotificationChannel(CH_PREGAME, "Before puck drop", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "30 minutes before your team plays"
            },
        ))
    }

    fun canPost(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun openGameIntent(context: Context, gameId: Long): PendingIntent = PendingIntent.getActivity(
        context, gameId.toInt(),
        Intent(context, MainActivity::class.java).putExtra(EXTRA_GAME, gameId).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun clipIntent(context: Context, url: String, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code, Intent(Intent.ACTION_VIEW, Uri.parse(url)), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun base(context: Context, channel: String, gameId: Long) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_stat_puck)
        .setColor(0xFFFFFFFF.toInt())
        .setAutoCancel(true)
        .setContentIntent(openGameIntent(context, gameId))

    /**
     * An animated LED notification body: one RemoteViews ViewFlipper, one small bitmap per frame.
     * Cell size 3 keeps a whole scene (board + strip frames) well under the 1 MB binder limit.
     */
    private fun flipper(context: Context, layout: Int, frames: List<com.bit128.bitsports.ui.LedGrid>): RemoteViews {
        val v = RemoteViews(context.packageName, layout)
        v.removeAllViews(R.id.frames)
        frames.forEach { g ->
            val f = RemoteViews(context.packageName, R.layout.notification_led_frame)
            f.setImageViewBitmap(R.id.frame, LedBitmaps.render(g, cell = 3))
            v.addView(R.id.frames, f)
        }
        return v
    }

    /** Collapsed + heads-up get the strip, expanded gets the board; title and text stay for the lock screen and screen readers. */
    private fun NotificationCompat.Builder.animated(context: Context, scene: LedScenes.Scene): NotificationCompat.Builder =
        setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(flipper(context, R.layout.notification_led_strip, scene.strips))
            .setCustomBigContentView(flipper(context, R.layout.notification_led, scene.frames))

    private fun post(context: Context, id: Int, n: Notification) {
        if (!canPost(context)) {
            AlertLog(context).failed("Blocked: notifications are off for bitsports", null)
            return
        }
        try {
            NotificationManagerCompat.from(context).notify(id, n)
            n.extras.getCharSequence(Notification.EXTRA_TITLE)?.let { AlertLog(context).sent(it.toString()) }
        } catch (e: SecurityException) {
            AlertLog(context).failed("Blocked by Android", e)
        }
    }

    /**
     * Posts one alert. If the animated LED version can't be built or posted, the same alert goes
     * out as plain text: an alert that arrives plain beats a fancy one that never arrives.
     */
    fun post(context: Context, alert: Alert, game: Game, prefs: UiPrefs, goals: List<GoalEvent>) {
        try {
            postRich(context, alert, game, prefs, goals)
        } catch (e: Exception) {
            AlertLog(context).failed("LED alert failed, sent plain", e)
            postPlain(context, alert, game, prefs)
        }
    }

    private fun postPlain(context: Context, alert: Alert, game: Game, prefs: UiPrefs) {
        val (id, channel, title, text) = when (alert) {
            is Alert.Goal -> listOf(("g" + alert.goal.gameId + alert.goal.eventId).hashCode(), if (prefs.goalSound) CH_GOALS_HORN else CH_GOALS_QUIET,
                AlertCopy.goalTitle(alert.goal, game), AlertCopy.goalText(alert.goal))
            is Alert.Replay -> listOf(("r" + alert.goal.gameId + alert.goal.eventId).hashCode(), CH_UPDATES, AlertCopy.replayTitle(alert.goal), "The replay is up.")
            is Alert.PeriodEnd -> listOf(("p" + game.id + alert.stats.period).hashCode(), CH_UPDATES, "End of ${alert.label}", AlertCopy.periodText(alert.stats))
            is Alert.Final -> listOf(("f" + game.id).hashCode(), CH_UPDATES, AlertCopy.finalTitle(alert.game, alert.shootout), AlertCopy.finalText(alert.threeStars))
            is Alert.Pregame -> listOf(("g" + game.id).hashCode(), CH_PREGAME, AlertCopy.pregameTitle(alert.game), AlertCopy.pregameText(alert.game))
            is Alert.CatchUp -> listOf(("c" + game.id).hashCode(), CH_GOALS_QUIET, AlertCopy.catchUpTitle(alert.game), AlertCopy.catchUpText(alert.goals))
        }
        post(context, id as Int, base(context, channel as String, game.id)
            .setContentTitle(title as String).setContentText(text as String)
            .setPriority(NotificationCompat.PRIORITY_HIGH).build())
    }

    private fun postRich(context: Context, alert: Alert, game: Game, prefs: UiPrefs, goals: List<GoalEvent>) {
        when (alert) {
            is Alert.Goal -> goal(context, alert.goal, game, prefs)
            is Alert.CatchUp -> post(context, ("c" + game.id).hashCode(),
                base(context, CH_GOALS_QUIET, game.id)
                    .setContentTitle(AlertCopy.catchUpTitle(alert.game))
                    .setContentText(AlertCopy.catchUpText(alert.goals))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .animated(context, LedScenes.live(alert.game, alert.goals.lastOrNull(), prefs.accent.toInt()))
                    .build())
            is Alert.Replay -> post(context, ("r" + alert.goal.gameId + alert.goal.eventId).hashCode(),
                base(context, CH_UPDATES, game.id)
                    .setContentTitle(AlertCopy.replayTitle(alert.goal))
                    .setContentText("The replay is up.")
                    .setContentIntent(clipIntent(context, alert.goal.clipUrl!!, alert.goal.eventId))
                    .build())
            is Alert.PeriodEnd -> {
                val after = goals.lastOrNull { it.period <= alert.stats.period }
                post(context, ("p" + game.id + alert.stats.period).hashCode(),
                    base(context, CH_UPDATES, game.id)
                        .setContentTitle(AlertCopy.periodTitle(alert.stats, alert.label, game, goals))
                        .setContentText(AlertCopy.periodText(alert.stats))
                        .animated(context, LedScenes.periodEnd(alert.stats, alert.label, game, after?.awayScore ?: 0, after?.homeScore ?: 0, prefs.accent.toInt()))
                        .build())
            }
            is Alert.Final -> post(context, ("f" + game.id).hashCode(),
                base(context, CH_UPDATES, game.id)
                    .setContentTitle(AlertCopy.finalTitle(alert.game, alert.shootout))
                    .setContentText(AlertCopy.finalText(alert.threeStars))
                    .animated(context, LedScenes.final(alert.game, alert.shootout, alert.threeStars, prefs.accent.toInt()))
                    .build())
            is Alert.Pregame -> post(context, ("g" + game.id).hashCode(),
                base(context, CH_PREGAME, game.id)
                    .setContentTitle(AlertCopy.pregameTitle(alert.game))
                    .setContentText(AlertCopy.pregameText(alert.game))
                    .build())
        }
    }

    private fun goal(context: Context, g: GoalEvent, game: Game, prefs: UiPrefs) {
        val b = base(context, if (prefs.goalSound) CH_GOALS_HORN else CH_GOALS_QUIET, game.id)
            .setContentTitle(AlertCopy.goalTitle(g, game))
            .setContentText(AlertCopy.goalText(g))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .animated(context, LedScenes.goal(g, game, prefs.accent.toInt()))
        g.clipUrl?.let { b.addAction(0, "Watch the goal", clipIntent(context, it, g.eventId)) }
        post(context, ("g" + g.gameId + g.eventId).hashCode(), b.build())
    }

    fun recap(context: Context, game: Game, label: String, text: String) = post(context, ("a" + game.id + label).hashCode(),
        base(context, CH_UPDATES, game.id)
            .setContentTitle("$label recap · ${AlertCopy.nick(game.away.abbrev)} @ ${AlertCopy.nick(game.home.abbrev)}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build())

    /** Settings → "Send a test alert": a real goal alert, so you can hear and see what you'll get. */
    fun test(context: Context, prefs: UiPrefs, teamAbbrev: String?): Boolean {
        ensureChannels(context)
        if (!canPost(context)) return false
        val abbrev = teamAbbrev ?: "EDM"
        val game = Game(
            id = -1, state = com.bit128.bitsports.nhl.GameState.LIVE, scheduleOk = true, startUtc = java.time.Instant.now(),
            away = com.bit128.bitsports.nhl.TeamLine(0, abbrev, 1, 12), home = com.bit128.bitsports.nhl.TeamLine(1, if (abbrev == "CGY") "EDM" else "CGY", 0, 9),
            clock = null,
        )
        val g = GoalEvent(
            gameId = -1, eventId = 1, teamAbbrev = abbrev, scorer = "Test alert", scorerTotal = null, assists = emptyList(),
            strength = com.bit128.bitsports.nhl.Strength.EV, shotType = null, goalie = null, period = 1, periodType = "REG",
            timeInPeriod = "12:34", awayScore = 1, homeScore = 0, clipUrl = null,
        )
        goal(context, g, game, prefs)
        return true
    }

    /** A tap-to-follow nudge when Android won't let the watcher start in the background. */
    fun followNudge(context: Context, game: Game) = post(context, ("n" + game.id).hashCode(),
        base(context, CH_PREGAME, game.id)
            .setContentTitle("${AlertCopy.nick(game.away.abbrev)} @ ${AlertCopy.nick(game.home.abbrev)} is about to start")
            .setContentText("Tap to follow it live.")
            .build())

    fun scoreboard(context: Context, game: Game, stale: String?, stopIntent: PendingIntent, lastGoal: GoalEvent? = null): Notification {
        val v = RemoteViews(context.packageName, R.layout.notification_scoreboard)
        val away = NhlTeams.byAbbrev(game.away.abbrev)
        val home = NhlTeams.byAbbrev(game.home.abbrev)
        fun crest(c1: Int, c2: Int, t: com.bit128.bitsports.nhl.NhlTeam?) {
            v.setInt(c1, "setBackgroundColor", (t?.colors?.first ?: 0xFF555555).toInt())
            v.setInt(c2, "setBackgroundColor", (t?.colors?.second ?: 0xFF888888).toInt())
        }
        crest(R.id.away_c1, R.id.away_c2, away)
        crest(R.id.home_c1, R.id.home_c2, home)
        v.setTextViewText(R.id.away_abbrev, game.away.abbrev)
        v.setTextViewText(R.id.home_abbrev, game.home.abbrev)
        v.setTextViewText(R.id.score, "${game.away.score ?: 0}–${game.home.score ?: 0}")
        v.setTextViewText(R.id.live, when {
            game.state.isOver -> "FINAL"
            game.clock?.inIntermission == true -> "INT"
            game.state.isLive -> "■ LIVE"
            else -> "SOON"
        })
        v.setTextViewText(R.id.clock, AlertCopy.scoreboardLine(game))
        v.setTextViewText(R.id.sog, if (game.away.sog != null) "SOG ${game.away.sog}–${game.home.sog}" else "")
        if (stale != null) {
            v.setViewVisibility(R.id.stale, android.view.View.VISIBLE)
            v.setTextViewText(R.id.stale, stale)
        } else v.setViewVisibility(R.id.stale, android.view.View.GONE)

        // Expanded: the animated jumbotron, with how stale it is underneath when the feed lags.
        val accent = com.bit128.bitsports.PrefsStore(context).load().accent.toInt()
        val big = flipper(context, R.layout.notification_led, LedScenes.live(game, lastGoal, accent).frames)
        if (stale != null) {
            big.setViewVisibility(R.id.caption, android.view.View.VISIBLE)
            big.setTextViewText(R.id.caption, stale)
        }
        return NotificationCompat.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_puck)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(v)
            .setCustomBigContentView(big)
            .setContentTitle("${game.away.abbrev} ${game.away.score ?: 0}–${game.home.score ?: 0} ${game.home.abbrev}")
            .setOngoing(!game.state.isOver)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openGameIntent(context, game.id))
            .addAction(0, "Open game", openGameIntent(context, game.id))
            .addAction(0, "Stop for tonight", stopIntent)
            .build()
    }
}
