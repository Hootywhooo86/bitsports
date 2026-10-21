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

/** Posts every bitsports game alert. Channel sounds can't change after creation, so the horn has its own channel. */
object AlertNotifier {
    const val CH_LIVE = "live"
    private const val CH_GOALS_HORN = "goals_horn"
    private const val CH_GOALS_QUIET = "goals_quiet"
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

    private fun post(context: Context, id: Int, n: Notification) {
        if (!canPost(context)) return
        try { NotificationManagerCompat.from(context).notify(id, n) } catch (_: SecurityException) { }
    }

    fun post(context: Context, alert: Alert, game: Game, prefs: UiPrefs, goals: List<GoalEvent>) {
        when (alert) {
            is Alert.Goal -> goal(context, alert.goal, game, prefs)
            is Alert.Replay -> post(context, ("r" + alert.goal.gameId + alert.goal.eventId).hashCode(),
                base(context, CH_UPDATES, game.id)
                    .setContentTitle(AlertCopy.replayTitle(alert.goal))
                    .setContentText("The replay is up.")
                    .setContentIntent(clipIntent(context, alert.goal.clipUrl!!, alert.goal.eventId))
                    .build())
            is Alert.PeriodEnd -> post(context, ("p" + game.id + alert.stats.period).hashCode(),
                base(context, CH_UPDATES, game.id)
                    .setContentTitle(AlertCopy.periodTitle(alert.stats, alert.label, game, goals))
                    .setContentText(AlertCopy.periodText(alert.stats))
                    .setStyle(NotificationCompat.BigTextStyle().bigText(AlertCopy.periodText(alert.stats)))
                    .build())
            is Alert.Final -> post(context, ("f" + game.id).hashCode(),
                base(context, CH_UPDATES, game.id)
                    .setContentTitle(AlertCopy.finalTitle(alert.game, alert.shootout))
                    .setContentText(AlertCopy.finalText(alert.threeStars))
                    .build())
            is Alert.Pregame -> post(context, ("g" + game.id).hashCode(),
                base(context, CH_PREGAME, game.id)
                    .setContentTitle(AlertCopy.pregameTitle(alert.game))
                    .setContentText(AlertCopy.pregameText(alert.game))
                    .build())
        }
    }

    private fun goal(context: Context, g: GoalEvent, game: Game, prefs: UiPrefs) {
        val team = NhlTeams.byAbbrev(g.teamAbbrev)
        val art = team?.let { GoalArt.render(context, prefs.goalStyle, it.colors) }
        val b = base(context, if (prefs.goalSound) CH_GOALS_HORN else CH_GOALS_QUIET, game.id)
            .setContentTitle(AlertCopy.goalTitle(g, game))
            .setContentText(AlertCopy.goalText(g))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        if (art != null) {
            b.setLargeIcon(art).setStyle(
                NotificationCompat.BigPictureStyle().bigPicture(art).bigLargeIcon(null as android.graphics.Bitmap?)
                    .setSummaryText(AlertCopy.goalDetail(g).replace("\n", " · "))
            )
        } else {
            b.setStyle(NotificationCompat.BigTextStyle().bigText(AlertCopy.goalDetail(g)))
        }
        g.clipUrl?.let { b.addAction(0, "Watch the goal", clipIntent(context, it, g.eventId)) }
        post(context, ("g" + g.gameId + g.eventId).hashCode(), b.build())
    }

    fun recap(context: Context, game: Game, label: String, text: String) = post(context, ("a" + game.id + label).hashCode(),
        base(context, CH_UPDATES, game.id)
            .setContentTitle("$label recap · ${AlertCopy.nick(game.away.abbrev)} @ ${AlertCopy.nick(game.home.abbrev)}")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .build())

    /** A tap-to-follow nudge when Android won't let the watcher start in the background. */
    fun followNudge(context: Context, game: Game) = post(context, ("n" + game.id).hashCode(),
        base(context, CH_PREGAME, game.id)
            .setContentTitle("${AlertCopy.nick(game.away.abbrev)} @ ${AlertCopy.nick(game.home.abbrev)} is about to start")
            .setContentText("Tap to follow it live.")
            .build())

    fun scoreboard(context: Context, game: Game, stale: String?, stopIntent: PendingIntent): Notification {
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

        return NotificationCompat.Builder(context, CH_LIVE)
            .setSmallIcon(R.drawable.ic_stat_puck)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(v)
            .setCustomBigContentView(v)
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
