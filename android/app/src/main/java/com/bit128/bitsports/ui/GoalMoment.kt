package com.bit128.bitsports.ui

import android.media.MediaPlayer
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.Pixel
import com.bit128.bitsports.R
import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlTeams
import kotlinx.coroutines.delay

/** The full-screen GOAL moment: lamp, burst, puck, or a plain text banner. About 2.5s, tap to dismiss. */
@Composable
fun GoalMoment(goal: GoalEvent, style: String, sound: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    LaunchedEffect(goal.gameId, goal.eventId) {
        val player = if (sound) runCatching { MediaPlayer.create(context, R.raw.goal_horn)?.apply { start() } }.getOrNull() else null
        delay(2_600)
        player?.release()
        onDone()
    }
    val team = NhlTeams.byAbbrev(goal.teamAbbrev)
    val primary = Color(team?.colors?.first ?: 0xFFFF4D6D)
    val secondary = Color(team?.colors?.second ?: 0xFFFFFFFF)
    val t = rememberInfiniteTransition(label = "goal")
    val step by t.animateFloat(0f, 1f, infiniteRepeatable(tween(500, easing = LinearEasing), RepeatMode.Restart), label = "step")
    val on = still || step < 0.5f

    Box(
        Modifier
            .fillMaxSize()
            .background(if (style == "burst") primary else Color.Black.copy(alpha = if (style == "text") 0.85f else 1f))
            .clickable(onClick = onDone),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            "burst" -> Text(
                "GOAL!", color = secondary, fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 72.sp,
                modifier = Modifier.scale(if (on) 1.08f else 0.9f),
            )
            "puck" -> PuckIntoNet(if (still) 1f else step)
            "text" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GOAL", color = Color.White, fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 40.sp)
            }
            else -> Lamp(on, if (still) 0f else step * 360f)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 72.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(team?.fullName ?: goal.teamAbbrev, color = if (style == "burst") secondary else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            if (goal.gameId >= 0) {
                Spacer(Modifier.height(6.dp))
                Text(AlertCopy.goalText(goal), color = if (style == "burst") secondary else Color(0xFFB4B4B4), fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun Lamp(on: Boolean, rays: Float) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(260.dp).rotate(rays).alpha(0.25f)) {
            val c = size.width / 26f
            val red = Color(0xFFFF4D6D)
            drawRect(red, Offset(12 * c, 0f), Size(2 * c, 7 * c)); drawRect(red, Offset(12 * c, 19 * c), Size(2 * c, 7 * c))
            drawRect(red, Offset(0f, 12 * c), Size(7 * c, 2 * c)); drawRect(red, Offset(19 * c, 12 * c), Size(7 * c, 2 * c))
        }
        Canvas(Modifier.size(140.dp)) {
            val c = size.width / 12f
            val red = Color(0xFFFF4D6D).copy(alpha = if (on) 1f else 0.35f)
            drawRect(red, Offset(4 * c, 0f), Size(4 * c, c))
            drawRect(red, Offset(3 * c, c), Size(6 * c, c))
            drawRect(red, Offset(2 * c, 2 * c), Size(8 * c, 6 * c))
            drawRect(Color(0xFFFFD0D8).copy(alpha = if (on) 1f else 0.35f), Offset(3 * c, 3 * c), Size(2 * c, 3 * c))
            drawRect(Color(0xFF3A3A3A), Offset(c, 8 * c), Size(10 * c, 2 * c))
        }
        Text("GOAL", color = Color.White, fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 34.sp, modifier = Modifier.padding(top = 200.dp))
    }
}

@Composable
private fun PuckIntoNet(progress: Float) {
    // Puck travels for the first 70%, then the net shakes.
    val travel = (progress / 0.7f).coerceAtMost(1f)
    val shake = if (progress > 0.7f) (if ((progress * 20).toInt() % 2 == 0) 1f else -1f) else 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("GOAL", color = Color.White, fontFamily = Pixel, fontWeight = FontWeight.Bold, fontSize = 40.sp)
        Spacer(Modifier.height(16.dp))
        Canvas(Modifier.width(300.dp).height(160.dp)) {
            val c = size.width / 26f
            val sx = shake * c * 0.3f
            val red = Color(0xFFFF4D6D)
            drawRect(red, Offset(17 * c + sx, 3 * c), Size(c, 8 * c))
            drawRect(red, Offset(17 * c + sx, 3 * c), Size(7 * c, c))
            drawRect(Color(0xFF3A3A3A), Offset(23 * c + sx, 3 * c), Size(c, 8 * c))
            for (x in listOf(19, 21)) drawRect(Color(0xFF2A2A2A), Offset(x * c + sx, 4 * c), Size(c, 7 * c))
            for (y in listOf(6, 8)) drawRect(Color(0xFF2A2A2A), Offset(18 * c + sx, y * c), Size(5 * c, c))
            val px = -2f + travel * 22f
            drawRect(Color.White, Offset(px * c, 9 * c), Size(3 * c, c))
            drawRect(Color(0xFFBDBDBD), Offset(px * c, 10 * c), Size(3 * c, c))
            drawRect(Color(0xFF1A2433), Offset(0f, 11 * c), Size(26 * c, c))
        }
    }
}
