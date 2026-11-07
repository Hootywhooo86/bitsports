package com.bit128.bitsports.ui

import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bit128.bitsports.*
import com.bit128.bitsports.nhl.Clip
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlClips
import kotlinx.coroutines.delay

/** A run of goal clips to play: every goal in a game with a clip, starting at [index]. */
data class ClipReel(val game: Game, val goals: List<GoalEvent>, val index: Int)

/**
 * The NHL's official goal clip, full screen, dressed like a broadcast: your goal moment plays as
 * the intro while it loads, then the video runs under an LED score bug and a lower third with
 * the scorer and assists. Prev / next walk the whole game's goals, and it rolls on to the next
 * goal by itself.
 */
@Composable
fun ClipPlayer(reel: ClipReel, style: String, onIndex: (Int) -> Unit, onClose: () -> Unit) {
    val goal = reel.goals[reel.index]
    var clip by remember(goal.eventId) { mutableStateOf<Clip?>(null) }
    var error by remember(goal.eventId) { mutableStateOf<String?>(null) }
    var intro by remember(goal.eventId) { mutableIntStateOf(0) }
    var playing by remember(goal.eventId) { mutableStateOf(true) }
    var controls by remember(goal.eventId) { mutableStateOf(true) }
    var started by remember(goal.eventId) { mutableStateOf(false) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    val special = remember(goal.eventId) { GoalSpecial.of(goal, reel.goals) }
    val scoredHome = goal.teamAbbrev == reel.game.home.abbrev
    val before = if (scoredHome) "${goal.awayScore}-${goal.homeScore - 1}" else "${(goal.awayScore - 1).coerceAtLeast(0)}-${goal.homeScore}"

    LaunchedEffect(goal.eventId) {
        val id = NhlClips.idFrom(goal.clipUrl)
        if (id == null) { error = "No clip for this goal yet"; return@LaunchedEffect }
        try { clip = NhlClips.clip(id) } catch (e: Exception) { error = "Couldn't load the clip" }
    }
    // The intro: about two seconds of the goal moment while the video buffers.
    LaunchedEffect(goal.eventId) { while (intro < 16) { delay(120); intro++ } }
    // Controls fade after a few seconds of no taps.
    LaunchedEffect(controls, goal.eventId) { if (controls) { delay(3_500); controls = false } }
    var lt by remember(goal.eventId) { mutableIntStateOf(0) }
    LaunchedEffect(goal.eventId, started) { if (started) while (lt < 8) { delay(70); lt++ } }

    Box(Modifier.fillMaxSize().background(Color.Black).clickable { controls = !controls }) {
        clip?.playable?.let { url ->
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setOnPreparedListener { mp -> mp.isLooping = false }
                        setOnCompletionListener {
                            playing = false
                            if (reel.index < reel.goals.lastIndex) onIndex(reel.index + 1)
                        }
                        view = this
                    }
                },
                update = { v ->
                    if (v.tag != url) {
                        v.tag = url
                        v.setVideoURI(Uri.parse(url))
                    }
                    // Start once the intro has had its moment.
                    if (intro >= 12 && playing && !v.isPlaying) { v.start(); started = true }
                    if (!playing && v.isPlaying) v.pause()
                },
                modifier = Modifier.fillMaxWidth().align(Alignment.Center),
            )
        }
        // LED overlays on the video.
        if (started) {
            Box(Modifier.align(Alignment.TopStart).padding(16.dp).width(150.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xCC000000)).padding(4.dp)) {
                PixelGrid(remember(goal.eventId) { ClipOverlays.bug(reel.game, goal) })
            }
            Box(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 96.dp).fillMaxWidth().background(Color(0xCC000000)).padding(4.dp)) {
                PixelGrid(remember(goal.eventId, lt) { ClipOverlays.lowerThird(goal, lt) })
            }
        }
        // The intro takes the whole screen, then fades away.
        AnimatedVisibility(intro < 14 || (clip == null && error == null), enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            Box(Modifier.fillMaxWidth().padding(16.dp)) {
                LedBoard(remember(goal.eventId, intro) {
                    GoalMoments.build(style, goal.teamAbbrev, goal.scorer, goal.scorerTotal, intro.coerceAtMost(GoalMoments.FRAMES - 1), special, before, "${goal.awayScore}-${goal.homeScore}")
                })
            }
        }
        error?.let {
            Text(it, color = Muted, fontSize = 14.sp, modifier = Modifier.align(Alignment.Center).padding(top = 160.dp))
        }
        // Controls.
        if (controls || error != null) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ClipButton("‹ PREV", reel.index > 0) { onIndex(reel.index - 1) }
                ClipButton(if (playing) "❚❚" else "▶", clip != null) {
                    if (!playing && view?.isPlaying == false && (view?.currentPosition ?: 0) >= (view?.duration ?: 1) - 500) view?.seekTo(0)
                    playing = !playing
                }
                Text("GOAL ${reel.index + 1} OF ${reel.goals.size}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                ClipButton("NEXT ›", reel.index < reel.goals.lastIndex) { onIndex(reel.index + 1) }
                ClipButton("✕", true, onClose)
            }
        }
    }
}

@Composable
private fun ClipButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xCC1C1C1C))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = if (enabled) TextInk else Dim, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
}
