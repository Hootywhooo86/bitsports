package com.bit128.bitsports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.radio.RadioService

/** While the radio is on, a bar above the tabs shows it, with play/pause and stop, from anywhere in the app. */
@Composable
fun NowPlaying() {
    val ctx = LocalContext.current
    val s by RadioService.state.collectAsState()
    if (s.status == RadioService.Status.IDLE) return
    val accent = Color(LocalUi.current.accent)
    Row(
        Modifier.fillMaxWidth().background(Card2).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(if (s.status == RadioService.Status.PLAYING) Live else Muted))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(s.station ?: "Radio", color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (s.status) {
                    RadioService.Status.CONNECTING -> "Connecting…"
                    RadioService.Status.PLAYING -> "LIVE" + (s.team?.let { " · $it radio" } ?: "")
                    RadioService.Status.PAUSED -> "Paused"
                    RadioService.Status.FAILED -> "Couldn't reach the station"
                    RadioService.Status.IDLE -> ""
                },
                color = Muted, fontSize = 12.sp,
            )
        }
        val playing = s.status == RadioService.Status.PLAYING || s.status == RadioService.Status.CONNECTING
        BarButton(if (playing) "❚❚" else "▶", accent) {
            RadioService.toggle(ctx)
        }
        Spacer(Modifier.width(6.dp))
        BarButton("■", TextInk) { RadioService.stop(ctx) }
    }
}

@Composable
private fun BarButton(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Card3).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Bold) }
}
