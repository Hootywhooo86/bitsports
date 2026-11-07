package com.bit128.bitsports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.*
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameOdds
import com.bit128.bitsports.nhl.NhlOddsParser
import com.bit128.bitsports.nhl.OddsHistory
import com.bit128.bitsports.nhl.OddsSample
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.bit128.bitsports.nhl.TeamOdds
import java.time.Duration
import java.time.Instant

/**
 * The betting market on the game: who's favoured to win (with the chance the price implies, as a
 * pixel bar in team colours), the puck line and the goal total. Refreshes with the play-by-play,
 * about every 20 seconds while the game is on.
 */
@Composable
internal fun OddsCard(g: Game, o: GameOdds, history: List<OddsSample>, now: Instant) {
    val (awayInk, homeInk) = TeamInk.pair(g.away.abbrev, g.home.abbrev)
    Spacer(Modifier.height(12.dp))
    CardBox {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(if (g.state.isLive) "LIVE ODDS" else "ODDS", color = if (g.state.isLive) Live else TextInk)
                Spacer(Modifier.weight(1f))
                Text(listOfNotNull(o.partner, o.updated?.let { ago(it, now) }).joinToString(" · "), color = Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(12.dp))
            // The WIN-O-METER: the chance each side wins as the market prices it, and how it's swung.
            val share = OddsHistory.share(o)
            val frame = (now.epochSecond / 5).toInt()
            // A big swing (a goal, usually): remember where the puck was and play the zoom.
            var last by remember(g.id) { mutableStateOf(share) }
            var swingFrom by remember(g.id) { mutableStateOf<Float?>(null) }
            var swingAge by remember(g.id) { mutableIntStateOf(OddsMeter.SWING_FRAMES) }
            LaunchedEffect(share) {
                val prev = last
                last = share
                if (prev != null && share != null && kotlin.math.abs(share - prev) >= OddsMeter.BIG_SWING) {
                    swingFrom = prev
                    for (k in 0..OddsMeter.SWING_FRAMES) { swingAge = k; kotlinx.coroutines.delay(80) }
                    swingFrom = null
                }
            }
            val board = remember(share, history, g.state.isLive, frame, awayInk, homeInk, swingFrom, swingAge) {
                OddsMeter.board(g.away.abbrev, g.home.abbrev, awayInk, homeInk, share, history, g.state.isLive, frame, swingFrom, swingAge)
            }
            PixelGrid(board)
            Spacer(Modifier.height(6.dp))
            Text(
                if (history.size < 2) "Momentum fills in as the odds move during the game." else "Momentum: above the line ${g.away.abbrev}'s chances rose, below it ${g.home.abbrev}'s. Gold dots are goals.",
                color = Dim, fontSize = 11.sp,
            )
            Spacer(Modifier.height(14.dp))
            Header()
            HorizontalDivider(color = Line)
            TeamRow(g.away.abbrev, o.away, Color(awayInk))
            TeamRow(g.home.abbrev, o.home, Color(homeInk))
            Spacer(Modifier.height(10.dp))
            Text("19+. Before puck drop: the NHL's betting partner (FanDuel). During the game: DraftKings live odds. For information only; please play responsibly.", color = Dim, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Header() {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text("", Modifier.weight(1f))
        listOf("TO WIN", "PUCK LINE", "TOTAL").forEach {
            Text(it, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
        }
    }
}

@Composable
private fun TeamRow(abbrev: String, t: TeamOdds, ink: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(abbrev, color = ink, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Cell(NhlOddsParser.american(t.moneyLine), null)
        Cell(t.puckLine ?: "–", t.puckLinePrice?.let(NhlOddsParser::american))
        Cell(t.total ?: "–", t.totalPrice?.let(NhlOddsParser::american))
    }
}

@Composable
private fun RowScope.Cell(main: String, price: String?) {
    Column(Modifier.weight(1.2f)) {
        Text(main, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (price != null) Text(price, color = Muted, fontSize = 12.sp)
    }
}

private fun ago(t: Instant, now: Instant): String {
    val s = Duration.between(t, now).seconds.coerceAtLeast(0)
    return when { s < 60 -> "updated just now"; s < 3600 -> "updated ${s / 60}m ago"; else -> "updated ${s / 3600}h ago" }
}
