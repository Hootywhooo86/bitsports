package com.bit128.bitsports

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.ui.GoalMoments
import com.bit128.bitsports.ui.PixelGrid
import com.bit128.bitsports.ui.TeamInk
import com.bit128.bitsports.widget.Widgets

/**
 * Pick the colour any NHL team lights up in on the boards, heat maps, alerts and widgets. Your
 * teams come first; every other team is a tap away for opponents that are hard to read.
 */
@Composable
internal fun TeamColorPicker(followed: List<String>) {
    val ctx = LocalContext.current
    val order = remember(followed) { followed + NhlTeams.all.map { it.abbrev }.filter { it !in followed } }
    var team by remember(order) { mutableStateOf(order.firstOrNull() ?: "EDM") }
    var tick by remember { mutableIntStateOf(0) }

    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        order.forEach { a ->
            key(a, tick) {
                val on = a == team
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) Card3 else Card2)
                        .border(1.dp, if (on) TextInk else Line, RoundedCornerShape(8.dp))
                        .clickable { team = a }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(Color(TeamInk.of(a))))
                    Spacer(Modifier.width(6.dp))
                    Text(a, color = if (on) TextInk else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    key(team, tick) {
        PixelGrid(remember { GoalMoments.build("text", team, null, null, frame = 2) })
    }
    Spacer(Modifier.height(12.dp))
    key(team, tick) {
        val current = TeamInk.of(team)
        val own = TeamInk.DEFAULTS[team]?.toList().orEmpty()
        val choices = (own + TeamInk.PALETTE).distinct()
        fun choose(c: Int?) {
            TeamInk.pick(team, c)
            tick++
            Widgets.refresh(ctx)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.chunked(8).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { c ->
                        Box(
                            Modifier
                                .size(32.dp)
                                .border(2.dp, if (c == current) TextInk else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { choose(c) }
                                .padding(4.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(Color(c)),
                        )
                    }
                }
            }
        }
        if (TeamInk.picked(team) != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Back to $team's own colour",
                color = Muted, fontSize = 13.sp,
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { choose(null) }.padding(vertical = 6.dp),
            )
        }
    }
}
