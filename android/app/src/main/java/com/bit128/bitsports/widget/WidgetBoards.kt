package com.bit128.bitsports.widget

import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.fantasy.FantasyNight
import com.bit128.bitsports.fantasy.Scoring
import com.bit128.bitsports.messageBoard
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.ui.HeatMap
import com.bit128.bitsports.ui.HockeyBoard
import com.bit128.bitsports.ui.LedGrid
import com.bit128.bitsports.ui.OddsMeter
import com.bit128.bitsports.ui.PixelArt
import com.bit128.bitsports.ui.PixelFont
import com.bit128.bitsports.ui.TeamInk
import java.time.Instant

/**
 * The all-LED widgets, frame by frame. The team widget is a little arena scoreboard in a
 * team-colour frame (glowing while your team leads) that flips by itself: the score, the last
 * goal, the odds; on game day the countdown and the matchup; between games what's up next.
 * Pure, so every frame is spacing-tested like the rest of the boards.
 */
object WidgetBoards {
    /** [g] inside a 2-LED border in [ink]; a soft outer glow when [glow]. */
    fun framed(g: LedGrid, ink: Int, glow: Boolean): LedGrid {
        val out = LedGrid(g.cols + 6, g.rows + 6)
        val dim = HeatMap.blend(0xFF000000.toInt(), ink, 0.45f)
        out.shape()
        for (x in 0 until out.cols) for (y in 0 until out.rows) {
            val edge = minOf(x, y, out.cols - 1 - x, out.rows - 1 - y)
            when {
                edge == 0 && glow -> if ((x + y) % 2 == 0) out.put(x, y, dim)
                edge == 1 -> out.put(x, y, ink)
            }
        }
        // The board sits inside with a dark gap, its spacing bookkeeping kept.
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            val i = y * g.cols + x
            val j = (y + 3) * out.cols + (x + 3)
            out.cells[j] = g.cells[i]
            out.owner[j] = if (g.owner[i] == 0) 0 else g.owner[i] + 10_000
        }
        out.textOwners += g.textOwners.map { it + 10_000 }
        return out
    }

    /** Every frame for the team widget, in the order it flips. */
    fun team(team: String, game: Game?, next: Game?, lastGoal: GoalEvent?, oddsShare: Float?, accent: Int, now: Instant = Instant.now()): List<LedGrid> {
        val ink = TeamInk.of(team)
        val frames = ArrayList<LedGrid>()
        var glow = false
        when {
            game != null && (game.state.isLive || game.state.isOver) -> {
                val input = HockeyBoard.input(game, accent, now)
                frames += HockeyBoard.build(input)
                val mine = if (game.home.abbrev == team) game.home.score ?: 0 else game.away.score ?: 0
                val theirs = if (game.home.abbrev == team) game.away.score ?: 0 else game.home.score ?: 0
                glow = mine > theirs
                lastGoal?.let { g ->
                    frames += HockeyBoard.build(input.copy(zone = HockeyBoard.Zone.Line(
                        "GOAL ${PixelFont.clean(g.scorer.substringAfterLast(' '))} ${AlertPlanner.periodLabel(g.period, g.periodType).uppercase()}".take(20),
                        TeamInk.of(g.teamAbbrev))))
                }
                if (game.state.isOver) frames += messageBoard(if (mine > theirs) "WIN" else "FINAL",
                    "$mine-$theirs", (if (game.home.abbrev == team) "VS " else "AT ") + PixelFont.clean(AlertCopy.nick(if (game.home.abbrev == team) game.away.abbrev else game.home.abbrev)), ink)
                if (!game.state.isOver) oddsShare?.let { frames += odds(game, it) }
            }
            game != null -> {
                // Game day.
                frames += HockeyBoard.build(HockeyBoard.input(game, accent, now))
                val opp = if (game.home.abbrev == team) game.away.abbrev else game.home.abbrev
                frames += messageBoard("GAME DAY", UpNext.whenText(game.startUtc, now).substringAfter(' '),
                    (if (game.home.abbrev == team) "VS " else "AT ") + PixelFont.clean(AlertCopy.nick(opp)), ink)
                oddsShare?.let { frames += odds(game, it) }
            }
            next != null -> {
                val opp = if (next.home.abbrev == team) next.away.abbrev else next.home.abbrev
                val w = UpNext.whenText(next.startUtc, now)
                frames += messageBoard("UP NEXT", w.substringBefore(' '), w.substringAfter(' ') + " " + (if (next.home.abbrev == team) "VS " else "AT ") + opp, ink)
                frames += HockeyBoard.build(HockeyBoard.input(next, accent, now).copy(zone = HockeyBoard.Zone.Line(UpNext.inText(next.startUtc, now).uppercase(), HockeyBoard.MUTED)))
            }
            else -> frames += messageBoard(PixelFont.clean(AlertCopy.nick(team)).take(12), "UP NEXT", "LOOKING IT UP", ink)
        }
        return frames.map { framed(it, ink, glow) }
    }

    /** The odds as a board: who's favoured, and the tug-of-war strip. */
    private fun odds(game: Game, share: Float): LedGrid {
        val g = LedGrid(HockeyBoard.COLS, HockeyBoard.ROWS)
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        g.textCenter(if (game.state.isLive) "LIVE ODDS" else "WIN ODDS", 4, if (game.state.isLive) HockeyBoard.LIVE else HockeyBoard.MUTED)
        val strip = OddsMeter.strip(game.away.abbrev, game.home.abbrev, ai, hi, share)
        for (y in 0 until strip.rows) for (x in 0 until strip.cols) {
            val v = strip[x, y]
            if (v != 0) { g.cells[(y + 18) * g.cols + x] = v; g.owner[(y + 18) * g.cols + x] = strip.owner[y * strip.cols + x] + 20_000 }
        }
        g.textOwners += strip.textOwners.map { it + 20_000 }
        return g
    }

    /**
     * One fantasy list on a board: name and tonight's total on top, then a row per player:
     * a live dot, the name, tonight's line, the points.
     */
    fun fantasy(n: FantasyNight.ListNight, rows: Int): LedGrid {
        val h = 11 + rows * 9
        val g = LedGrid(HockeyBoard.COLS, h)
        val total = Scoring.fmt(n.total)
        g.text(PixelFont.clean(n.list.name).take(PixelFont.fits(HockeyBoard.COLS - PixelFont.width(total) - 8)), 1, 1, HockeyBoard.MUTED)
        g.textRight(total, HockeyBoard.COLS - 2, 1, PixelArt.GOLD)
        g.fill(1, 9, HockeyBoard.COLS - 2, 1, HockeyBoard.DIM)
        val mvp = n.mvp
        n.rows.take(rows).forEachIndexed { i, r ->
            val y = 12 + i * 9
            val dot = when {
                r.game?.state?.isLive == true -> HockeyBoard.LIVE
                r.line != null -> HockeyBoard.WHITE
                r.game != null -> HockeyBoard.MUTED
                else -> HockeyBoard.DIM
            }
            g.fill(1, y + 2, 2, 3, dot)
            val pts = if (r.line != null) Scoring.fmt(r.points) else if (r.game != null) "-" else ""
            val line = r.line?.let { l -> if (r.player.isGoalie) (l.decision ?: "") + " ${l.saves}SV" else "${l.goals}G${l.assists}A" } ?: ""
            val ptsW = PixelFont.width(pts)
            val lineW = PixelFont.width(line)
            val nameRoom = HockeyBoard.COLS - 6 - ptsW - lineW - 10
            g.text(PixelFont.clean(r.player.lastName).take(PixelFont.fits(nameRoom)), 6, y, if (r == mvp) PixelArt.GOLD else HockeyBoard.WHITE)
            if (line.isNotEmpty()) g.textRight(line, HockeyBoard.COLS - 2 - ptsW - 5, y, HockeyBoard.MUTED)
            if (pts.isNotEmpty()) g.textRight(pts, HockeyBoard.COLS - 2, y, if (r.points > 0) TeamInk.of(r.player.team) else HockeyBoard.DIM)
        }
        return g
    }
}
