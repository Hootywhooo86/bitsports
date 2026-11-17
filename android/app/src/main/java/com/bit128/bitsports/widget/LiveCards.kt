package com.bit128.bitsports.widget

import java.time.Instant
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.OddsSample
import com.bit128.bitsports.nhl.OnIce
import com.bit128.bitsports.nhl.Shift
import com.bit128.bitsports.nhl.Strength
import com.bit128.bitsports.ui.HeatMap
import com.bit128.bitsports.ui.HockeyBoard
import com.bit128.bitsports.ui.LedGrid
import com.bit128.bitsports.ui.OddsMeter
import com.bit128.bitsports.ui.PixelArt
import com.bit128.bitsports.ui.PixelFont
import com.bit128.bitsports.ui.TeamInk

/**
 * Six ways to fill a widget during the game, all 128 LEDs across and as tall as the widget. Each
 * but the first starts with the same compact score header. Pure, so spacing is tested.
 */
object LiveCards {
    const val COLS = 128
    /** Rows the compact header takes, gap included. */
    const val HEADER = 30
    /** The smooth widget draws the rink this many times finer than the board's LEDs. */
    const val FINE = 4

    private val SPEAKER = arrayOf("...#...", "..##.w.", "####..w", "####.w.", "####..w", "..##.w.", "...#...")

    private fun paste(g: LedGrid, src: LedGrid, y: Int, tag: Int, x0: Int = 0) {
        for (yy in 0 until src.rows) {
            if (y + yy >= g.rows) break
            for (x in 0 until src.cols) {
                val v = src[x, yy]
                if (v != 0 && x0 + x in 0 until g.cols) { val i = (y + yy) * g.cols + x0 + x; g.cells[i] = v; g.owner[i] = src.owner[yy * src.cols + x] + tag }
            }
        }
        g.textOwners += src.textOwners.map { it + tag }
    }

    /**
     * VAN  1-3  EDM over the clock, with the power play beside it when there is one. Before puck
     * drop it's VAN  VS  EDM over the start time and countdown. [sogBeside] puts each side's shots
     * at the ends of the clock line (when there's no room for a line of its own).
     */
    fun header(g: LedGrid, game: Game, roomLeft: Int = 0, sogBeside: Boolean = false, radioOn: Boolean = false) {
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        g.text(game.away.abbrev, 2, 6, ai); g.textRight(game.home.abbrev, COLS - 3, 6, hi)
        val started = game.state.isLive || game.state.isOver
        if (started) {
            val a = game.away.score ?: 0; val h = game.home.score ?: 0
            val step = (PixelFont.W + 1) * 2
            val all = "$a-$h"
            val x0 = (COLS - PixelFont.width(all, 2)) / 2
            g.text("$a", x0, 2, if (a >= h) HockeyBoard.WHITE else HockeyBoard.MUTED, 2)
            g.text("-", x0 + "$a".length * step, 2, HockeyBoard.DIM, 2)
            g.text("$h", x0 + ("$a".length + 1) * step, 2, if (h >= a) HockeyBoard.WHITE else HockeyBoard.MUTED, 2)
        } else g.textCenter("VS", 2, HockeyBoard.WHITE, 2)
        val clock = if (started) HockeyBoard.clockText(game)
            else UpNext.whenText(game.startUtc).substringAfter(' ') + " " + WidgetCards.countdownText(game.startUtc)
        val banner = com.bit128.bitsports.nhl.Strengths.banner(game)?.text
        var edge = roomLeft
        var speakerX = 2
        if (sogBeside && started) {
            val l = "${game.away.sog ?: 0}"; val r = "${game.home.sog ?: 0}"
            g.text(l, 2, 19, ai); g.textRight(r, COLS - 3, 19, hi)
            edge = maxOf(edge, maxOf(PixelFont.width(l), PixelFont.width(r)) + 6)
            speakerX = 2 + PixelFont.width(l) + 3
        }
        // The radio's on: a little speaker at the left of the clock line.
        if (radioOn) {
            g.sprite(SPEAKER, speakerX, 19, mapOf('#' to HockeyBoard.WHITE, 'w' to PixelArt.GOLD))
            edge = maxOf(edge, speakerX + SPEAKER[0].length + 3)
        }
        // [edge] keeps the line clear of what's at the ends (shots, the radio speaker), both sides alike.
        val line = listOfNotNull(clock, banner).joinToString(" ").take(PixelFont.fits(COLS - 2 * edge))
        g.textCenter(line, 19, when { banner != null -> HockeyBoard.WARN; started -> HockeyBoard.LIVE; else -> HockeyBoard.MUTED })
    }

    private fun base(game: Game, rows: Int, roomLeft: Int = 0) = LedGrid(COLS, rows).also { header(it, game, roomLeft) }

    /** 1. The arena jumbotron, then the period-by-period linescore under it. */
    fun jumbotron(game: Game, goals: List<GoalEvent>, accent: Int, rows: Int): LedGrid {
        val g = LedGrid(COLS, rows)
        paste(g, HockeyBoard.build(HockeyBoard.input(game, accent)), 0, 50_000)
        val periods = maxOf(3, game.clock?.period ?: 3)
        val labels = (1..periods).map { if (it <= 3) "$it" else if (it == 4) "OT" else "${it - 3}OT" } + "T"
        val colW = 16
        val x0 = COLS - 2 - colW * labels.size
        var y = 50
        if (y + 27 > rows) return g
        labels.forEachIndexed { i, l -> g.textRight(l, x0 + colW * (i + 1) - 2, y, HockeyBoard.MUTED) }
        for ((team, ink) in listOf(game.away.abbrev to TeamInk.pair(game.away.abbrev, game.home.abbrev).first, game.home.abbrev to TeamInk.pair(game.away.abbrev, game.home.abbrev).second)) {
            y += 10
            g.text(team, 2, y, ink)
            (1..periods).forEach { p ->
                val n = goals.count { it.teamAbbrev == team && it.period == p }
                val played = p <= (game.clock?.period ?: 3)
                g.textRight(if (played) "$n" else "-", x0 + colW * p - 2, y, if (n > 0) HockeyBoard.WHITE else HockeyBoard.DIM)
            }
            g.textRight("${goals.count { it.teamAbbrev == team }}", x0 + colW * labels.size - 2, y, HockeyBoard.WHITE)
        }
        return g
    }

    /**
     * 2. The shot heat map, as big as the widget allows, under the score: the game-day widget.
     * Before puck drop the rink waits empty under VS and the countdown, with [below] (the last
     * meeting) under it if there's room.
     */
    fun heat(game: Game, d: GameDetail?, rows: Int, onlyTeam: String? = null, radioOn: Boolean = false, below: String? = null): LedGrid {
        val started = game.state.isLive || game.state.isOver
        val heat = HeatMap.heat(d?.let { com.bit128.bitsports.nhl.GameAnalysis.shots(it) }.orEmpty(), game.away.abbrev, onlyTeam)
        val inks = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        val rink = HeatMap.draw(heat, inks)
        if (rows < HEADER - 2 + rink.rows) return compactHeat(game, rink, rows).also { c ->
            c.detail?.let { d0 -> c.detail = LedGrid.Detail(HeatMap.drawFine(heat, inks, d0.grid.cols * FINE, d0.grid.rows * FINE), d0.x, d0.y, FINE) }
        }
        val g = LedGrid(COLS, rows)
        // The line under the rink: shots on goal (or, before puck drop, the last meeting) if it fits.
        val lineBelow = HEADER - 2 + rink.rows + 9 <= rows
        header(g, game, sogBeside = !lineBelow, radioOn = radioOn)
        val text = if (started) null else below
        val heights = if (lineBelow && (started || text != null)) listOf(rink.rows, 7) else listOf(rink.rows)
        val at = WidgetCards.spread(heights, rows - HEADER + 2).map { it + HEADER - 2 }
        paste(g, rink, at[0], 60_000)
        g.detail = LedGrid.Detail(HeatMap.drawFine(heat, inks, rink.cols * FINE, rink.rows * FINE), 0, at[0], FINE)
        if (heights.size == 2) {
            val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
            if (started) { g.text("SOG ${game.away.sog ?: 0}", 2, at[1], ai); g.textRight("${game.home.sog ?: 0} SOG", COLS - 3, at[1], hi) }
            else text?.let { g.textCenter(it.take(20), at[1], if (it.contains(" W ")) 0xFF7ED957.toInt() else HockeyBoard.MUTED) }
        }
        return g
    }

    /**
     * A short, wide widget: one line on top (teams at the ends, the time or score and clock in the
     * middle) and the whole rink shrunk to the rows left, so it's never cut off. Live, the score
     * sits big at each side of the rink with the shots under it when there's room.
     */
    private fun compactHeat(game: Game, rink: LedGrid, rows: Int): LedGrid {
        val g = LedGrid(COLS, rows)
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        val started = game.state.isLive || game.state.isOver
        g.text(game.away.abbrev, 2, 1, ai); g.textRight(game.home.abbrev, COLS - 3, 1, hi)
        val h = (rows - 10).coerceIn(8, rink.rows)
        val small = shrink(rink, h)
        val x0 = (COLS - small.cols) / 2
        val side = x0 - 4
        val a = "${game.away.score ?: 0}"; val hs = "${game.home.score ?: 0}"
        val bigSides = started && side >= PixelFont.width(maxOf(a, hs, compareBy { it.length }), 2) + 2 && h >= 16
        val clock = if (started) HockeyBoard.clockText(game) else UpNext.whenText(game.startUtc).substringAfter(' ')
        val middle = when {
            !started -> if (java.time.Instant.now() >= game.startUtc) "PUCK DROP" else clock
            bigSides -> clock
            else -> "$a-$hs $clock"
        }
        val edge = maxOf(PixelFont.width(game.away.abbrev), PixelFont.width(game.home.abbrev)) + 6
        val line = middle.take(PixelFont.fits(COLS - 2 * edge)).let { if (it.length < middle.length) middle.substringBefore(' ').take(PixelFont.fits(COLS - 2 * edge)) else it }
        g.textCenter(line, 1, if (started) HockeyBoard.LIVE else HockeyBoard.MUTED)
        val y0 = 10 + (rows - 10 - h) / 2
        paste(g, small, y0, 60_000, x0)
        // A placeholder: heat() swaps in the fine rink at this spot and size.
        g.detail = LedGrid.Detail(small, x0, y0, 1)
        // Before puck drop the sides say when: PUCK DROP · IN 1H 9M.
        val left = Instant.now().let { java.time.Duration.between(it, game.startUtc).toMinutes() }
        if (!started && left > 0 && h >= 18) {
            val cd = if (left < 60) "${left}M" else "${left / 60}H ${left % 60}M"
            val sides = listOf(listOf("PUCK", "DROP"), listOf("IN", cd))
            if (sides.flatten().all { PixelFont.width(it) + 4 <= side }) {
                val sy = y0 + (h - 16) / 2
                sides.forEachIndexed { k, ls -> ls.forEachIndexed { j, t ->
                    val cx = if (k == 0) x0 / 2 else x0 + small.cols + (COLS - x0 - small.cols) / 2
                    g.text(t, cx - PixelFont.width(t) / 2, sy + j * 9, if (k == 1 && j == 1) HockeyBoard.WHITE else HockeyBoard.MUTED)
                } }
            }
        }
        if (bigSides) {
            val sy = y0 + (h - 14) / 2 - (if (h >= 26) 4 else 0)
            val am = (game.away.score ?: 0) >= (game.home.score ?: 0); val hm = (game.home.score ?: 0) >= (game.away.score ?: 0)
            g.text(a, (x0 - PixelFont.width(a, 2)) / 2, sy, if (am) ai else HockeyBoard.MUTED, 2)
            g.text(hs, x0 + small.cols + (COLS - x0 - small.cols - PixelFont.width(hs, 2)) / 2, sy, if (hm) hi else HockeyBoard.MUTED, 2)
            if (h >= 26) {
                val l = "${game.away.sog ?: 0}"; val r = "${game.home.sog ?: 0}"
                g.text(l, (x0 - PixelFont.width(l)) / 2, sy + 18, HockeyBoard.DIM)
                g.text(r, x0 + small.cols + (COLS - x0 - small.cols - PixelFont.width(r)) / 2, sy + 18, HockeyBoard.DIM)
            }
        }
        return g
    }

    /** The rink at [h] rows, same shape; each LED keeps the brightest of the ones it covers, so lines and heat survive. */
    internal fun shrink(src: LedGrid, h: Int): LedGrid {
        if (h >= src.rows) return src
        val w = (h * src.cols + src.rows / 2) / src.rows
        val out = LedGrid(w, h)
        fun lum(c: Int) = ((c shr 16) and 0xFF) * 3 + ((c shr 8) and 0xFF) * 6 + (c and 0xFF)
        for (y in 0 until h) for (x in 0 until w) {
            val sy0 = y * src.rows / h; val sy1 = maxOf(sy0 + 1, (y + 1) * src.rows / h)
            val sx0 = x * src.cols / w; val sx1 = maxOf(sx0 + 1, (x + 1) * src.cols / w)
            var best = 0; var bl = -1; var bo = 0
            for (yy in sy0 until sy1) for (xx in sx0 until sx1) {
                val c = src[xx, yy]; val l = lum(c)
                if (l > bl) { bl = l; best = c; bo = src.owner[yy * src.cols + xx] }
            }
            out.cells[y * w + x] = best; out.owner[y * w + x] = bo
        }
        return out
    }

    /** 3. The WIN-O-METER under the score. */
    fun odds(game: Game, share: Float?, history: List<OddsSample>, rows: Int): LedGrid {
        val g = base(game, rows)
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        val meter = OddsMeter.board(game.away.abbrev, game.home.abbrev, ai, hi, share, history, live = true)
        paste(g, meter, HEADER, 70_000)
        return g
    }

    /** 4. Every goal so far, newest at the bottom: when, who, and any PP / SH / EN tag. */
    fun goals(game: Game, goals: List<GoalEvent>, rows: Int): LedGrid {
        val g = base(game, rows)
        g.textCenter("GOALS", HEADER, HockeyBoard.MUTED)
        val fit = ((rows - HEADER - 11) / 10).coerceAtLeast(1)
        val shown = goals.takeLast(fit)
        shown.forEachIndexed { i, goal ->
            val y = HEADER + 11 + i * 10
            val ink = TeamInk.of(goal.teamAbbrev)
            // "3 4:00", "OT 2:11": the period and the time into it.
            val p = if (goal.periodType == "OT") "OT" else "${goal.period}"
            g.text("$p ${goal.timeInPeriod.removePrefix("0")}", 2, y, HockeyBoard.DIM)
            g.text(goal.teamAbbrev, 46, y, ink)
            val tag = when (goal.strength) { Strength.PP -> "PP"; Strength.SH -> "SH"; Strength.EN -> "EN"; else -> null }
            val name = PixelFont.clean(goal.scorer.substringAfterLast(' ')) + (goal.scorerTotal?.let { " $it" } ?: "")
            val room = PixelFont.fits(COLS - 68 - (tag?.let { PixelFont.width(it) + 4 } ?: 0))
            g.text(name.take(room), 68, y, if (i == shown.lastIndex) PixelArt.GOLD else HockeyBoard.WHITE)
            tag?.let { g.textRight(it, COLS - 3, y, HockeyBoard.WARN) }
        }
        if (goals.isEmpty()) g.textCenter("NO GOALS YET", HEADER + 14, HockeyBoard.DIM)
        return g
    }

    /** 5. Head-to-head bars for the game so far, in team colours. */
    fun stats(game: Game, d: GameDetail, rows: Int): LedGrid {
        val g = base(game, rows)
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        fun count(type: String, home: Boolean) = d.plays.count { it.type == type && (it.teamId == game.home.id) == home }
        val fo = count("faceoff", false) to count("faceoff", true)
        val foPct = if (fo.first + fo.second == 0) 50 to 50 else (fo.first * 100 / (fo.first + fo.second)).let { it to 100 - it }
        // name, (away, home), whether fewer is better
        val bars = listOf(
            Triple("SHOTS", (game.away.sog ?: count("shot-on-goal", false)) to (game.home.sog ?: count("shot-on-goal", true)), false),
            Triple("HITS", count("hit", false) to count("hit", true), false),
            Triple("FO%", foPct, false),
            Triple("TAKEAWAYS", count("takeaway", false) to count("takeaway", true), false),
            Triple("GIVEAWAYS", count("giveaway", false) to count("giveaway", true), true),
        )
        val each = 14
        bars.forEachIndexed { i, (name, v, fewerBetter) ->
            val y = HEADER + i * each
            if (y + 11 > rows) return@forEachIndexed
            g.text("${v.first}", 2, y, ai); g.textCenter(name, y, HockeyBoard.MUTED); g.textRight("${v.second}", COLS - 3, y, hi)
            val a = if (fewerBetter) v.second else v.first
            val b = if (fewerBetter) v.first else v.second
            val share = if (a + b == 0) 0.5f else a.toFloat() / (a + b)
            val split = (share * (COLS - 4)).toInt().coerceIn(1, COLS - 5)
            g.fill(2, y + 9, split, 2, ai); g.fill(2 + split, y + 9, COLS - 4 - split, 2, hi)
        }
        return g
    }

    /** 6. Who's on the ice right now, both teams side by side; the goalie marked G. */
    fun onIce(game: Game, shifts: List<Shift>, goalieIds: Set<Int>, rows: Int): LedGrid {
        val g = base(game, rows)
        val snap = OnIce.snapshot(game, shifts)
        g.textCenter("ON THE ICE", HEADER, HockeyBoard.MUTED)
        if (snap == null) { g.textCenter("WAITING FOR SHIFTS", HEADER + 16, HockeyBoard.DIM); return g }
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        fun names(list: List<Shift>) = list.sortedBy { it.playerId in goalieIds }.map { s ->
            (if (s.playerId in goalieIds) "G " else "") + PixelFont.clean(s.name.substringAfterLast(' ')).take(9)
        }
        val left = names(snap.away); val right = names(snap.home)
        for (i in 0 until maxOf(left.size, right.size)) {
            val y = HEADER + 11 + i * 9
            if (y + 7 > rows) break
            left.getOrNull(i)?.let { g.text(it, 2, y, if (it.startsWith("G ")) HockeyBoard.MUTED else ai) }
            right.getOrNull(i)?.let { g.textRight(it, COLS - 3, y, if (it.startsWith("G ")) HockeyBoard.MUTED else hi) }
        }
        return g
    }
}
