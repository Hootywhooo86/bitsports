package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.ShotMark
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Shot pressure on the 128-pixel rink. Each team is drawn attacking the end under its own name
 * (away left, home right, like the widget's score line), so a lit end reads as "that team's
 * pressure". Every shot spreads a soft disc of heat; the total is banded into five pixel-art
 * levels of the team's colour, the hottest with a white-hot core. Goals are gold sparks ringed in
 * black, so they show on any team's colour, white-hot ice included.
 */
object HeatMap {
    /** Heat spreads this many pixels from a shot (about 11 ft). */
    const val RADIUS = 7f
    /** Band edges, as a fraction of the hottest pixel; below the first, the ice shows through. */
    private val BANDS = floatArrayOf(0.10f, 0.28f, 0.48f, 0.68f, 0.86f)
    private val STRENGTH = floatArrayOf(0.30f, 0.48f, 0.66f, 0.84f, 1f)

    data class Heat(val away: FloatArray, val home: FloatArray, val goals: List<Pair<Int, Int>>, val goalTeams: List<Boolean>)

    /** [onlyTeam]: just that team's shots ("just mine" in the widget settings). */
    fun heat(shots: List<ShotMark>, awayAbbrev: String, onlyTeam: String? = null): Heat {
        val w = PixelRink.W
        val h = PixelRink.H
        val away = FloatArray(w * h)
        val home = FloatArray(w * h)
        val goals = ArrayList<Pair<Int, Int>>()
        val goalTeams = ArrayList<Boolean>()
        val mid = w / 2
        for (s in shots) {
            if (onlyTeam != null && s.teamAbbrev != onlyTeam) continue
            val isAway = s.teamAbbrev == awayAbbrev
            val x = if (isAway) -abs(s.x.toFloat()) else abs(s.x.toFloat())
            // Mirror y with x so a shot from the left circle stays on the left circle.
            val y = if ((s.x < 0) == isAway) s.y.toFloat() else -s.y.toFloat()
            val (cx, cy) = PixelRink.at(x, y)
            val field = if (isAway) away else home
            val r = RADIUS.toInt()
            for (py in cy - r..cy + r) for (px in cx - r..cx + r) {
                if (px !in 0 until w || py !in 0 until h) continue
                if (if (isAway) px >= mid else px < mid) continue // each team's heat stays in its own end
                val d = sqrt(((px - cx) * (px - cx) + (py - cy) * (py - cy)).toFloat())
                // A soft, flat-topped falloff so nearby shots melt into one zone instead of rings.
                if (d <= RADIUS) field[py * w + px] += 1f - (d / (RADIUS + 1f)).let { it * it }
            }
            if (s.isGoal) { goals += cx to cy; goalTeams += isAway }
        }
        return Heat(away, home, goals, goalTeams)
    }

    /** 0 = no heat, 1..5 = hotter. */
    fun level(v: Float, max: Float): Int {
        if (v <= 0f || max <= 0f) return 0
        val f = v / max
        var l = 0
        for (b in BANDS) if (f >= b) l++
        return l
    }

    /** [inks]: the away and home LED colours, from [TeamInk.pair]. */
    fun draw(heat: Heat, inks: Pair<Int, Int>): LedGrid {
        val g = PixelRink.copy()
        val (awayLed, homeLed) = inks
        val awayMax = heat.away.maxOrNull() ?: 0f
        val homeMax = heat.home.maxOrNull() ?: 0f
        for (y in 0 until g.rows) for (x in 0 until g.cols) {
            val i = y * g.cols + x
            val base = g[x, y]
            if (base == PixelRink.OUTSIDE || base == PixelRink.BOARDS || base == PixelRink.NET) continue
            val a = level(heat.away[i], awayMax)
            val h = level(heat.home[i], homeMax)
            val (lvl, led) = if (a >= h) a to awayLed else h to homeLed
            if (lvl == 0) continue
            // Faint heat lets the rink's lines show through; from level 3 up the heat owns the pixel.
            if (lvl <= 2 && base != PixelRink.ICE) continue
            var c = blend(PixelRink.ICE, led, STRENGTH[lvl - 1])
            if (lvl == 5) c = blend(c, 0xFFFFFFFF.toInt(), 0.28f)
            g.put(x, y, c)
        }
        heat.goals.forEachIndexed { k, (cx, cy) -> goalMark(g, cx, cy, if (heat.goalTeams[k]) awayLed else homeLed) }
        return g
    }

    const val GOAL = 0xFFFFD95E.toInt()
    private val SPARK = listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1, -2 to 0, 2 to 0, 0 to -2, 0 to 2)
    private val CORNERS = listOf(-1 to -1, 1 to -1, -1 to 1, 1 to 1)

    /**
     * A goal: a gold plus with a white centre and the scoring team's colour in its corners, ringed
     * in black so it never melts into heat or shots of a similar colour.
     */
    fun goalMark(g: LedGrid, cx: Int, cy: Int, team: Int) {
        val mark = (SPARK + CORNERS).toSet()
        for ((dx, dy) in mark) for (ox in -1..1) for (oy in -1..1) {
            val p = dx + ox to dy + oy
            val x = cx + p.first
            val y = cy + p.second
            if (p in mark || x !in 0 until g.cols || y !in 0 until g.rows) continue
            if (g[x, y] != PixelRink.BOARDS) g.put(x, y, PixelRink.OUTSIDE)
        }
        for ((dx, dy) in CORNERS) g.put(cx + dx, cy + dy, team)
        for ((dx, dy) in SPARK) g.put(cx + dx, cy + dy, GOAL)
        g.put(cx, cy, HockeyBoard.WHITE)
    }

    fun blend(from: Int, to: Int, t: Float): Int {
        fun ch(v: Int, shift: Int) = v shr shift and 0xFF
        fun mix(shift: Int) = (ch(from, shift) + (ch(to, shift) - ch(from, shift)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
