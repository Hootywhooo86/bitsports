package com.bit128.bitsports.widget

import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.Strengths
import com.bit128.bitsports.ui.HockeyBoard
import com.bit128.bitsports.ui.LedGrid
import com.bit128.bitsports.ui.PixelFont
import com.bit128.bitsports.ui.ScoreFormat

/**
 * Tonight's NHL games as one 128-LED-wide arena board, a game per 10-LED row:
 *
 *   [stripe] AWY 2-1 HOM [stripe]  PP  P2 8:41
 *
 * Followed teams get an accent underline; a power play lights PP in yellow.
 */
object ScoreboardLeds {
    const val COLS = 128
    const val ROW_H = 10

    fun rowsFor(games: Int) = maxOf(1, games) * ROW_H - 2

    fun build(games: List<Game>, followed: Set<String>, accent: Int, maxGames: Int): LedGrid {
        val shown = games.take(maxGames.coerceAtLeast(1))
        val g = LedGrid(COLS, rowsFor(shown.size))
        if (shown.isEmpty()) {
            g.textCenter("NO GAMES", 0, HockeyBoard.MUTED)
            return g
        }
        shown.forEachIndexed { i, game -> row(g, game, i * ROW_H, followed, accent) }
        return g
    }

    private fun row(g: LedGrid, game: Game, y: Int, followed: Set<String>, accent: Int) {
        val has = game.state.isLive || game.state.isOver
        val a = game.away.score ?: 0
        val h = game.home.score ?: 0
        val ac = HockeyBoard.colors(game.away.abbrev)
        val hc = HockeyBoard.colors(game.home.abbrev)
        // Team stripes: top half first colour, bottom half second.
        g.fill(0, y, 2, 4, ac.first); g.fill(0, y + 4, 2, 3, ac.second)
        g.fill(65, y, 2, 4, hc.first); g.fill(65, y + 4, 2, 3, hc.second)
        // Over: the loser's code dims. Live: both stay lit.
        val awayInk = if (game.state.isOver && a < h) HockeyBoard.DIM else HockeyBoard.led(ac)
        val homeInk = if (game.state.isOver && h < a) HockeyBoard.DIM else HockeyBoard.led(hc)
        g.text(game.away.abbrev.take(3), 3, y, awayInk)
        g.text(game.home.abbrev.take(3), 47, y, homeInk)
        val mid = if (has) "$a-$h" else "@"
        val mx = 22 + (24 - PixelFont.width(mid)) / 2
        g.text(mid, mx, y, if (has) HockeyBoard.WHITE else HockeyBoard.DIM)

        val banner = Strengths.banner(game)
        if (banner?.kind == Strengths.Kind.PP) g.text("PP", 70, y, HockeyBoard.WARN)
        if (banner?.kind == Strengths.Kind.EMPTY_NET) g.text("EN", 70, y, HockeyBoard.LIVE)
        val (status, color) = status(game)
        g.textRight(status, COLS - 1, y, color)

        if (game.away.abbrev in followed || game.home.abbrev in followed) g.fill(3, y + 8, 61, 1, accent)
    }

    /** Short enough to sit right of the score: "P2 8:41", "INT P1", "F/OT", "7:00PM". */
    fun status(game: Game): Pair<String, Int> {
        val c = game.clock
        return when {
            !game.scheduleOk -> "PPD" to HockeyBoard.WARN
            game.state.isLive && c != null && c.inIntermission -> "INT ${short(c.period, c.periodType)}" to HockeyBoard.MUTED
            game.state.isLive && c != null -> "${short(c.period, c.periodType)} ${c.timeRemaining.removePrefix("0")}" to HockeyBoard.LIVE
            game.state.isOver -> ScoreFormat.status(game).replace("FINAL/", "F/") to HockeyBoard.MUTED
            else -> ScoreFormat.status(game).filterNot { it.isWhitespace() || it == '\u00A0' || it == '\u202F' }.uppercase() to HockeyBoard.MUTED
        }
    }

    private fun short(period: Int, type: String) = when (type) {
        "OT" -> if (period > 4) "${period - 3}OT" else "OT"
        "SO" -> "SO"
        else -> "P$period"
    }
}
