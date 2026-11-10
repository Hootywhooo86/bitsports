package com.bit128.bitsports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.*
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.StartingLines
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The lines each team says it's starting with, as reported before the game: forward lines, D
 * pairs, the starting goalie, both power-play and penalty-kill units, and who's out. Lines that
 * weren't together last game are marked NEW.
 */
@Composable
fun StartingLinesView(g: Game, all: Map<String, Pair<StartingLines, Set<String>>>) {
    if (all.isEmpty()) return
    SectionHead("STARTING LINES", "As reported before the game")
    listOf(g.away.abbrev, g.home.abbrev).forEach { team ->
        val (lines, fresh) = all[team] ?: return@forEach
        val ink = Color(TeamInk.of(team))
        CardBox {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(team, color = ink, fontFamily = Pixel, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                fun last(n: String) = n.substringAfter(' ', n)
                @Composable fun row(tag: String, names: List<String>, key: String?, small: Boolean = false) {
                    Row(Modifier.fillMaxWidth().padding(vertical = if (small) 3.dp else 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tag, color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                        Text(names.joinToString(" · ") { last(it) + if (it in lines.gtd) "?" else "" },
                            color = if (small) Muted else TextInk, fontSize = if (small) 12.sp else 14.sp,
                            fontWeight = if (small) FontWeight.Normal else FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (key != null && key in fresh) {
                            Text("NEW", color = ink, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Card3).padding(horizontal = 5.dp, vertical = 1.dp))
                        }
                    }
                }
                lines.forwards.forEachIndexed { i, l -> row("L${i + 1}", l, "f${i + 1}") }
                lines.defense.forEachIndexed { i, l -> row("D${i + 1}", l, "d${i + 1}") }
                if (lines.goalies.isNotEmpty()) {
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("G", color = ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(34.dp))
                        Text(last(lines.goalies.first()), color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("  STARTS", color = Color(0xFF7ED957), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        lines.goalies.getOrNull(1)?.let { Text("   backup ${last(it)}", color = Muted, fontSize = 12.sp) }
                    }
                }
                HorizontalDivider(color = Line, modifier = Modifier.padding(vertical = 6.dp))
                lines.pp.forEachIndexed { i, l -> row("PP${i + 1}", l, "pp${i + 1}", small = true) }
                lines.pk.forEachIndexed { i, l -> row("PK${i + 1}", l, null, small = true) }
                if (lines.injured.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Out: " + lines.injured.joinToString(", ") { last(it) }, color = Warn, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                val time = lines.updated?.let { DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.CANADA).format(it.atZone(ZoneId.systemDefault())) }
                Text(listOfNotNull("Daily Faceoff", lines.source?.let { "via $it" }, time).joinToString(" · ") +
                    if (lines.gtd.isNotEmpty()) " · ? game-time decision" else "", color = Dim, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    Spacer(Modifier.height(14.dp))
}
