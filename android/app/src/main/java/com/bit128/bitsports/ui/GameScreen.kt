package com.bit128.bitsports.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.*
import com.bit128.bitsports.ai.AiResult
import com.bit128.bitsports.ai.FamilyAi
import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.alerts.GameWatchService
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GameExtras
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.nhl.OnIce
import com.bit128.bitsports.nhl.PlayText
import com.bit128.bitsports.nhl.Skater
import com.bit128.bitsports.nhl.StatPair
import com.bit128.bitsports.nhl.TeamExtras
import kotlinx.coroutines.launch

private enum class GameTab(val label: String) { GAME("Game"), LINEUPS("Lineups"), PLAYS("Plays"), STATS("Stats") }

/** One game, in depth: the board, then Game / Lineups / Plays / Stats. Refreshes itself while live. */
@Composable
fun GameScreen(vm: BitsportsViewModel, onBack: () -> Unit) {
    val id = vm.openGameId ?: return
    val d = vm.detail
    val x = vm.extras
    val game = d?.game ?: vm.games.firstOrNull { it.id == id } ?: vm.nextGames.values.firstOrNull { it.id == id }
    var tab by rememberSaveable(id) { mutableStateOf(GameTab.GAME) }
    Column(Modifier.fillMaxSize()) {
        Column {
            Row(
                Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center,
                ) { Label("‹ BACK", color = TextInk) }
                Spacer(Modifier.weight(1f))
                val stale = d?.fetchedAt?.let { AlertPlanner.staleLabel(it, vm.now) }
                val note = when {
                    vm.detailError != null && d != null -> "${stale ?: "Just updated"} · reconnecting"
                    vm.detailError != null -> "Can't reach the NHL feed · retrying"
                    game?.state?.isLive == true -> stale ?: "Live · updates every 15s"
                    else -> stale ?: ""
                }
                Text(note, color = if (vm.detailError != null && d == null) Warn else Muted, fontSize = 12.sp,
                    modifier = Modifier.padding(end = 8.dp))
            }
            HorizontalDivider(color = Line)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(20.dp))
            if (game == null) {
                Text("Loading game…", color = Muted, fontSize = 14.sp)
                return@Column
            }
            Board(game, x)
            Spacer(Modifier.height(12.dp))
            WatchButton(game, vm.followedAbbrevs())
            Spacer(Modifier.height(16.dp))
            Segmented(GameTab.entries.map { it.label }, tab.ordinal) { tab = GameTab.entries[it] }
            Spacer(Modifier.height(24.dp))
            when (tab) {
                GameTab.GAME -> GameTabContent(game, d, x)
                GameTab.LINEUPS -> LineupsTab(game, x)
                GameTab.PLAYS -> PlaysTab(game, d)
                GameTab.STATS -> StatsTab(vm, game, d, x)
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

private fun colorsOf(abbrev: String) = teamColors(teamName(abbrev))

/** The LED board, then each side's jersey, name and record. */
@Composable
private fun Board(g: Game, x: GameExtras?) {
    val hasScore = g.state.isLive || g.state.isOver
    CardBox {
        Column(Modifier.padding(10.dp)) {
            HockeyLedBoard(HockeyBoard.input(g, LocalUi.current.accent.toInt(), records = x?.away?.record to x?.home?.record))
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBlock(g.away.abbrev, x?.away, Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (g.state.isLive) LiveMarker() else Label(if (hasScore) "SOG" else "AT")
                    if (hasScore) {
                        Spacer(Modifier.height(4.dp))
                        Text("${g.away.sog ?: 0}–${g.home.sog ?: 0}", color = Muted, fontFamily = Pixel, fontSize = 13.sp)
                    }
                }
                TeamBlock(g.home.abbrev, x?.home, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TeamBlock(abbrev: String, t: TeamExtras?, modifier: Modifier) {
    val (c1, c2) = colorsOf(abbrev)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PixelJersey(c1, c2, 40.dp)
        Spacer(Modifier.height(8.dp))
        Text(AlertCopy.nick(abbrev), color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        t?.record?.let { Text(it, color = Muted, fontSize = 12.sp) }
    }
}

/** Starts the background watcher (mini scoreboard + alerts) for a game that isn't over. */
@Composable
private fun WatchButton(g: Game, followed: Set<String>) {
    if (g.state.isOver || !g.scheduleOk) return
    val ctx = LocalContext.current
    var started by remember(g.id) { mutableStateOf(false) }
    val yours = g.away.abbrev in followed || g.home.abbrev in followed
    OutlinedButton(
        onClick = { started = GameWatchService.start(ctx, g.id) },
        enabled = !started,
        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
        Text(
            when {
                started -> "Watching · mini scoreboard is in your notifications"
                yours -> "Watch now · auto-starts 15 min before puck drop"
                else -> "Watch this game with alerts"
            },
            color = if (started) Muted else TextInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )
    }
}

// ---------- Game tab ----------

@Composable
private fun GameTabContent(g: Game, d: GameDetail?, x: GameExtras?) {
    val started = d != null && d.plays.isNotEmpty()
    if (g.state.isLive && x != null) {
        OnIceSection(g, x)
        Spacer(Modifier.height(32.dp))
    }
    if (!started) {
        Matchup(g, x)
        return
    }
    val away = NhlTeams.byAbbrev(g.away.abbrev)
    val home = NhlTeams.byAbbrev(g.home.abbrev)
    Linescore(g, d!!)
    Spacer(Modifier.height(32.dp))
    ShotMapSection(g, d, away, home)

    val goals = GameAnalysis.goals(d)
    Spacer(Modifier.height(32.dp))
    SectionHead("GOALS", if (goals.isEmpty()) "None yet" else "${goals.size}")
    if (goals.isNotEmpty()) CardBox {
        goals.forEachIndexed { i, goal ->
            if (i > 0) HorizontalDivider(color = Line)
            GoalRow(goal)
        }
    }

    if (d.threeStars.isNotEmpty()) {
        Spacer(Modifier.height(32.dp))
        SectionHead("THREE STARS", "")
        CardBox {
            d.threeStars.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = Line)
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Label("★".repeat(i + 1).padEnd(3, ' '), color = Warn)
                    Spacer(Modifier.width(12.dp))
                    Text(s, color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
    x?.let { GameInfo(g, it) }
}

/** Who's out there right now, drawn on the rink, from the NHL's shift chart. */
@Composable
private fun OnIceSection(g: Game, x: GameExtras) {
    val snap = OnIce.snapshot(g, x.shifts)
    val clock = g.clock
    SectionHead(
        "ON THE ICE",
        when {
            clock?.inIntermission == true -> "Intermission"
            snap == null -> "Waiting on the shift chart"
            else -> "${AlertPlanner.periodLabel(snap.period, clock?.periodType ?: "REG")} ${snap.clock}" +
                if (snap.lagging) " · line changes running behind" else ""
        },
    )
    if (snap == null) {
        CardBox {
            InfoLine(
                if (clock?.inIntermission == true) "Lines come back out after the break."
                else "The NHL posts line changes a little behind live. They'll show here as soon as they do."
            )
        }
        return
    }
    val people = (x.away.skaters + x.home.skaters).associateBy { it.id }
    val goalieIds = (x.away.goalies + x.home.goalies).associateBy { it.id }
    fun rink(list: List<com.bit128.bitsports.nhl.Shift>, abbrev: String) = list.map { s ->
        val sk = people[s.playerId]
        val gl = goalieIds[s.playerId]
        RinkPlayer(sk?.number ?: gl?.number, if (gl != null) "G" else sk?.pos ?: "F", colorsOf(abbrev))
    }
    CardBox {
        Column(Modifier.padding(10.dp)) {
            OnIceRink(rink(snap.away, g.away.abbrev), rink(snap.home, g.home.abbrev))
            Spacer(Modifier.height(10.dp))
            for ((abbrev, list) in listOf(g.away.abbrev to snap.away, g.home.abbrev to snap.home)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Crest(colorsOf(abbrev), 14.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        list.sortedBy { if (goalieIds.containsKey(it.playerId)) 1 else 0 }.joinToString("  ·  ") { s ->
                            val n = people[s.playerId]?.number ?: goalieIds[s.playerId]?.number
                            (n?.let { "#$it " } ?: "") + s.name.substringAfterLast(' ')
                        },
                        color = TextInk, fontSize = 13.sp, lineHeight = 18.sp,
                    )
                }
            }
        }
    }
}

/** Before puck drop: the tale of the tape. */
@Composable
private fun Matchup(g: Game, x: GameExtras?) {
    if (x == null) {
        Text("Loading the matchup…", color = Muted, fontSize = 14.sp)
        return
    }
    val ac = ledColor(colorsOf(g.away.abbrev).first, colorsOf(g.away.abbrev).second)
    val hc = ledColor(colorsOf(g.home.abbrev).first, colorsOf(g.home.abbrev).second)
    SectionHead("TALE OF THE TAPE", "${g.away.abbrev} · ${g.home.abbrev}")
    CardBox {
        Column(Modifier.padding(16.dp)) {
            for ((abbrev, t, c) in listOf(Triple(g.away.abbrev, x.away, ac), Triple(g.home.abbrev, x.home, hc))) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(AlertCopy.nick(abbrev), color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(96.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ResultStrip(t.recent, c)
                    Spacer(Modifier.weight(1f))
                    Text(listOfNotNull(t.last10, t.streak).joinToString(" · "), color = Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(10.dp))
            }
            Text("Last 10 games, oldest first · full block = win, half = OT loss", color = Dim, fontSize = 11.sp)
            if (x.seriesAwayWins != null && x.seriesHomeWins != null && (x.seriesAwayWins + x.seriesHomeWins) > 0) {
                Spacer(Modifier.height(8.dp))
                Text("Season series: ${g.away.abbrev} ${x.seriesAwayWins} – ${x.seriesHomeWins} ${g.home.abbrev}", color = TextInk, fontSize = 13.sp)
            }
        }
    }
    if (x.seasonStats.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionHead("THIS SEASON", "")
        StatBars(g, x.seasonStats)
    }
    if (x.leaders.isNotEmpty()) {
        Spacer(Modifier.height(24.dp))
        SectionHead("PLAYERS TO WATCH", "Season")
        CardBox {
            x.leaders.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.awayName, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(l.awayValue, color = Muted, fontFamily = Pixel, fontSize = 14.sp)
                    }
                    Label(l.label.uppercase())
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text(l.homeName, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(l.homeValue, color = Muted, fontFamily = Pixel, fontSize = 14.sp)
                    }
                }
            }
        }
    }
    val goalies = x.away.goalieSeason.firstOrNull() to x.home.goalieSeason.firstOrNull()
    if (goalies.first != null || goalies.second != null) {
        Spacer(Modifier.height(24.dp))
        SectionHead("IN NET", "Likely starters by games played")
        CardBox {
            Row(Modifier.padding(16.dp)) {
                for ((i, gs) in listOf(goalies.first, goalies.second).withIndex()) {
                    Column(Modifier.weight(1f), horizontalAlignment = if (i == 0) Alignment.Start else Alignment.End) {
                        if (gs == null) {
                            Text("–", color = Muted)
                        } else {
                            Text("${gs.number?.let { "#$it " }.orEmpty()}${gs.name}", color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(gs.record, color = Muted, fontSize = 12.sp)
                            Text("%.2f GAA · .%03d".format(gs.gaa, (gs.svPct * 1000).toInt()), color = Muted, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    GameInfo(g, x)
}

@Composable
private fun GameInfo(g: Game, x: GameExtras) {
    val lines = listOfNotNull(
        x.venue?.let { "Arena" to it },
        x.tv.takeIf { it.isNotEmpty() }?.let { "TV" to it.joinToString(", ") },
        x.away.coach?.let { "${g.away.abbrev} coach" to it },
        x.home.coach?.let { "${g.home.abbrev} coach" to it },
        x.officials.takeIf { it.isNotEmpty() }?.let { "Officials" to it.joinToString(", ") },
    )
    if (lines.isEmpty()) return
    Spacer(Modifier.height(24.dp))
    SectionHead("GAME INFO", "")
    CardBox {
        Column(Modifier.padding(16.dp)) {
            lines.forEach { (k, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text(k, color = Muted, fontSize = 13.sp, modifier = Modifier.width(110.dp))
                    Text(v, color = TextInk, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Linescore(g: Game, d: GameDetail) {
    val periods = d.plays.filter { it.periodType != "SO" }.map { it.period to it.periodType }.distinct().sortedBy { it.first }
    val goals = GameAnalysis.goals(d)
    SectionHead("BY PERIOD", "")
    CardBox {
        Column(Modifier.padding(16.dp)) {
            Row {
                Spacer(Modifier.width(56.dp))
                periods.forEach { (p, t) -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { Label(AlertPlanner.periodLabel(p, t).uppercase()) } }
                Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) { Label("T", color = TextInk) }
            }
            for (abbrev in listOf(g.away.abbrev, g.home.abbrev)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.width(56.dp), verticalAlignment = Alignment.CenterVertically) {
                        Crest(colorsOf(abbrev), 14.dp)
                        Spacer(Modifier.width(6.dp))
                        Label(abbrev, color = TextInk)
                    }
                    periods.forEach { (p, _) ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text("${goals.count { it.period == p && it.teamAbbrev == abbrev }}", color = TextInk, fontFamily = Pixel, fontSize = 16.sp)
                        }
                    }
                    Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                        val total = if (abbrev == g.away.abbrev) g.away.score else g.home.score
                        Text("${total ?: 0}", color = TextInk, fontFamily = Pixel, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

/** The shot map, filterable by team and period. */
@Composable
private fun ShotMapSection(g: Game, d: GameDetail, away: com.bit128.bitsports.nhl.NhlTeam?, home: com.bit128.bitsports.nhl.NhlTeam?) {
    var who by remember(g.id) { mutableStateOf(0) }
    var period by remember(g.id) { mutableStateOf(0) }
    var heat by rememberSaveable(g.id) { mutableStateOf(true) }
    val all = GameAnalysis.shots(d)
    val periods = all.map { it.period }.distinct().sorted()
    val shown = all
        .filter { who == 0 || it.teamAbbrev == (if (who == 1) g.away.abbrev else g.home.abbrev) }
        .filter { period == 0 || it.period == period }
    SectionHead("SHOT MAP", "${shown.count { it.isGoal }} goals · ${shown.size} shots on goal")
    CardBox {
        Column(Modifier.padding(10.dp)) {
            if (heat) {
                val grid = remember(shown) {
                    HeatMap.draw(HeatMap.heat(shown, g.away.abbrev), HockeyBoard.colors(g.away.abbrev), HockeyBoard.colors(g.home.abbrev))
                }
                PixelGrid(grid)
            } else {
                ShotMap(shown, away, home)
            }
            Spacer(Modifier.height(10.dp))
            Segmented(listOf("Heat", "Shots"), if (heat) 0 else 1) { heat = it == 0 }
            Spacer(Modifier.height(6.dp))
            Segmented(listOf("Both", g.away.abbrev, g.home.abbrev), who) { who = it }
            if (periods.size > 1) {
                Spacer(Modifier.height(6.dp))
                Segmented(listOf("All") + periods.map { if (it > 3) "OT" else "P$it" }, if (period == 0) 0 else periods.indexOf(period) + 1) {
                    period = if (it == 0) 0 else periods[it - 1]
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                if (heat) "← ${g.away.abbrev} attacks left · ${g.home.abbrev} attacks right → · brighter = more shots · white sparks = goals"
                else "${g.away.abbrev} shoots right → · ← ${g.home.abbrev} shoots left · white = goal",
                color = Dim, fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun GoalRow(g: GoalEvent) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.Top) {
        Crest(teamName(g.teamAbbrev), 24.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${g.scorer}${g.scorerTotal?.let { " ($it)" }.orEmpty()}",
                color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(AlertCopy.goalDetail(g).lines().drop(1).joinToString(" · "), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Label("${AlertPlanner.periodLabel(g.period, g.periodType).uppercase()} ${g.timeInPeriod} · ${g.awayScore}–${g.homeScore}")
        }
        g.clipUrl?.let { url ->
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).background(Card2)
                    .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) { Label("▶ CLIP", color = TextInk) }
        }
    }
}

// ---------- Lineups tab ----------

@Composable
private fun LineupsTab(g: Game, x: GameExtras?) {
    var side by rememberSaveable(g.id) { mutableStateOf(0) }
    Segmented(listOf(AlertCopy.nick(g.away.abbrev), AlertCopy.nick(g.home.abbrev)), side) { side = it }
    Spacer(Modifier.height(20.dp))
    if (x == null) {
        Text("Loading lineups…", color = Muted, fontSize = 14.sp)
        return
    }
    val abbrev = if (side == 0) g.away.abbrev else g.home.abbrev
    val t = if (side == 0) x.away else x.home
    val onIce = OnIce.snapshot(g, x.shifts)?.let { (it.away + it.home).map { s -> s.playerId }.toSet() }.orEmpty()
    val colors = colorsOf(abbrev)
    if (t.skaters.isEmpty()) {
        SectionHead("SEASON LEADERS", "Tonight's lineup posts near puck drop")
        if (t.seasonSkaters.isEmpty()) {
            CardBox { InfoLine("Nothing posted yet for this team.") }
        } else CardBox {
            t.seasonSkaters.take(14).forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = Line)
                PlayerRow(s, colors, false, season = true)
            }
        }
        if (t.goalieSeason.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            SectionHead("GOALIES", "Season")
            CardBox {
                t.goalieSeason.forEachIndexed { i, gs ->
                    if (i > 0) HorizontalDivider(color = Line)
                    Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Number(gs.number, colors)
                        Spacer(Modifier.width(12.dp))
                        Text(gs.name, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("${gs.record} · %.2f".format(gs.gaa), color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    } else {
        val forwards = t.skaters.filter { it.pos != "D" }
        val defense = t.skaters.filter { it.pos == "D" }
        val live = g.state.isLive
        SectionHead("FORWARDS", if (live) "Dot = on the ice now · tap a player" else "Tap a player for more")
        CardBox {
            forwards.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = Line)
                PlayerRow(s, colors, s.id in onIce)
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionHead("DEFENSE", "")
        CardBox {
            defense.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = Line)
                PlayerRow(s, colors, s.id in onIce)
            }
        }
        Spacer(Modifier.height(24.dp))
        SectionHead("GOALIES", "")
        CardBox {
            t.goalies.forEachIndexed { i, gl ->
                if (i > 0) HorizontalDivider(color = Line)
                Row(Modifier.fillMaxWidth().padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Number(gl.number, colors)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(gl.name, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        if (gl.starter) Label("STARTER", color = Color(LocalUi.current.accent))
                    }
                    if (gl.id in onIce) { OnIceDot(); Spacer(Modifier.width(8.dp)) }
                    Text(
                        if (gl.shotsAgainst > 0) "${gl.saves}/${gl.shotsAgainst} saves · ${gl.toi}" else gl.toi.takeIf { it != "00:00" } ?: "–",
                        color = Muted, fontSize = 12.sp,
                    )
                }
            }
        }
    }
    if (t.scratches.isNotEmpty() || t.coach != null) {
        Spacer(Modifier.height(24.dp))
        CardBox {
            Column(Modifier.padding(16.dp)) {
                t.coach?.let { Text("Coach · $it", color = TextInk, fontSize = 13.sp) }
                if (t.scratches.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text("Scratched · ${t.scratches.joinToString(", ")}", color = Muted, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Number(n: Int?, colors: Pair<Color, Color>) {
    Box(
        Modifier.size(width = 34.dp, height = 26.dp).clip(RoundedCornerShape(4.dp)).background(colors.first),
        contentAlignment = Alignment.Center,
    ) {
        Text(n?.toString() ?: "–", color = if (colors.first.luminanceIsLight()) Color.Black else Color.White, fontFamily = Pixel, fontSize = 13.sp)
    }
}

private fun Color.luminanceIsLight() = luminance() > 0.45f

@Composable
private fun OnIceDot() {
    Box(Modifier.size(8.dp).background(Live))
}

@Composable
private fun PlayerRow(s: Skater, colors: Pair<Color, Color>, onIce: Boolean, season: Boolean = false) {
    var open by remember(s.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = !open }.animateContentSize().padding(16.dp, 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Number(s.number, colors)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Label(posName(s.pos).uppercase())
            }
            if (onIce) { OnIceDot(); Spacer(Modifier.width(10.dp)) }
            Text(
                if (season) "${s.goals}G ${s.assists}A ${s.points}P" else "${s.goals}G ${s.assists}A · ${s.sog} SOG",
                color = if (s.points > 0) TextInk else Muted, fontSize = 12.sp, fontFamily = Pixel,
            )
        }
        if (open) {
            Spacer(Modifier.height(10.dp))
            val bits = listOfNotNull(
                s.gamesPlayed?.let { "Games $it" },
                "Points ${s.points}",
                "Shots ${s.sog}",
                "Hits ${s.hits}",
                "+/- ${if (s.plusMinus > 0) "+" else ""}${s.plusMinus}",
                s.toi?.let { if (season) "Avg ice time $it" else "Ice time $it" },
            )
            Text(bits.joinToString("  ·  "), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

private fun posName(p: String) = when (p) {
    "C" -> "Centre"
    "L" -> "Left wing"
    "R" -> "Right wing"
    "D" -> "Defense"
    "G" -> "Goalie"
    else -> p
}

// ---------- Plays tab ----------

@Composable
private fun PlaysTab(g: Game, d: GameDetail?) {
    if (d == null) {
        Text("Loading plays…", color = Muted, fontSize = 14.sp)
        return
    }
    val feed = PlayText.feed(d)
    if (feed.isEmpty()) {
        CardBox { InfoLine("Plays start at puck drop. Leave this open: it fills in live.") }
        return
    }
    SectionHead("PLAY BY PLAY", if (g.state.isLive) "Newest first · live" else "Newest first")
    CardBox {
        feed.forEachIndexed { i, line ->
            if (i > 0) HorizontalDivider(color = Line)
            val goal = line.play.type == "goal"
            Row(
                Modifier.fillMaxWidth().background(if (goal) Card2 else Color.Transparent).padding(16.dp, 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(52.dp)) {
                    Label(AlertPlanner.periodLabel(line.play.period, line.play.periodType).uppercase())
                    Text(line.play.timeInPeriod, color = Dim, fontSize = 11.sp)
                }
                if (line.teamAbbrev != null) Crest(colorsOf(line.teamAbbrev), 12.dp) else Spacer(Modifier.width(12.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    line.text, color = if (goal) TextInk else if (line.play.type in MINOR) Muted else TextInk,
                    fontSize = 13.sp, fontWeight = if (goal) FontWeight.Bold else FontWeight.Normal, lineHeight = 18.sp,
                )
            }
        }
    }
}

private val MINOR = setOf("faceoff", "stoppage", "giveaway", "takeaway", "blocked-shot", "missed-shot")

// ---------- Stats tab ----------

@Composable
private fun StatsTab(vm: BitsportsViewModel, g: Game, d: GameDetail?, x: GameExtras?) {
    val stats = x?.gameStats.orEmpty()
    if (stats.isNotEmpty()) {
        SectionHead("TEAM STATS", "${g.away.abbrev} · ${g.home.abbrev}")
        StatBars(g, stats)
    } else if (x?.seasonStats?.isNotEmpty() == true) {
        SectionHead("THIS SEASON", "Game stats start at puck drop")
        StatBars(g, x.seasonStats)
    } else {
        Text(if (x == null) "Loading stats…" else "Stats start at puck drop.", color = Muted, fontSize = 14.sp)
    }
    val periods = d?.let { finishedPeriods(it) }.orEmpty()
    if (d != null && periods.isNotEmpty()) {
        Spacer(Modifier.height(32.dp))
        SectionHead("PERIODS", "${g.away.abbrev} – ${g.home.abbrev}")
        periods.forEach { (p, type) ->
            PeriodCard(vm, d, p, type)
            Spacer(Modifier.height(10.dp))
        }
    }
}

/** Head-to-head stats as pixel bars in each team's colour. */
@Composable
private fun StatBars(g: Game, stats: List<StatPair>) {
    val a = colorsOf(g.away.abbrev).let { ledColor(it.first, it.second) }
    val h = colorsOf(g.home.abbrev).let { ledColor(it.first, it.second) }
    // Two similar team colours would merge into one bar; dim the home side so the split still shows.
    val home = if (a == h) h.copy(alpha = 0.4f) else h
    CardBox {
        Column(Modifier.padding(16.dp)) {
            stats.forEachIndexed { i, s ->
                if (i > 0) Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.away, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
                    Text(s.label, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Text(s.home, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
                }
                Spacer(Modifier.height(6.dp))
                PixelSplitBar(s.awayShare, a, home)
            }
        }
    }
}

/** Periods that are over (or in intermission), in order. Shootouts aren't periods here. */
internal fun finishedPeriods(d: GameDetail): List<Pair<Int, String>> {
    val c = d.game.clock
    return d.plays.filter { it.periodType != "SO" }.map { it.period to it.periodType }.distinct().sortedBy { it.first }
        .filter { (p, _) -> d.game.state.isOver || c == null || p < c.period || (p == c.period && c.inIntermission) }
}

@Composable
private fun PeriodCard(vm: BitsportsViewModel, d: GameDetail, period: Int, type: String) {
    val s = GameAnalysis.periodStats(d, period)
    val scope = rememberCoroutineScope()
    var recap by remember(d.game.id, period) { mutableStateOf(vm.recapFor(d.game.id, period)) }
    var note by remember(d.game.id, period) { mutableStateOf<String?>(null) }
    var busy by remember(d.game.id, period) { mutableStateOf(false) }
    CardBox {
        Column(Modifier.padding(16.dp)) {
            Heading(AlertPlanner.periodLabel(period, type).uppercase())
            Spacer(Modifier.height(10.dp))
            StatRow("Goals", "${s.awayGoals}", "${s.homeGoals}")
            StatRow("Shots", "${s.awayShots}", "${s.homeShots}")
            StatRow("Power play", s.awayPp, s.homePp)
            StatRow("Hits", "${s.awayHits}", "${s.homeHits}")
            StatRow("Faceoffs", "${s.awayFaceoffPct}%", "${s.homeFaceoffPct}%")
            Spacer(Modifier.height(10.dp))
            when {
                recap != null -> Text(recap!!, color = TextInk, fontSize = 14.sp, lineHeight = 20.sp)
                else -> {
                    note?.let { Text(it, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(8.dp)) }
                    OutlinedButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                when (val r = vm.writeRecap(period)) {
                                    is AiResult.Ok -> recap = r.text
                                    is AiResult.Unavailable -> note = FamilyAi.reasonText(r.reason)
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                    ) { Text(if (busy) "Writing recap…" else "AI recap", color = if (busy) Muted else TextInk, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, away: String, home: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(away, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(64.dp))
        Text(label, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Text(home, color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
    }
}
