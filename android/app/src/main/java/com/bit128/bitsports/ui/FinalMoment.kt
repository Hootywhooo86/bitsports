package com.bit128.bitsports.ui

import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** The full-screen final-horn moment: wipe, three stars, shutout padlock, the Cup. Tap to dismiss. */
@Composable
fun FinalMoment(info: FinalMoments.Info, onDone: () -> Unit) {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    val total = FinalMoments.frames(info)
    var frame by remember { mutableIntStateOf(if (still) total - 1 else 0) }
    LaunchedEffect(info) {
        if (!still) while (frame < total - 1) { delay(120); frame++ }
        delay(2_500)
        onDone()
    }
    val grid = remember(info, frame) { FinalMoments.build(info, frame) }
    Box(Modifier.fillMaxSize().background(Color.Black).clickable(onClick = onDone).padding(16.dp), contentAlignment = Alignment.Center) {
        LedBoard(grid)
    }
}
