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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.sports.EspnGame
import com.bit128.bitsports.sports.EspnState
import com.bit128.bitsports.ui.Led
import com.bit128.bitsports.ui.LedBoard
import com.bit128.bitsports.ui.PixelFont
import com.bit128.bitsports.ui.ScoreFormat
import com.bit128.bitsports.ui.centerX
import com.bit128.bitsports.ui.ledColor

/** Home is only about your teams: their games up top, their news under it. */
@Composable
fun HomeTab(vm: BitsportsViewModel) {
    val ui = LocalUi.current
    val nhl = vm.favoriteNhlGames()
    val other = vm.favoriteEspnGames()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Jumbotron(vm, nhl, other)
        Spacer(Modifier.height(28.dp))
        YourGames(vm, nhl, other)
        if (ui.showNews) {
            Spacer(Modifier.height(36.dp))
            NewsSection(vm)
        }
        Spacer(Modifier.height(40.dp))
        Footer()
    }
}

internal const val BOARD_COLS = 80
internal const val BOARD_ROWS = 28

/** The arena board: your lead game in LED dots. Live beats upcoming beats final. */
@Composable
private fun Jumbotron(vm: BitsportsViewModel, nhl: List<Game>, other: List<EspnGame>) {
    val accent = Color(LocalUi.current.accent)
    val leadNhl = nhl.firstOrNull()
    val leadOther = other.firstOrNull()
    val useOther = leadOther != null && (leadNhl == null || (leadOther.isLive && !leadNhl.state.isLive))
    val board: List<Led>
    val onClick: (() -> Unit)?
    when {
        vm.teams.isEmpty() -> {
            board = message("PICK YOUR", "TEAMS", "TAP HERE", accent)
            onClick = { vm.tab = Tab.SETTINGS }
        }
        useOther && leadOther != null -> {
            board = scoreBoard(
                leadOther.away.abbrev, espnColors(leadOther.away), leadOther.home.abbrev, espnColors(leadOther.home),
                leadOther.away.score.takeIf { leadOther.state != EspnState.PRE },
                leadOther.home.score.takeIf { leadOther.state != EspnState.PRE },
                espnWhen(leadOther), leadOther.isLive,
            )
            onClick = { vm.sportsLeague = leadOther.league.key; vm.tab = Tab.SPORTS }
        }
        leadNhl != null -> {
            val g = leadNhl
            val has = g.state.isLive || g.state.isOver
            board = scoreBoard(
                g.away.abbrev, teamColors(teamName(g.away.abbrev)), g.home.abbrev, teamColors(teamName(g.home.abbrev)),
                g.away.score?.toString()?.takeIf { has }, g.home.score?.toString()?.takeIf { has },
                ScoreFormat.status(g).replace("LIVE · ", ""), g.state.isLive,
            )
            onClick = { vm.openGame(g.id) }
        }
        !vm.gamesLoaded -> {
            board = message("LOADING", "", "", accent)
            onClick = null
        }
        else -> {
            val next = vm.nextGames.values.minByOrNull { it.startUtc }
            board = if (next != null) {
                message("NEXT UP", "${next.away.abbrev} @ ${next.home.abbrev}", whenText(next.startUtc), accent)
            } else {
                message("NO GAMES", "TODAY", "", accent)
            }
            onClick = { vm.tab = Tab.SPORTS }
        }
    }
    CardBox(if (onClick != null) Modifier.clip(CardShape).clickable(onClick = onClick) else Modifier) {
        Box(Modifier.padding(10.dp)) { LedBoard(BOARD_COLS, BOARD_ROWS, board) }
    }
}

private fun message(top: String, mid: String, bottom: String, accent: Color): List<Led> = listOfNotNull(
    Led.Text(top, centerX(top, BOARD_COLS), 3, accent),
    mid.takeIf { it.isNotEmpty() }?.let { m ->
        // Double size only when it fits across and there's no bottom line to collide with.
        val scale = if (bottom.isEmpty() && PixelFont.width(m, 2) <= BOARD_COLS - 4) 2 else 1
        Led.Text(m, centerX(m, BOARD_COLS, scale), 11, Color.White, scale)
    },
    bottom.takeIf { it.isNotEmpty() }?.let { Led.Text(it.uppercase().take(13), centerX(it.take(13), BOARD_COLS), 20, Muted) },
)

internal fun scoreBoard(
    away: String, awayColors: Pair<Color, Color>, home: String, homeColors: Pair<Color, Color>,
    awayScore: String?, homeScore: String?, status: String, live: Boolean,
): List<Led> {
    val items = ArrayList<Led>()
    val a = away.take(4)
    val h = home.take(4)
    items += Led.Text(a, 3, 2, ledColor(awayColors.first, awayColors.second))
    items += Led.Text(h, BOARD_COLS - 3 - PixelFont.width(h), 2, ledColor(homeColors.first, homeColors.second))
    // Team stripes under each name, both colours.
    items += Led.Fill(3, 11, PixelFont.width(a), 1, awayColors.first)
    items += Led.Fill(3, 12, PixelFont.width(a), 1, awayColors.second)
    items += Led.Fill(BOARD_COLS - 3 - PixelFont.width(h), 11, PixelFont.width(h), 1, homeColors.first)
    items += Led.Fill(BOARD_COLS - 3 - PixelFont.width(h), 12, PixelFont.width(h), 1, homeColors.second)
    val mid = if (awayScore != null && homeScore != null) "$awayScore-$homeScore" else "VS"
    // Big digits when they fit between the names, regular ones when they don't (football scores).
    val room = BOARD_COLS - 2 * (3 + maxOf(PixelFont.width(a), PixelFont.width(h)) + 2)
    val scale = if (PixelFont.width(mid, 2) <= room) 2 else 1
    items += Led.Text(mid, centerX(mid, BOARD_COLS, scale), if (scale == 2) 2 else 5, Color.White, scale)
    val s = status.uppercase().take(13)
    items += Led.Text(s, centerX(s, BOARD_COLS), 19, if (live) Live else Muted)
    if (live) items += Led.Fill(BOARD_COLS / 2 - 1, 26, 2, 1, Live)
    return items
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
