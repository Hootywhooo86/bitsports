package com.bit128.bitsports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.fantasy.FantasyList
import com.bit128.bitsports.fantasy.FantasyNight
import com.bit128.bitsports.fantasy.Scoring
import com.bit128.bitsports.nhl.Injuries
import com.bit128.bitsports.nhl.PlayerRef
import com.bit128.bitsports.ui.LedGrid
import com.bit128.bitsports.ui.PixelArt
import com.bit128.bitsports.ui.PixelGrid
import com.bit128.bitsports.ui.TeamInk
import com.bit128.bitsports.ui.goalFrame
import com.bit128.bitsports.ui.ScoreFormat
import kotlinx.coroutines.delay

/**
 * Fantasy: your lists of players from any team, scored live all night. Tonight's panel says who's
 * playing and when; each player shows tonight's line and points, a flame for a point streak, OUT
 * if they're injured or scratched, and (tap) who they skated with. At the end of the night the
 * Fantasy Report plays.
 */
@Composable
fun FantasyTab(vm: BitsportsViewModel) {
    val lists = vm.fantasyLists
    var selected by remember { mutableStateOf(lists.firstOrNull()?.id) }
    val list = lists.firstOrNull { it.id == selected } ?: lists.firstOrNull()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Heading("FANTASY", size = 18.sp)
        Spacer(Modifier.height(16.dp))
        if (lists.isEmpty()) {
            CardBox {
                Column(Modifier.padding(16.dp)) {
                    Text("Make a list of players from any team: your pool, the family pool, a waiver watch.", color = Muted, fontSize = 14.sp)
                    Spacer(Modifier.height(12.dp))
                    NewList(vm) { selected = it }
                }
            }
            return@Column
        }
        // Your lists.
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lists.forEach { l -> Chip(l.name, l.id == list?.id) { selected = l.id } }
            var adding by remember { mutableStateOf(false) }
            Chip("+ NEW", false) { adding = !adding }
            if (adding) Dialog("New list", onDismiss = { adding = false }) { NewList(vm) { selected = it; adding = false } }
        }
        Spacer(Modifier.height(14.dp))
        if (list != null) ListView(vm, list)
        Spacer(Modifier.height(32.dp))
        SectionHead("SCORING", vm.fantasyScoring.name)
        ScoringEditor(vm)
        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun ListView(vm: BitsportsViewModel, list: FantasyList) {
    val night = remember(list, vm.games, vm.fantasyLines, vm.fantasyScoring) { FantasyNight.night(list, vm.games, vm.fantasyLines, vm.fantasyScoring) }
    val accent = Color(LocalUi.current.accent)
    // Tonight.
    CardBox {
        Column(Modifier.padding(16.dp)) {
            val playing = night.rows.filter { it.playing }
            val live = playing.any { it.game!!.state.isLive }
            val first = playing.filter { !it.game!!.state.isLive && !it.game.state.isOver }.minByOrNull { it.game!!.startUtc }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label(if (live) "TONIGHT · LIVE" else "TONIGHT", color = if (live) Live else TextInk)
                Spacer(Modifier.weight(1f))
                Text(Scoring.fmt(night.total), color = TextInk, fontFamily = Pixel, fontSize = 26.sp)
                Spacer(Modifier.width(4.dp))
                Label("PTS")
            }
            Spacer(Modifier.height(6.dp))
            Text(
                listOfNotNull(
                    "${playing.size} of ${night.rows.size} playing",
                    first?.let { "first puck drop ${ScoreFormat.status(it.game!!)}" },
                    night.mvp?.let { "MVP so far: ${it.player.lastName}" },
                ).joinToString(" · "),
                color = Muted, fontSize = 13.sp,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    if (night.rows.isNotEmpty()) CardBox {
        night.rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Line)
            PlayerRow(vm, list, r)
        }
    }
    Spacer(Modifier.height(10.dp))
    AddPlayer(vm, list)
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (night.rows.any { it.line != null }) {
            OutlinedButton(onClick = { vm.showFantasyReport() }, border = BorderStroke(1.dp, accent), shape = RoundedCornerShape(11.dp),
                modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("▶ Report", color = TextInk, fontWeight = FontWeight.SemiBold) }
        }
        var renaming by remember(list.id) { mutableStateOf(false) }
        OutlinedButton(onClick = { renaming = true }, border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
            modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text("Rename", color = TextInk) }
        var confirm by remember(list.id) { mutableStateOf(false) }
        OutlinedButton(onClick = { if (confirm) vm.deleteList(list.id) else confirm = true }, border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
            modifier = Modifier.weight(1f).heightIn(min = 44.dp)) { Text(if (confirm) "Sure?" else "Delete", color = if (confirm) Live else TextInk) }
        if (renaming) Dialog("Rename list", onDismiss = { renaming = false }) {
            var name by remember { mutableStateOf(list.name) }
            Field(name, { name = it }, "List name")
            Spacer(Modifier.height(10.dp))
            Button(onClick = { vm.renameList(list.id, name); renaming = false }, shape = RoundedCornerShape(11.dp)) { Text("Save") }
        }
    }
}

@Composable
private fun PlayerRow(vm: BitsportsViewModel, list: FantasyList, r: FantasyNight.Row) {
    val p = r.player
    var open by remember(p.id) { mutableStateOf(false) }
    val streak = vm.fantasyStreaks[p.id] ?: 0
    val injury = remember(vm.fantasyInjuries, p.id) { Injuries.find(vm.fantasyInjuries, p) }
    val g = r.game
    Column(Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 4.dp, height = 30.dp).background(Color(TeamInk.of(p.team))))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    // A point streak burns, taller every game.
                    if (streak >= 3) { Spacer(Modifier.width(6.dp)); StreakFlame(streak); Text("$streak", color = Color(0xFFFFB020), fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    if (injury != null) {
                        Spacer(Modifier.width(6.dp))
                        Text("OUT", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Live).padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                }
                Text(
                    listOfNotNull("${p.team} · ${p.pos}${p.number?.let { " #$it" } ?: ""}", when {
                        g == null -> "no game tonight"
                        r.line != null -> FantasyNight.statText(r.line, p.isGoalie).ifEmpty { if (g.state.isLive) "on now" else null }
                        else -> (if (g.home.abbrev == p.team) "vs ${g.away.abbrev} " else "@ ${g.home.abbrev} ") + ScoreFormat.status(g)
                    }).joinToString(" · "),
                    color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text(if (r.line != null) Scoring.fmt(r.points) else "–", color = if (r.points > 0) TextInk else Dim, fontFamily = Pixel, fontSize = 18.sp)
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            injury?.let { Text("${it.status}${it.comment?.let { c -> " · $c" } ?: ""}", color = Warn, fontSize = 12.sp) }
            // Line-mates: who he's been on the ice with most tonight.
            var mates by remember(p.id) { mutableStateOf<List<Pair<String, Int>>?>(null) }
            LaunchedEffect(p.id, g?.id) { mates = vm.linemates(p) }
            mates?.takeIf { it.isNotEmpty() }?.let { m ->
                Text("Skating with: " + m.joinToString(", ") { (n, s) -> "$n (${s / 60}:${"%02d".format(s % 60)})" }, color = Muted, fontSize = 12.sp)
            } ?: if (g != null && (g.state.isLive || g.state.isOver)) Text("Line-mates show once the shift chart is in.", color = Dim, fontSize = 12.sp) else Unit
            if (streak > 0) Text("Point streak: $streak game${if (streak == 1) "" else "s"}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text("Remove from ${list.name}", color = Live, fontSize = 13.sp, modifier = Modifier.clickable { vm.removePlayer(list.id, p.id) }.padding(vertical = 4.dp))
        }
    }
}

/** A little flame next to a player on a point streak; taller the longer it runs. */
@Composable
private fun StreakFlame(n: Int) {
    val frame = goalFrame(loop = true, stepMs = 140)
    val grid = remember(n, frame) { LedGrid(7, 12).also { PixelArt.flame(it, 0, 11, (3 + n).coerceAtMost(11), frame, w = 6) } }
    Box(Modifier.size(width = 10.dp, height = 17.dp)) { PixelGrid(grid) }
}

@Composable
private fun AddPlayer(vm: BitsportsViewModel, list: FantasyList) {
    var q by remember(list.id) { mutableStateOf("") }
    var results by remember(list.id) { mutableStateOf<List<PlayerRef>>(emptyList()) }
    LaunchedEffect(q) { delay(300); results = vm.searchPlayers(q) }
    Field(q, { q = it }, "Add a player: search any NHL name")
    results.take(8).forEach { p ->
        val on = list.players.any { it.id == p.id }
        Row(
            Modifier.fillMaxWidth().clickable(enabled = !on) { vm.addPlayer(list.id, p); q = "" }.padding(vertical = 10.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(width = 4.dp, height = 22.dp).background(Color(TeamInk.of(p.team))))
            Spacer(Modifier.width(10.dp))
            Text(p.name, color = TextInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Text("${p.team} · ${p.pos}", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.width(10.dp))
            Text(if (on) "✓" else "+ ADD", color = if (on) Dim else TextInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun NewList(vm: BitsportsViewModel, onMade: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Field(name, { name = it }, "List name, like My pool")
    Spacer(Modifier.height(10.dp))
    Button(onClick = { onMade(vm.createList(name.ifBlank { "My pool" }).id) }, shape = RoundedCornerShape(11.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(LocalUi.current.accent), contentColor = Color.Black)) {
        Text("Make the list", fontWeight = FontWeight.SemiBold)
    }
}

/** The two ready-made scorings, or your own weights for every stat. */
@Composable
private fun ScoringEditor(vm: BitsportsViewModel) {
    val s = vm.fantasyScoring
    val presets = Scoring.PRESETS.map { it.name } + "Custom"
    Segmented(presets, presets.indexOf(s.name).takeIf { it >= 0 } ?: 2) { i ->
        vm.setScoring(if (i < Scoring.PRESETS.size) Scoring.PRESETS[i] else s.copy(name = "Custom"))
    }
    Spacer(Modifier.height(10.dp))
    CardBox {
        Column(Modifier.padding(12.dp)) {
            val fields: List<Triple<String, Double, (Double) -> Scoring>> = listOf(
                Triple("Goal", s.goal) { v -> s.copy(goal = v) }, Triple("Assist", s.assist) { v -> s.copy(assist = v) },
                Triple("+/-", s.plusMinus) { v -> s.copy(plusMinus = v) }, Triple("PIM", s.pim) { v -> s.copy(pim = v) },
                Triple("PP goal", s.ppGoal) { v -> s.copy(ppGoal = v) }, Triple("Shot", s.shot) { v -> s.copy(shot = v) },
                Triple("Hit", s.hit) { v -> s.copy(hit = v) }, Triple("Block", s.block) { v -> s.copy(block = v) },
                Triple("Goalie win", s.win) { v -> s.copy(win = v) }, Triple("Save", s.save) { v -> s.copy(save = v) },
                Triple("Goal against", s.goalAgainst) { v -> s.copy(goalAgainst = v) }, Triple("Shutout", s.shutout) { v -> s.copy(shutout = v) },
            )
            fields.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, v, set) ->
                        Row(Modifier.weight(1f).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            var text by remember(s.name, label) { mutableStateOf(Scoring.fmt(v)) }
                            OutlinedTextField(
                                value = text, singleLine = true, enabled = s.name == "Custom",
                                onValueChange = { t -> text = t; t.toDoubleOrNull()?.let { vm.setScoring(set(it).copy(name = "Custom")) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = TextInk),
                                modifier = Modifier.width(72.dp),
                            )
                        }
                    }
                }
            }
            Text("Pick Custom to change any number.", color = Dim, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (on) TextInk else Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Card3 else Card2)
            .border(1.dp, if (on) TextInk else Line, RoundedCornerShape(8.dp)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(hint, color = Dim) }, shape = RoundedCornerShape(11.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Card2, unfocusedContainerColor = Card2, focusedBorderColor = Line2, unfocusedBorderColor = Line,
            focusedTextColor = TextInk, unfocusedTextColor = TextInk,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Dialog(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Card,
        title = { Text(title, color = TextInk) },
        text = { Column(content = content) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Muted) } },
    )
}
