package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.Chances
import com.bit128.bitsports.nhl.Tape
import com.bit128.bitsports.nhl.WinProb
import com.bit128.bitsports.nhl.XgShot
import kotlin.math.roundToInt

/** The analytics charts as LED boards, 128 across. Pure, so their spacing is tested. */
object AnalyticsLeds {
    const val COLS = 128
    private const val AXIS = 0xFF3A3A3A.toInt()
    private const val GRID = 0xFF242424.toInt()
    private fun dim(c: Int, t: Float = 0.4f) = HeatMap.blend(0xFF000000.toInt(), c, t)

    private fun periodLines(g: LedGrid, top: Int, bottom: Int, totalSec: Int) {
        for (p in 1 until (totalSec / 1200).coerceAtLeast(3)) {
            val x = (p * 1200L * (COLS - 1) / totalSec).toInt()
            for (y in top..bottom step 2) g.put(x, y, GRID)
        }
    }

    /**
     * Win probability over the game for [mineHome]'s side: filled above the even line in [mine]'s
     * colour while they're favoured, below in [theirs] while they're not; gold dots at goals.
     */
    fun winProb(timeline: List<WinProb.Point>, mineHome: Boolean, mine: Int, theirs: Int, rows: Int = 40, totalSec: Int = 3600): LedGrid {
        val g = LedGrid(COLS, rows)
        val mid = rows / 2
        g.shape()
        periodLines(g, 0, rows - 1, totalSec)
        for (x in 0 until COLS step 2) g.put(x, mid, AXIS)
        if (timeline.isEmpty()) return g
        val lastX = (timeline.last().second.toLong() * (COLS - 1) / totalSec).toInt().coerceAtMost(COLS - 1)
        for (x in 0..lastX) {
            val sec = (x.toLong() * totalSec / (COLS - 1)).toInt()
            val p = timeline.lastOrNull { it.second <= sec } ?: timeline.first()
            val v = if (mineHome) p.home else 1 - p.home
            val y = ((1 - v) * (rows - 1)).roundToInt()
            val ink = if (v >= 0.5) mine else theirs
            for (yy in minOf(y, mid)..maxOf(y, mid)) g.put(x, yy, if (yy == y) ink else dim(ink, 0.35f))
        }
        // Goals: where the score changed.
        timeline.zipWithNext().forEach { (a, b) ->
            if (a.awayScore != b.awayScore || a.homeScore != b.homeScore) {
                val x = (b.second.toLong() * (COLS - 1) / totalSec).toInt()
                val v = if (mineHome) b.home else 1 - b.home
                val y = ((1 - v) * (rows - 1)).roundToInt()
                for ((dx, dy) in listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1)) g.put(x + dx - 1, y + dy - 1, PixelArt.GOLD)
            }
        }
        return g
    }

    /** Both sides' expected goals adding up through the game, as two stepped lines. */
    fun xgRace(c: Chances, away: Int, home: Int, rows: Int = 36, totalSec: Int = 3600): LedGrid {
        val g = LedGrid(COLS, rows)
        g.shape()
        periodLines(g, 0, rows - 1, totalSec)
        val top = maxOf(c.awayXg, c.homeXg, 1.0)
        for (x in 0 until COLS) {
            val sec = (x.toLong() * totalSec / (COLS - 1)).toInt()
            val p = c.race.lastOrNull { it.first <= sec } ?: continue
            if (p.first > c.race.last().first) break
            if (sec > c.race.last().first + 60) break
            g.put(x, (rows - 1 - p.second / top * (rows - 2)).roundToInt(), away)
            g.put(x, (rows - 1 - p.third / top * (rows - 2)).roundToInt(), home)
        }
        return g
    }

    /** Pressure: expected goals in each 2-minute stretch, [mine] up and the other side down. */
    fun pressure(shots: List<XgShot>, mineHome: Boolean, mine: Int, theirs: Int, rows: Int = 24, totalSec: Int = 3600): LedGrid {
        val g = LedGrid(COLS, rows)
        val bins = totalSec / 120
        val up = DoubleArray(bins); val down = DoubleArray(bins)
        shots.forEach { s ->
            val i = (Chances.gameSecond(s.period, s.second) / 120).coerceIn(0, bins - 1)
            if (s.home == mineHome) up[i] += s.xg else down[i] += s.xg
        }
        val mid = rows / 2
        g.shape()
        for (x in 0 until COLS step 2) g.put(x, mid, AXIS)
        val w = COLS / bins
        val scale = (maxOf(up.maxOrNull() ?: 0.0, down.maxOrNull() ?: 0.0, 0.3))
        for (i in 0 until bins) {
            val hu = (up[i] / scale * (mid - 1)).roundToInt()
            val hd = (down[i] / scale * (mid - 1)).roundToInt()
            for (k in 1..hu) for (dx in 0 until w - 1) g.put(i * w + dx, mid - k, mine)
            for (k in 1..hd) for (dx in 0 until w - 1) g.put(i * w + dx, mid + k, theirs)
        }
        return g
    }

    /**
     * One row of the tape: a track from behind to ahead with each team's marker on it, placed
     * by where its value sits between the league's lowest and highest.
     */
    fun tapeTrack(away: Double, home: Double, lo: Double, hi: Double, awayInk: Int, homeInk: Int): LedGrid {
        val g = LedGrid(COLS, 7)
        g.fill(2, 3, COLS - 4, 1, AXIS)
        g.fill(COLS / 2, 1, 1, 5, AXIS)
        fun x(v: Double) = (2 + ((v - lo) / (hi - lo).coerceAtLeast(1e-9)).coerceIn(0.0, 1.0) * (COLS - 9)).roundToInt()
        g.fill(x(away), 0, 5, 7, awayInk)
        g.fill(x(home), 1, 5, 5, homeInk)
        return g
    }

    /** The league's range for one tape number. */
    fun range(all: Collection<Tape>, sel: (Tape) -> Double): Pair<Double, Double> =
        (all.minOfOrNull(sel) ?: 0.0) to (all.maxOfOrNull(sel) ?: 1.0)
}
