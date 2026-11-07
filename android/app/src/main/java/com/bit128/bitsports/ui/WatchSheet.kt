package com.bit128.bitsports.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.*
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GameExtras
import com.bit128.bitsports.nhl.NhlRadio
import com.bit128.bitsports.nhl.WatchOptions
import com.bit128.bitsports.overlay.OverlayService
import com.bit128.bitsports.radio.RadioService

/**
 * ▶ WATCH: every legal way to follow this game right now, free ones first (CBC Gem when it's
 * Hockey Night in Canada, the radio call, the goal clips, NHL GameCenter), then the services
 * carrying it. Pick one and, if you like, the bitsports scoreboard floats over it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchLive(vm: BitsportsViewModel, g: Game, d: GameDetail?, x: GameExtras?) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val mine = listOf(g.away.abbrev, g.home.abbrev).firstOrNull { it in vm.followedAbbrevs() } ?: g.home.abbrev
    val goals = d?.let { GameAnalysis.goals(it) }.orEmpty()
    val options = remember(x?.broadcasts, g.state, goals.size) {
        WatchOptions.of(g, x?.broadcasts.orEmpty(), NhlRadio.forTeam(mine, vm.ui.radioUrl), goals.any { it.clipUrl != null })
    }
    val accent = Color(LocalUi.current.accent)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = { open = true },
        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
        shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text("▶ WATCH", fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text("${options.size} ways" + (options.count { it.free }.takeIf { it > 0 }?.let { " · $it free" } ?: ""), fontSize = 12.sp)
    }
    if (!open) return

    var float by remember { mutableStateOf(OverlayService.allowed(ctx)) }
    ModalBottomSheet(onDismissRequest = { open = false }, containerColor = Card) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Text("Watch ${g.away.abbrev} @ ${g.home.abbrev}", color = TextInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Free first. Services need their own account.", color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            // The floating scoreboard rides on top of whatever you pick.
            ToggleRow("Float the bitsports scoreboard over it", float) { on ->
                if (on && !OverlayService.allowed(ctx)) ctx.startActivity(OverlayService.permissionIntent(ctx))
                float = on
            }
            if (float && !OverlayService.allowed(ctx)) {
                Text("Allow bitsports to display over other apps, then come back.", color = Warn, fontSize = 12.sp)
            }
            Spacer(Modifier.height(8.dp))
            options.forEach { o ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = o.url != null || o.kind == WatchOptions.Kind.CLIPS) {
                            open = false
                            when (o.kind) {
                                WatchOptions.Kind.RADIO -> RadioService.play(ctx, o.name.removeSuffix(" radio"), o.url!!, mine)
                                WatchOptions.Kind.CLIPS -> goals.firstOrNull { it.clipUrl != null }?.let { vm.playClips(g, goals, it) }
                                else -> {
                                    if (float && !g.state.isOver) OverlayService.start(ctx, g.id)
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(o.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(o.name, color = if (o.url != null || o.kind == WatchOptions.Kind.CLIPS) TextInk else Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            if (o.free) {
                                Spacer(Modifier.width(8.dp))
                                Text("FREE", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color(0xFF7ED957)).padding(horizontal = 5.dp, vertical = 1.dp))
                            }
                        }
                        Text(o.detail, color = Muted, fontSize = 12.sp)
                    }
                    Text(when (o.kind) {
                        WatchOptions.Kind.RADIO -> "♪"
                        WatchOptions.Kind.CLIPS -> "▶"
                        WatchOptions.Kind.TV -> "TV"
                        else -> "↗"
                    }, color = Muted, fontSize = 15.sp)
                }
                HorizontalDivider(color = Line)
            }
            if (!g.state.isOver) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(
                    onClick = {
                        open = false
                        if (!OverlayService.start(ctx, g.id)) ctx.startActivity(OverlayService.permissionIntent(ctx))
                    },
                    border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                ) { Text("Just float the scoreboard", color = TextInk, fontWeight = FontWeight.SemiBold) }
                Text("Drag it anywhere. Tap to resize, double-tap to close.", color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
