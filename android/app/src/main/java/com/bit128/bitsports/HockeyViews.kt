package com.bit128.bitsports

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.nhl.LeaderBoard
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.nhl.StandingRow
import com.bit128.bitsports.ui.HockeyBoard
import com.bit128.bitsports.ui.PixelJersey

// Hockey beyond tonight's games: where your teams sit, the standings, and the league leaders.

private fun ordinal(n: Int) = n.toString() + when {
    n % 100 in 11..13 -> "th"
    n % 10 == 1 -> "st"
    n % 10 == 2 -> "nd"
    n % 10 == 3 -> "rd"
    else -> "th"
}

private fun ledOf(abbrev: String) = Color(HockeyBoard.led(HockeyBoard.colors(abbrev)))

/** One card per NHL team you follow: jersey, place in its division, points and form. */
@Composable
fun YourTeamsCards(vm: BitsportsViewModel) {
    val mine = vm.followedAbbrevs().mapNotNull { vm.standingFor(it) }
    if (mine.isEmpty()) return
    SectionHead("YOUR TEAMS", "Standings")
    CardBox {
        mine.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = Line)
            val (c1, c2) = colorsOf(t.abbrev)
            Row(
                Modifier.fillMaxWidth().clickable { vm.sportsLeague = "nhl"; vm.nhlView = 1; vm.tab = Tab.SPORTS }.padding(16.dp, 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PixelJersey(c1, c2, 34.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(AlertCopy.nick(t.abbrev), color = TextInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("${ordinal(t.divisionRank)} in the ${t.division} · ${t.record}", color = Muted, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    FormDots(t.l10, ledOf(t.abbrev))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("${t.points}", color = TextInk, fontFamily = Pixel, fontSize = 28.sp)
                    Label(listOfNotNull("PTS", t.streak).joinToString(" · "))
                }
            }
        }
    }
}

private fun colorsOf(abbrev: String) = HockeyBoard.colors(abbrev).let { Color(it.first) to Color(it.second) }

/** Last ten as W / L / OT pixel blocks, from a "8-1-1" record (order unknown, so wins first). */
@Composable
private fun FormDots(l10: String, color: Color) {
    val (w, l, ot) = l10.split("-").map { it.toIntOrNull() ?: 0 }.let { Triple(it.getOrElse(0) { 0 }, it.getOrElse(1) { 0 }, it.getOrElse(2) { 0 }) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 130.dp, height = 8.dp)) {
            val s = size.height
            val step = s + 5f
            var x = 0f
            repeat(w) { drawRect(color, Offset(x, 0f), Size(s, s)); x += step }
            repeat(ot) { drawRect(color.copy(alpha = 0.35f), Offset(x, 0f), Size(s, s)); drawRect(color, Offset(x, 0f), Size(s, s / 2)); x += step }
            repeat(l) { drawRect(color.copy(alpha = 0.22f), Offset(x, 0f), Size(s, s)); x += step }
        }
        Spacer(Modifier.width(8.dp))
        Text("L10 $l10", color = Dim, fontSize = 11.sp)
    }
}

/** Division / Wild card / League, with the playoff line drawn where it falls. */
@Composable
fun StandingsView(vm: BitsportsViewModel) {
    val rows = vm.standings
    var mode by remember { mutableIntStateOf(0) }
    if (rows.isEmpty()) {
        CardBox {
            if (vm.leagueError != null) {
                InfoLine(vm.leagueError!!, color = Warn)
                RetryButton { vm.refreshLeague() }
            } else InfoLine("Loading standings…")
        }
        return
    }
    Segmented(listOf("Division", "Wild card", "League"), mode) { mode = it }
    Spacer(Modifier.height(16.dp))
    val followed = vm.followedAbbrevs()
    val maxPts = rows.maxOf { it.points }.coerceAtLeast(1)
    when (mode) {
        0 -> rows.groupBy { it.division }.toSortedMap(compareBy { DIVISION_ORDER.indexOf(it).let { i -> if (i < 0) 9 else i } })
            .forEach { (div, list) ->
                StandingsTable(div.uppercase(), list.sortedBy { it.divisionRank }, followed, maxPts, lineAfter = 3)
                Spacer(Modifier.height(16.dp))
            }
        1 -> rows.groupBy { it.conference }.toSortedMap().forEach { (conf, list) ->
            // Top three in each division are in; then the two best of the rest.
            list.groupBy { it.division }.toSortedMap(compareBy { DIVISION_ORDER.indexOf(it) }).forEach { (div, d) ->
                StandingsTable(div.uppercase(), d.sortedBy { it.divisionRank }.take(3), followed, maxPts, lineAfter = null)
                Spacer(Modifier.height(10.dp))
            }
            val rest = list.filter { it.divisionRank > 3 }.sortedBy { if (it.wildCardRank > 0) it.wildCardRank else 99 + it.conferenceRank }
            StandingsTable("${conf.uppercase()} WILD CARD", rest, followed, maxPts, lineAfter = 2)
            Spacer(Modifier.height(20.dp))
        }
        else -> StandingsTable("NHL", rows.sortedBy { it.leagueRank }, followed, maxPts, lineAfter = null)
    }
    Text("Points bars are out of the league's best (${maxPts}). Dashes mark the playoff line.", color = Dim, fontSize = 12.sp)
}

private val DIVISION_ORDER = listOf("Atlantic", "Metropolitan", "Central", "Pacific")

@Composable
private fun StandingsTable(title: String, rows: List<StandingRow>, followed: Set<String>, maxPts: Int, lineAfter: Int?) {
    val accent = Color(LocalUi.current.accent)
    CardBox {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(title, color = TextInk)
                Spacer(Modifier.weight(1f))
                Label("GP", color = Dim); Spacer(Modifier.width(34.dp))
                Label("PTS", color = Dim)
            }
            rows.forEachIndexed { i, t ->
                if (lineAfter != null && i == lineAfter) PlayoffLine(accent)
                val mine = t.abbrev in followed
                Row(
                    Modifier.fillMaxWidth().background(if (mine) Card2 else Color.Transparent).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = if (mine) accent else Dim, fontFamily = Pixel, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    Crest(colorsOf(t.abbrev), 16.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            NhlTeams.byAbbrev(t.abbrev)?.nickname ?: t.name, color = TextInk, fontSize = 14.sp,
                            fontWeight = if (mine) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        PointsBar(t.points, maxPts, ledOf(t.abbrev))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("${t.gamesPlayed}", color = Muted, fontSize = 13.sp, textAlign = TextAlign.End, modifier = Modifier.width(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("${t.points}", color = TextInk, fontFamily = Pixel, fontSize = 16.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                }
            }
        }
    }
}

/** A dashed pixel line across the table. */
@Composable
private fun PlayoffLine(color: Color) {
    Canvas(Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 16.dp)) {
        val dash = 6f
        var x = 0f
        while (x < size.width) {
            drawRect(color.copy(alpha = 0.6f), Offset(x, size.height / 2 - 1f), Size(dash, 2f))
            x += dash * 2
        }
    }
}

/** Points as a row of up to 32 pixel blocks. */
@Composable
private fun PointsBar(points: Int, max: Int, color: Color) {
    val blocks = 32
    val lit = (points.toFloat() / max * blocks).toInt().coerceIn(if (points > 0) 1 else 0, blocks)
    Canvas(Modifier.fillMaxWidth().height(4.dp)) {
        val gap = 1.5f
        val w = (size.width - gap * (blocks - 1)) / blocks
        for (i in 0 until blocks) {
            drawRect(if (i < lit) color else Color(0xFF1E1E1E), Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}

/** League leaders: skaters and goalies, top five each, tap a board for the top ten. */
@Composable
fun LeadersView(vm: BitsportsViewModel) {
    val boards = vm.leaders
    if (boards.isEmpty()) {
        CardBox {
            if (vm.leagueError != null) {
                InfoLine(vm.leagueError!!, color = Warn)
                RetryButton { vm.refreshLeague() }
            } else InfoLine("Loading league leaders…")
        }
        return
    }
    val followed = vm.followedAbbrevs()
    boards.forEach { b ->
        LeaderCard(b, followed)
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun LeaderCard(b: LeaderBoard, followed: Set<String>) {
    var open by remember(b.label) { mutableStateOf(false) }
    val accent = Color(LocalUi.current.accent)
    CardBox(Modifier.clip(CardShape).clickable { open = !open }.animateContentSize()) {
        Column(Modifier.padding(vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Label(b.label.uppercase(), color = TextInk)
                Spacer(Modifier.weight(1f))
                Label(if (open) "TOP 10 ‹" else "TOP 5 ›")
            }
            b.leaders.take(if (open) 10 else 5).forEachIndexed { i, p ->
                val mine = p.team in followed
                Row(
                    Modifier.fillMaxWidth().background(if (mine) Card2 else Color.Transparent).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${i + 1}", color = if (i == 0) accent else Dim, fontFamily = Pixel, fontSize = 12.sp, modifier = Modifier.width(24.dp))
                    val (c1, c2) = colorsOf(p.team)
                    Box(
                        Modifier.size(width = 30.dp, height = 22.dp).clip(RoundedCornerShape(4.dp)).background(c1),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(p.number?.toString() ?: "", color = if (c1.luminance() > 0.45f) Color.Black else Color.White,
                            fontFamily = Pixel, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = TextInk, fontSize = 14.sp, fontWeight = if (i == 0) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.team} · ${p.pos}", color = Muted, fontSize = 12.sp)
                    }
                    Text(p.value, color = TextInk, fontFamily = Pixel, fontSize = if (i == 0) 20.sp else 15.sp)
                }
            }
        }
    }
}
