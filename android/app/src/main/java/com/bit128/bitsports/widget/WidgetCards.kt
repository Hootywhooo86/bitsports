package com.bit128.bitsports.widget

import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.ui.HeatMap
import com.bit128.bitsports.ui.HockeyBoard
import com.bit128.bitsports.ui.LedGrid
import com.bit128.bitsports.ui.OddsMeter
import com.bit128.bitsports.ui.PixelArt
import com.bit128.bitsports.ui.PixelFont
import com.bit128.bitsports.ui.TeamInk

/**
 * Taller widget boards that fill a squarer widget: 128 LEDs across, [rows] down. Three for game
 * day (Matchup, Tale of the tape, Countdown) and three for days off (Team card, Last game,
 * Around the league). Pure, so their spacing is tested.
 */
object WidgetCards {
    const val COLS = 128

    /** What a team's line looks like on these cards. */
    data class TeamLine(val abbrev: String, val record: String, val points: Int, val place: String,
                        val gfPerGame: Double, val gaPerGame: Double, val ppPct: Double, val pkPct: Double, val l10: String, val streak: String?)

    data class Recap(val game: Game, val scorers: List<Pair<String, String>>, val stars: List<String>)

    private val JERSEY = arrayOf(
        "....pppppppp....",
        "..pppp.pp.pppp..",
        ".pppppppppppppp.",
        "pppppppppppppppp",
        "ppp.pppppppp.ppp",
        "pp..pppppppp..pp",
        "ss..ssssssss..ss",
        "pp..pppppppp..pp",
        "....pppppppp....",
        "....ssssssss....",
        "....pppppppp....",
        "....pppppppp....",
    )

    private fun jersey(g: LedGrid, team: String, x: Int, y: Int, scale: Int = 2) {
        val c = HockeyBoard.colors(team)
        // Dark team colours (navy, black) are lifted so the jersey still reads on a black board.
        fun lum(v: Int) = (0.299 * (v shr 16 and 0xFF) + 0.587 * (v shr 8 and 0xFF) + 0.114 * (v and 0xFF)) / 255
        fun lit(v: Int) = if (lum(v) < 0.2) HeatMap.blend(v, 0xFFFFFFFF.toInt(), 0.3f) else v
        g.sprite(JERSEY, x, y, mapOf('p' to lit(c.first), 's' to lit(c.second).let { if (it == lit(c.first)) HockeyBoard.WHITE else it }), scale)
    }

    private fun when_(game: Game) = UpNext.whenText(game.startUtc).substringAfter(' ')

    private fun stripAt(g: LedGrid, strip: LedGrid, y: Int) {
        for (yy in 0 until strip.rows) for (x in 0 until strip.cols) {
            val v = strip[x, yy]
            if (v != 0) { g.cells[(y + yy) * g.cols + x] = v; g.owner[(y + yy) * g.cols + x] = strip.owner[yy * strip.cols + x] + 30_000 }
        }
        g.textOwners += strip.textOwners.map { it + 30_000 }
    }

    /** "IN 8H 20M", "IN 20M", "PUCK DROP". */
    fun countdownText(start: java.time.Instant, now: java.time.Instant = java.time.Instant.now()): String {
        val m = java.time.Duration.between(now, start).toMinutes()
        return when { m <= 0 -> "PUCK DROP"; m < 60 -> "IN ${m}M"; else -> "IN ${m / 60}H ${m % 60}M" }
    }

    /** "LAST MEET W 4-1" from [team]'s side, for their last game against the same opponent. */
    fun lastMeeting(team: String, g: Game): String {
        val home = g.home.abbrev == team
        val us = (if (home) g.home.score else g.away.score) ?: 0
        val them = (if (home) g.away.score else g.home.score) ?: 0
        return "LAST MEET ${if (us > them) "W" else "L"} $us-$them"
    }

    /** How many LED rows make a 128-wide board fill a widget this shape. */
    fun rowsFor(widthDp: Int, heightDp: Int): Int = (heightDp.toFloat() / widthDp * COLS).toInt().coerceIn(60, 140)

    // ---------- game day ----------

    /** A: both jerseys big, records, VS, puck drop, the last meeting and the odds. */
    fun matchup(game: Game, awayRecord: String?, homeRecord: String?, lastMeeting: String?, oddsShare: Float?, countdown: String, rows: Int = 104): LedGrid {
        val g = LedGrid(COLS, rows)
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        jersey(g, game.away.abbrev, 2, 2); jersey(g, game.home.abbrev, COLS - 34, 2)
        g.textCenter("VS", 8, HockeyBoard.WHITE, 3)
        g.text(game.away.abbrev, 9, 30, ai); g.textRight(game.home.abbrev, COLS - 10, 30, hi)
        awayRecord?.let { g.text(it, 2, 40, HockeyBoard.MUTED) }; homeRecord?.let { g.textRight(it, COLS - 3, 40, HockeyBoard.MUTED) }
        // Below the jerseys, top to bottom as far as the widget is tall: puck drop (big if
        // there's room), the countdown, the last meeting, the odds bar at the very bottom.
        var y = 50
        val oddsRows = if (oddsShare != null) 12 else 0
        fun room(h: Int) = y + h <= rows - oddsRows
        val time = when_(game)
        if (room(14) && rows - oddsRows - y >= 14 + 10) { g.textCenter(time, y, HockeyBoard.WHITE, 2); y += 18 }
        else if (room(7)) { g.textCenter(time, y, HockeyBoard.WHITE); y += 10 }
        if (room(7)) { g.textCenter(countdown, y, HockeyBoard.MUTED); y += 10 }
        lastMeeting?.let { if (room(7)) { g.textCenter(it.take(20), y, if (it.contains(" W ")) 0xFF7ED957.toInt() else HockeyBoard.MUTED); y += 10 } }
        oddsShare?.let { stripAt(g, OddsMeter.strip(game.away.abbrev, game.home.abbrev, ai, hi, it), rows - 10) }
        return g
    }

    /** B: the matchup up top, then head-to-head bars in team colours. */
    fun taleOfTheTape(game: Game, away: TeamLine, home: TeamLine, rows: Int = 104): LedGrid {
        val g = LedGrid(COLS, rows)
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        g.text(game.away.abbrev, 2, 2, ai); g.textRight(game.home.abbrev, COLS - 3, 2, hi)
        g.textCenter(when_(game), 2, HockeyBoard.WHITE)
        g.textCenter("TALE OF THE TAPE", 12, PixelArt.GOLD)
        fun pct(v: Double) = "%.0f".format(java.util.Locale.ROOT, v * 100)
        fun one(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)
        fun l10pts(s: String) = s.split("-").map { it.toIntOrNull() ?: 0 }.let { (it.getOrElse(0) { 0 }) * 2 + it.getOrElse(2) { 0 } }
        val bars = listOf(
            Triple("PTS", "${away.points}" to "${home.points}", away.points.toFloat() to home.points.toFloat()),
            Triple("GF/G", one(away.gfPerGame) to one(home.gfPerGame), away.gfPerGame.toFloat() to home.gfPerGame.toFloat()),
            // Fewer against is better: the bar goes to the side that allows less.
            Triple("GA/G", one(away.gaPerGame) to one(home.gaPerGame), home.gaPerGame.toFloat() to away.gaPerGame.toFloat()),
            Triple("PP%", pct(away.ppPct) to pct(home.ppPct), away.ppPct.toFloat() to home.ppPct.toFloat()),
            Triple("PK%", pct(away.pkPct) to pct(home.pkPct), away.pkPct.toFloat() to home.pkPct.toFloat()),
            Triple("L10", away.l10 to home.l10, l10pts(away.l10).toFloat() to l10pts(home.l10).toFloat()),
        )
        val each = ((rows - 22) / bars.size).coerceAtLeast(12)
        bars.forEachIndexed { i, (name, v, s) ->
            val y = 22 + i * each
            g.text(v.first, 2, y, ai); g.textCenter(name, y, HockeyBoard.MUTED); g.textRight(v.second, COLS - 3, y, hi)
            val share = if (s.first + s.second <= 0f) 0.5f else s.first / (s.first + s.second)
            val split = (share * (COLS - 4)).toInt().coerceIn(1, COLS - 5)
            g.fill(2, y + 9, split, 2, ai); g.fill(2 + split, y + 9, COLS - 4 - split, 2, hi)
        }
        return g
    }

    /** C: a big countdown to puck drop under the matchup, then the odds and the last meeting. */
    fun countdown(game: Game, hours: Long, minutes: Long, lastMeeting: String?, oddsShare: Float?, rows: Int = 104): LedGrid {
        val g = LedGrid(COLS, rows)
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        g.text(game.away.abbrev, 2, 2, ai); g.textRight(game.home.abbrev, COLS - 3, 2, hi)
        g.textCenter("PUCK DROP", 2, HockeyBoard.MUTED)
        val clock = "%d:%02d".format(java.util.Locale.ROOT, hours, minutes)
        g.textCenter(clock, 14, PixelArt.GOLD, 4)
        val cx = (COLS - PixelFont.width(clock, 4)) / 2
        g.text("HRS", cx, 46, HockeyBoard.DIM); g.textRight("MIN", cx + PixelFont.width(clock, 4) - 1, 46, HockeyBoard.DIM)
        g.textCenter(when_(game), 58, HockeyBoard.WHITE)
        lastMeeting?.let { g.textCenter(it.take(20), 70, if (it.contains(" W ")) 0xFF7ED957.toInt() else HockeyBoard.MUTED) }
        oddsShare?.let { stripAt(g, OddsMeter.strip(game.away.abbrev, game.home.abbrev, ai, hi, it), rows - 12) }
        return g
    }

    // ---------- days off ----------

    /** D: your team at a glance: place, record, streak, last result, what's next. */
    fun teamCard(t: TeamLine, last: String?, next: Game?, rows: Int = 104, frame: Int = 0): LedGrid {
        val g = LedGrid(COLS, rows)
        val ink = TeamInk.of(t.abbrev)
        val nick = PixelFont.clean(AlertCopy.nick(t.abbrev)).uppercase()
        g.text(nick.take(PixelFont.fits(88, 2)), 2, 2, ink, 2)
        jersey(g, t.abbrev, COLS - 34, 2)
        g.text(t.place, 2, 20, HockeyBoard.WHITE)
        g.text("${t.record} ${t.points} PTS", 2, 32, HockeyBoard.MUTED)
        g.text("L10 ${t.l10}", 2, 44, HockeyBoard.MUTED)
        t.streak?.let { s ->
            val win = s.startsWith("W")
            g.textRight(s, COLS - 12, 44, if (win) 0xFF7ED957.toInt() else HockeyBoard.LIVE)
            if (win && (s.drop(1).toIntOrNull() ?: 0) >= 2) PixelArt.flame(g, COLS - 9, 50, 8, frame, w = 6)
        }
        last?.let { g.text(it.take(20), 2, 56, if (it.startsWith("LAST W")) 0xFF7ED957.toInt() else HockeyBoard.MUTED) }
        g.fill(2, 68, COLS - 4, 1, HockeyBoard.DIM)
        if (next != null) {
            val home = next.home.abbrev == t.abbrev
            val opp = if (home) next.away.abbrev else next.home.abbrev
            g.text("UP NEXT", 2, 73, ink)
            g.textRight((if (home) "VS " else "@ ") + opp, COLS - 3, 73, TeamInk.of(opp))
            g.textCenter(UpNext.whenText(next.startUtc), 84, HockeyBoard.WHITE)
            g.textCenter(UpNext.inText(next.startUtc).uppercase(), 94, HockeyBoard.MUTED)
        }
        return g
    }

    private val STAR = arrayOf("..#..", ".###.", "#####", ".###.", ".#.#.")

    /** E: last game's final, who scored, and the three stars. */
    fun lastGame(r: Recap, mine: String, next: Game?, rows: Int = 104): LedGrid {
        val g = LedGrid(COLS, rows)
        val game = r.game
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        val a = game.away.score ?: 0; val h = game.home.score ?: 0
        g.textCenter("LAST GAME", 2, HockeyBoard.MUTED)
        g.text(game.away.abbrev, 2, 14, ai); g.textRight(game.home.abbrev, COLS - 3, 14, hi)
        g.textCenter("$a-$h", 11, HockeyBoard.WHITE, 2)
        // Scorers: each team on its own line, two per line.
        var y = 30
        for ((team, ink) in listOf(game.away.abbrev to ai, game.home.abbrev to hi)) {
            val names = r.scorers.filter { it.first == team }.groupingBy { it.second }.eachCount()
                .map { (n, c) -> PixelFont.clean(n.substringAfterLast(' ').substringAfterLast('.')).trim() + if (c > 1) " X$c" else "" }
            names.chunked(2).take(2).forEach { pair -> g.text(pair.joinToString(" ").take(PixelFont.fits(COLS - 4)), 2, y, ink); y += 9 }
        }
        y += 3
        r.stars.take(3).forEachIndexed { i, s ->
            g.sprite(STAR, 2, y + 1, mapOf('#' to if (i == 0) PixelArt.GOLD else HockeyBoard.MUTED))
            g.text(PixelFont.clean(s.substringAfterLast(' ').substringAfterLast('.')).trim().take(PixelFont.fits(COLS - 14)), 10, y, if (i == 0) PixelArt.GOLD else HockeyBoard.WHITE)
            y += 9
        }
        next?.let {
            val home = it.home.abbrev == mine
            g.textCenter(("NEXT " + (if (home) "VS " else "@ ") + (if (home) it.away.abbrev else it.home.abbrev) + " " + UpNext.whenText(it.startUtc).substringBefore(' ')).take(20), rows - 9, TeamInk.of(mine))
        }
        return g
    }

    /** F: your next game on top, then the rest of the league's games on the scoreboard. */
    fun aroundTheLeague(mine: String, next: Game?, games: List<Game>, followed: Set<String>, accent: Int, rows: Int = 104): LedGrid {
        val g = LedGrid(COLS, rows)
        val ink = TeamInk.of(mine)
        if (next != null) {
            val home = next.home.abbrev == mine
            g.text("NEXT", 2, 2, ink)
            g.textRight(((if (home) "VS " else "@ ") + (if (home) next.away.abbrev else next.home.abbrev) + " " + UpNext.whenText(next.startUtc).substringBefore(' ')).take(15), COLS - 3, 2, HockeyBoard.WHITE)
        }
        g.fill(2, 12, COLS - 4, 1, HockeyBoard.DIM)
        g.textCenter("AROUND THE NHL", 16, HockeyBoard.MUTED)
        val fit = ((rows - 27) / ScoreboardLeds.ROW_H).coerceAtLeast(1)
        val board = ScoreboardLeds.build(games, followed, accent, fit)
        for (y in 0 until board.rows) for (x in 0 until board.cols) {
            if (27 + y >= rows) break
            val v = board[x, y]
            if (v != 0) { g.cells[(27 + y) * COLS + x] = v; g.owner[(27 + y) * COLS + x] = board.owner[y * board.cols + x] + 40_000 }
        }
        g.textOwners += board.textOwners.map { it + 40_000 }
        return g
    }
}
