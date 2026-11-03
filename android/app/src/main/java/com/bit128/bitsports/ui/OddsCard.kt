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
import com.bit128.bitsports.nhl.TeamOdds
import java.time.Duration
import java.time.Instant

/**
 * The betting market on the game: who's favoured to win (with the chance the price implies, as a
 * pixel bar in team colours), the puck line and the goal total. Refreshes with the play-by-play,
 * about every 20 seconds while the game is on.
 */
@Composable
internal fun OddsCard(g: Game, o: GameOdds, now: Instant) {
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
            // The chance each side wins as the market prices it, the book's margin taken out.
            val a = o.away.moneyLine?.let(NhlOddsParser::implied)
            val h = o.home.moneyLine?.let(NhlOddsParser::implied)
            if (a != null && h != null) {
                val share = a / (a + h)
                Row(Modifier.fillMaxWidth()) {
                    Text("${g.away.abbrev} ${(share * 100).toInt()}%", color = Color(awayInk), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${100 - (share * 100).toInt()}% ${g.home.abbrev}", color = Color(homeInk), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().height(8.dp)) {
                    if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().background(Color(awayInk)))
                    Spacer(Modifier.width(2.dp))
                    if (share < 1f) Box(Modifier.weight(1f - share).fillMaxHeight().background(Color(homeInk)))
                }
                Spacer(Modifier.height(14.dp))
            }
            Header()
            HorizontalDivider(color = Line)
            TeamRow(g.away.abbrev, o.away, Color(awayInk))
            TeamRow(g.home.abbrev, o.home, Color(homeInk))
            Spacer(Modifier.height(10.dp))
            Text("19+. Odds from the NHL's betting partner, for information. Please play responsibly.", color = Dim, fontSize = 11.sp)
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
