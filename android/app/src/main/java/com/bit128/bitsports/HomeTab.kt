package com.bit128.bitsports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.sports.EspnGame
import com.bit128.bitsports.sports.EspnState
import com.bit128.bitsports.ui.HockeyBoard
import com.bit128.bitsports.ui.HockeyLedBoard
import com.bit128.bitsports.ui.LedBoard
import com.bit128.bitsports.ui.LedGrid
import com.bit128.bitsports.ui.PixelFont

/** Home is only about your teams: their games up top, their news under it. */
@Composable
fun HomeTab(vm: BitsportsViewModel) {
    val ui = LocalUi.current
    val nhl = vm.favoriteNhlGames()
    val other = vm.favoriteEspnGames()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Jumbotron(vm, nhl, other)
        AlertsOffBanner(vm)
        Spacer(Modifier.height(28.dp))
        YourGames(vm, nhl, other)
        if (vm.followedAbbrevs().any { vm.standingFor(it) != null }) {
            Spacer(Modifier.height(36.dp))
            YourTeamsCards(vm)
        }
        if (ui.showNews) {
            Spacer(Modifier.height(36.dp))
            NewsSection(vm)
        }
        Spacer(Modifier.height(40.dp))
        Footer()
    }
}


/** The arena board: your lead game in LED dots. Live beats upcoming beats final. */
@Composable
private fun Jumbotron(vm: BitsportsViewModel, nhl: List<Game>, other: List<EspnGame>) {
    val accent = LocalUi.current.accent.toInt()
    val leadNhl = nhl.firstOrNull()
    val leadOther = other.firstOrNull()
    val useOther = leadOther != null && (leadNhl == null || (leadOther.isLive && !leadNhl.state.isLive))
    val onClick: (() -> Unit)?
    val content: @Composable () -> Unit
    when {
        vm.teams.isEmpty() -> {
            content = { LedBoard(messageBoard("PICK YOUR", "TEAMS", "TAP HERE", accent)) }
            onClick = { vm.tab = Tab.SETTINGS }
        }
        useOther && leadOther != null -> {
            val g = leadOther
            val has = g.state != EspnState.PRE
            fun ints(t: com.bit128.bitsports.sports.EspnTeam) = espnColors(t).let { it.first.toArgb() to it.second.toArgb() }
            val input = HockeyBoard.Input(
                away = g.away.abbrev, awayColors = ints(g.away), home = g.home.abbrev, homeColors = ints(g.home),
                awayScore = g.away.score?.toIntOrNull()?.takeIf { has }, homeScore = g.home.score?.toIntOrNull()?.takeIf { has },
                clock = espnWhen(g).uppercase(), clockColor = if (g.isLive) HockeyBoard.LIVE else HockeyBoard.MUTED,
                awaySub = g.away.record, homeSub = g.home.record, zone = HockeyBoard.Zone.None, progress = null, accent = accent,
            )
            content = { HockeyLedBoard(input) }
            onClick = { vm.sportsLeague = g.league.key; vm.tab = Tab.SPORTS }
        }
        leadNhl != null -> {
            val g = leadNhl
            val records = vm.standingFor(g.away.abbrev)?.record to vm.standingFor(g.home.abbrev)?.record
            val input = HockeyBoard.input(g, accent, vm.now, records)
            content = { HockeyLedBoard(input) }
            onClick = { vm.openGame(g.id) }
        }
        !vm.gamesLoaded -> {
            content = { LedBoard(messageBoard("LOADING", "", "", accent)) }
            onClick = null
        }
        else -> {
            val next = vm.nextGames.values.minByOrNull { it.startUtc }
            val board = if (next != null) {
                messageBoard("NEXT UP", "${next.away.abbrev} @ ${next.home.abbrev}", whenText(next.startUtc), accent)
            } else {
                messageBoard("NO GAMES", "TODAY", "", accent)
            }
            content = { LedBoard(board) }
            onClick = { vm.tab = Tab.SPORTS }
        }
    }
    CardBox(if (onClick != null) Modifier.clip(CardShape).clickable(onClick = onClick) else Modifier) {
        Box(Modifier.padding(10.dp)) { content() }
    }
}

/** When alerts can't reach you, say so where you'll see it, with the one-tap fix. */
@Composable
private fun AlertsOffBanner(vm: BitsportsViewModel) {
    if (vm.followedAbbrevs().isEmpty()) return
    val ctx = LocalContext.current
    var problem by remember { mutableStateOf(AlertScheduler.problem(ctx)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) problem = AlertScheduler.problem(ctx)
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }
    if (problem == null) return
    Spacer(Modifier.height(16.dp))
    CardBox {
        Column(Modifier.padding(16.dp)) {
            Label("GAME ALERTS ARE BLOCKED", color = Warn)
            Spacer(Modifier.height(8.dp))
            Text(problem!!, color = TextInk, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { vm.tab = Tab.SETTINGS },
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(LocalUi.current.accent), contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(44.dp),
            ) { Text("Fix it in Alert check", fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** Words on the jumbotron, with a puck and two crossed sticks so it still reads as a rink board. */
internal fun messageBoard(top: String, mid: String, bottom: String, accent: Int): LedGrid {
    val g = LedGrid(HockeyBoard.COLS, HockeyBoard.ROWS)
    g.textCenter(top, 4, accent)
    if (mid.isNotEmpty()) {
        // Double size only if it clears the sticks in the corners (they reach 18 columns in).
        val scale = if (PixelFont.width(mid, 2) <= HockeyBoard.COLS - 2 * 20) 2 else 1
        g.textCenter(mid, if (scale == 2) 14 else 17, HockeyBoard.WHITE, scale)
    }
    if (bottom.isNotEmpty()) g.textCenter(bottom.uppercase().take(20), 32, HockeyBoard.MUTED)
    // Crossed sticks in the corners: a shaft of diagonal pixels and a 3-pixel blade.
    g.shape()
    for (i in 0 until 10) {
        g.put(4 + i, 4 + i, HockeyBoard.DIM); g.put(123 - i, 4 + i, HockeyBoard.DIM)
    }
    g.fill(13, 14, 4, 1, HockeyBoard.MUTED); g.fill(111, 14, 4, 1, HockeyBoard.MUTED)
    g.sprite(com.bit128.bitsports.ui.Sprites.PUCK, 62, 40, mapOf('k' to HockeyBoard.MUTED))
    return g
}

@Composable
private fun YourGames(vm: BitsportsViewModel, nhl: List<Game>, other: List<EspnGame>) {
    val note = vm.nhlNote()
    SectionHead("YOUR GAMES", note.text.ifEmpty { "Tap a game for the full story" }, noteColor = if (note.warn) Warn else Muted)
    when {
        vm.teams.isEmpty() -> CardBox {
            InfoLine("Pick the teams you care about and this page becomes just them: their games, their news.")
            Button(
                onClick = { vm.tab = Tab.SETTINGS },
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(LocalUi.current.accent), contentColor = Color.Black),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp).fillMaxWidth().height(48.dp),
            ) { Heading("CHOOSE TEAMS", color = Color.Black) }
        }
        !vm.gamesLoaded && other.isEmpty() -> CardBox { InfoLine("Loading your games…") }
        else -> {
            val followed = vm.followedAbbrevs()
            nhl.forEachIndexed { i, g ->
                GameCard(g, hero = i == 0, followed = followed, onClick = { vm.openGame(g.id) })
                Spacer(Modifier.height(10.dp))
            }
            other.forEach { g ->
                EspnCard(g) { t -> vm.teams.any { t.matches(it.team) } }
                Spacer(Modifier.height(10.dp))
            }
            if (nhl.isEmpty() && other.isEmpty()) {
                CardBox {
                    if (vm.gamesError != null && vm.games.isEmpty()) {
                        InfoLine("Your games show here once the NHL feed answers.", color = Warn)
                        RetryButton { vm.refreshGames() }
                    } else {
                        InfoLine("None of your teams play today.")
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            NextUp(vm)
            AllGamesLink(vm)
        }
    }
}

/** Followed NHL teams with no game today: when they play next. */
@Composable
private fun NextUp(vm: BitsportsViewModel) {
    val next = vm.nextGames.entries.sortedBy { it.value.startUtc }
    if (next.isEmpty()) return
    CardBox {
        Column(Modifier.padding(vertical = 6.dp)) {
            Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)) { Label("NEXT UP") }
            next.forEachIndexed { i, (abbrev, g) ->
                if (i > 0) HorizontalDivider(color = Line, modifier = Modifier.padding(horizontal = 16.dp))
                val home = g.home.abbrev == abbrev
                val opp = if (home) g.away.abbrev else g.home.abbrev
                Row(
                    Modifier.fillMaxWidth().clickable { vm.openGame(g.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Crest(teamName(abbrev), 22.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${com.bit128.bitsports.alerts.AlertCopy.nick(abbrev)} ${if (home) "vs" else "at"} ${com.bit128.bitsports.alerts.AlertCopy.nick(opp)}",
                        color = TextInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    )
                    Label(whenText(g.startUtc).uppercase())
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun AllGamesLink(vm: BitsportsViewModel) {
    val live = vm.games.count { it.state.isLive } + vm.espnGames.values.flatten().count { it.isLive }
    OutlinedButton(
        onClick = { vm.tab = Tab.SPORTS },
        border = BorderStroke(1.dp, Line2), shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth().height(48.dp),
    ) {
        Text(
            if (live > 0) "All games · $live live now ›" else "All of today's games ›",
            color = TextInk, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        )
    }
}
