package com.bit128.bitsports.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bit128.bitsports.*
import com.bit128.bitsports.nhl.Clock
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.OddsSample
import com.bit128.bitsports.nhl.Situation
import com.bit128.bitsports.nhl.TeamLine
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Every animation in bitsports, playable on demand: most only happen at a certain moment of a
 * real game (a hat trick, a pulled goalie, the last minute of a one-goal game), so this is where
 * you can see them any time. Pure entries, so a test also runs every frame of every one.
 */
object Gallery {
    data class Entry(val group: String, val name: String, val frames: Int, val build: (Int) -> LedGrid)

    fun entries(mine: String = "EDM", rival: String = "CGY"): List<Entry> {
        val t0 = Instant.parse("2026-11-08T03:00:00Z")
        fun game(state: GameState, a: Int?, h: Int?, clock: Clock?, sit: String? = null, id: Long = 2026020300, home: String = rival) =
            Game(id, state, true, t0, TeamLine(1, mine, a, if (a == null) null else 31), TeamLine(2, home, h, if (h == null) null else 27), clock,
                sit?.let { Situation(it, "00:47") })
        val other = listOf("VAN", "CGY", "TOR", "DAL").first { it != mine && it != rival }
        val mid = Clock(2, "REG", "08:12", false, true)
        fun board(g: Game, now: Instant = t0, tweak: (HockeyBoard.Input) -> HockeyBoard.Input = { it }) =
            tweak(HockeyBoard.input(g, HockeyBoard.WHITE, now))
        val (ai, hi) = TeamInk.pair(mine, other)
        val swingHist = List(90) { OddsSample(it * 20_000L, if (it < 70) 0.42f else 0.61f, if (it < 70) 0 else 1, 0) }
        val finalGame = game(GameState.OFF, 3, 0, null, id = 2026030417, home = other)
        val final = FinalMoments.Info(finalGame, listOf("Connor McDavid", "Stuart Skinner", "Leon Draisaitl"), "Stuart Skinner", shutout = true, cup = true)
        val n = GoalMoments.FRAMES
        return listOf(
            Entry("GOALS", "Shockwave", n) { GoalMoments.build("shockwave", mine, "Connor McDavid", 10, it, before = "2-2", after = "3-2") },
            Entry("GOALS", "Celly", n) { GoalMoments.build("celly", mine, "Connor McDavid", 10, it) },
            Entry("GOALS", "Net ripple", n) { GoalMoments.build("ripple", mine, "Connor McDavid", 10, it) },
            Entry("GOALS", "Hat trick", n) { GoalMoments.build("lamp", mine, "Leon Draisaitl", 14, it, GoalSpecial.HatTrick(3)) },
            Entry("GOALS", "Power-play goal", n) { GoalMoments.build("lamp", mine, "Zach Hyman", 6, it, GoalSpecial.PowerPlay) },
            Entry("GOALS", "Empty net", n) { GoalMoments.build("lamp", mine, "Ryan Nugent-Hopkins", 4, it, GoalSpecial.EmptyNet) },
            Entry("GOALS", "Milestone", n) { GoalMoments.build("lamp", mine, "Connor McDavid", 50, it, GoalSpecial.Milestone("500TH GOAL")) },
            Entry("IN GAME", "Power play", 30) { HockeyBoard.build(board(game(GameState.LIVE, 2, 2, mid, "1541", home = other)), it) },
            Entry("IN GAME", "Goalie pulled", 60) { HockeyBoard.build(board(game(GameState.LIVE, 2, 3, Clock(3, "REG", "01:12", false, true), "0651", home = other)), it) },
            Entry("IN GAME", "Big save", 18) { HockeyBoard.build(board(game(GameState.LIVE, 2, 2, mid, home = other)) { b ->
                b.copy(flash = HockeyBoard.Flash(HockeyBoard.Flash.Kind.SAVE, true, TeamInk.of(other), 9_000_001)) }, it) },
            Entry("IN GAME", "Big hit", 18) { HockeyBoard.build(board(game(GameState.LIVE, 2, 2, mid, home = other)) { b ->
                b.copy(flash = HockeyBoard.Flash(HockeyBoard.Flash.Kind.HIT, false, TeamInk.of(mine), 9_000_002)) }, it) },
            Entry("IN GAME", "Heartbeat", 24) { HockeyBoard.build(board(game(GameState.LIVE, 3, 2, Clock(3, "REG", "00:38", false, true), home = other)), it) },
            Entry("IN GAME", "Odds swing", OddsMeter.SWING_FRAMES + 6) {
                OddsMeter.board(mine, other, ai, hi, 0.61f, swingHist, true, it, 0.42f, it.coerceAtMost(OddsMeter.SWING_FRAMES)) },
            Entry("AFTER", "Zamboni", 150) { HockeyBoard.build(board(game(GameState.LIVE, 2, 1, Clock(1, "REG", "00:00", true, false), home = other)), it) },
            Entry("AFTER", "Final horn", FinalMoments.frames(final)) { FinalMoments.build(final, it) },
            Entry("AFTER", "Win streak", 12) { f -> LedGrid(24, 16).also { PixelArt.flame(it, 2, 15, 12, f, 7); PixelArt.flame(it, 13, 15, 8, f + 1, 7) } },
            Entry("HOME", "Puck-drop countdown", 40) { f -> HockeyBoard.build(board(game(GameState.FUT, null, null, null, home = other), t0.minusSeconds(754L - f / 8)), f) },
            Entry("HOME", "Game-day lights", WarmUp.FRAMES + 8) { f -> WarmUp.mask(HockeyBoard.build(board(game(GameState.FUT, null, null, null, home = other), t0.minusSeconds(9_000))), f) },
            Entry("HOME", "Standings move", 12) { f -> LedGrid(20, 12).also { g ->
                val up = Arrows.arrow(2, f); val down = Arrows.arrow(-1, f)
                for (y in 0 until 12) for (x in 0 until 7) { g.cells[y * 20 + x + 2] = up[x, y]; g.cells[y * 20 + x + 11] = down[x, y] }
            } },
            Entry("HOME", "Slapshot refresh", 24) { f -> if (f < 10) Slapshot.build(f / 10f, false, 0, TeamInk.of(mine)) else Slapshot.build(1f, true, f - 10, TeamInk.of(mine)) },
            Entry("SEASON", "Rivalry night", 30) { HockeyBoard.build(board(game(GameState.LIVE, 1, 1, mid, home = rival)), it) },
            Entry("SEASON", "Playoff beard", 4) { r -> HockeyBoard.build(board(game(GameState.LIVE, 1, 1, mid, id = 2026030111 + r * 100L, home = other))) },
        )
    }
}

/** Settings → Animations: pick any one and it plays here on a loop. */
@Composable
fun AnimationGallery(team: String) {
    val entries = remember(team) { Gallery.entries(team, if (team == "CGY") "EDM" else when (team) { "EDM" -> "CGY"; else -> HockeyBoard.rivalryFor(team) ?: "CGY" }) }
    var pick by remember { mutableStateOf(entries.first()) }
    var frame by remember(pick) { mutableIntStateOf(0) }
    LaunchedEffect(pick) {
        while (true) {
            delay(if (pick.name == "Playoff beard") 900 else 120)
            frame = (frame + 1) % (pick.frames + 6)
        }
    }
    val grid = remember(pick, frame) { pick.build(frame.coerceAtMost(pick.frames - 1)) }
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color.Black).padding(8.dp)) { PixelGrid(grid) }
    Spacer(Modifier.height(10.dp))
    entries.groupBy { it.group }.forEach { (group, list) ->
        Text(group, color = Dim, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { e ->
                val on = e == pick
                Text(
                    e.name, color = if (on) TextInk else Muted, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (on) Card3 else Card2)
                        .border(1.dp, if (on) TextInk else Line, RoundedCornerShape(8.dp))
                        .clickable { pick = e }.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}
