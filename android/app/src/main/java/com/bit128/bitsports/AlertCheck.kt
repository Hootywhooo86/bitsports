package com.bit128.bitsports

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bit128.bitsports.alerts.AlertLog
import com.bit128.bitsports.alerts.AlertNotifier
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.alerts.GameWatchService
import java.time.Duration
import java.time.Instant

/**
 * Settings → Alert check: every condition goal alerts need, each with a pass/fail light and the
 * button that fixes it, then what the alert machinery last did. If alerts don't arrive, this says
 * exactly where they stop.
 */
@Composable
fun AlertCheck(vm: BitsportsViewModel) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) { tick++; AlertScheduler.refresh(ctx) } }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    // After two no's Android stops showing the prompt and answers "denied" at once; then only
    // the app's notification settings can turn alerts on, so go straight there.
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
        tick++
    }
    // vm.now ticks every 5s, so "last checked 12s ago" and the watcher light stay current.
    val now = vm.now
    val s = remember(tick, now, vm.teams, vm.ui) { Status.read(ctx, vm) }

    CardBox {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("ALERT CHECK", color = TextInk)
                Spacer(Modifier.weight(1f))
                Label(if (s.blocking == 0) "ALL GOOD" else "${s.blocking} TO FIX", color = if (s.blocking == 0) Good else Warn)
            }
            Spacer(Modifier.height(10.dp))
            Check(s.team, "NHL team followed", s.teamText)
            Check(s.notifications, "Notifications allowed", if (s.notifications) "bitsports can post alerts" else "Android is blocking every bitsports alert", "Allow") {
                if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
            }
            Check(s.goalChannel, "Goal alerts switched on", s.goalChannelText, "Open") {
                val i = if (Build.VERSION.SDK_INT >= 26 && vm.ui.alertGoals) Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, s.goalChannelId)
                else Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                if (vm.ui.alertGoals) ctx.startActivity(i) else vm.updateUi(vm.ui.copy(alertGoals = true))
            }
            Check(s.exact, "Alarms allowed", if (s.exact) "Watching starts on time, app closed" else "Watching can't start by itself before puck drop", "Allow") {
                if (Build.VERSION.SDK_INT >= 31) ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
            }
            Check(s.notRestricted, "Background allowed", if (s.notRestricted) "Android lets bitsports run during games" else "Battery is set to Restricted, so Android stops game watching", "Fix") {
                ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
            }
            Check(s.unoptimised, "Battery: unrestricted", if (s.unoptimised) "Alarms and checks won't be delayed" else "Recommended: battery saving can delay game checks", "Allow", required = false) {
                @Suppress("BatteryLife")
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = Line)
            Spacer(Modifier.height(10.dp))
            Label("TONIGHT")
            Spacer(Modifier.height(6.dp))
            InfoRow("Schedule", s.log.schedule?.text ?: "Not checked yet")
            InfoRow("Watching", if (s.watching) "Yes, the mini scoreboard is in your notifications" else "Not right now")
            InfoRow("Last check", s.log.lastCheck?.let { "${it.text} · ${ago(it.at, now)}" } ?: "None yet")
            InfoRow("Last alert", s.log.lastSent?.let { "${it.text} · ${ago(it.at, now)}" } ?: "None yet")
            s.log.lastError?.let { InfoRow("Last problem", "${it.text} · ${ago(it.at, now)}", Warn) }

            val game = vm.favoriteNhlGames().firstOrNull { !it.state.isOver }
            if (game != null && !s.watching) {
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { GameWatchService.start(ctx, game.id); tick++ },
                    border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) { Text("Watch ${game.away.abbrev} @ ${game.home.abbrev} now", color = TextInk, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

private val Good = Color(0xFF5EF2B0)

@Composable
private fun Check(ok: Boolean, title: String, detail: String, action: String? = null, required: Boolean = true, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(if (ok) Good else if (required) Live else Warn))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = Muted, fontSize = 12.sp)
        }
        if (!ok && action != null) {
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = onAction, border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.height(36.dp),
            ) { Text(action, color = TextInk, fontSize = 13.sp) }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = TextInk) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = Muted, fontSize = 12.sp, modifier = Modifier.width(92.dp))
        Text(value, color = color, fontSize = 12.sp)
    }
}

private fun ago(at: Instant, now: Instant): String {
    val s = Duration.between(at, now).seconds.coerceAtLeast(0)
    return when {
        s < 60 -> "${s}s ago"
        s < 3600 -> "${s / 60}m ago"
        s < 86_400 -> "${s / 3600}h ago"
        else -> "${s / 86_400}d ago"
    }
}

private data class Status(
    val team: Boolean, val teamText: String,
    val notifications: Boolean,
    val goalChannel: Boolean, val goalChannelId: String, val goalChannelText: String,
    val exact: Boolean, val notRestricted: Boolean, val unoptimised: Boolean,
    val watching: Boolean, val log: AlertLog,
) {
    val blocking get() = listOf(team, notifications, goalChannel, exact, notRestricted).count { !it }

    companion object {
        fun read(ctx: Context, vm: BitsportsViewModel): Status {
            val followed = vm.followedAbbrevs()
            val channelId = if (vm.ui.goalSound) AlertNotifier.CH_GOALS_HORN else AlertNotifier.CH_GOALS_QUIET
            AlertNotifier.ensureChannels(ctx)
            val channelOn = Build.VERSION.SDK_INT < 26 ||
                ctx.getSystemService(NotificationManager::class.java).getNotificationChannel(channelId)?.importance != NotificationManager.IMPORTANCE_NONE
            val restricted = Build.VERSION.SDK_INT >= 28 && ctx.getSystemService(ActivityManager::class.java).isBackgroundRestricted
            return Status(
                team = followed.isNotEmpty(),
                teamText = if (followed.isEmpty()) "Alerts are for NHL teams: pick one under Your teams" else "Following ${followed.joinToString(", ")}",
                notifications = AlertNotifier.canPost(ctx),
                goalChannel = channelOn && vm.ui.alertGoals,
                goalChannelId = channelId,
                goalChannelText = when {
                    !vm.ui.alertGoals -> "Goals are switched off below"
                    !channelOn -> "The Goals channel is turned off in Android"
                    else -> "Every goal in your teams' games"
                },
                exact = AlertScheduler.canExact(ctx),
                notRestricted = !restricted,
                unoptimised = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName),
                watching = GameWatchService.watchingAny(),
                log = AlertLog(ctx),
            )
        }

    }
}
