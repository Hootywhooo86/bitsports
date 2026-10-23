package com.bit128.bitsports.ui

import android.media.MediaPlayer
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bit128.bitsports.R
import com.bit128.bitsports.nhl.GoalEvent
import kotlinx.coroutines.delay

/** The full-screen GOAL moment: an LED board in one of four styles. About 2.6s, tap to dismiss. */
@Composable
fun GoalMoment(goal: GoalEvent, style: String, sound: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    // Reduced motion: hold one finished frame instead of animating.
    val still = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    LaunchedEffect(goal.gameId, goal.eventId) {
        val player = if (sound) runCatching { MediaPlayer.create(context, R.raw.goal_horn)?.apply { start() } }.getOrNull() else null
        delay(2_600)
        player?.release()
        onDone()
    }
    val frame = goalFrame(running = !still)
    val preview = goal.gameId < 0
    val grid = remember(style, goal, frame) {
        GoalMoments.build(style, goal.teamAbbrev, if (preview) null else goal.scorer, if (preview) null else goal.scorerTotal, if (still) 18 else frame)
    }
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDone).padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        LedBoard(grid)
    }
}

/** A frame counter at [stepMs] a step while [running]; [loop] restarts it after a short hold. */
@Composable
fun goalFrame(running: Boolean = true, loop: Boolean = false, stepMs: Long = 120): Int {
    var frame by remember { mutableIntStateOf(0) }
    if (running) LaunchedEffect(Unit) {
        while (true) {
            delay(stepMs)
            frame = if (loop) (frame + 1) % (GoalMoments.FRAMES + 6) else frame + 1
        }
    }
    return frame
}
