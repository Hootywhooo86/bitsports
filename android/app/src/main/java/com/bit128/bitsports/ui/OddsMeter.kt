package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.OddsSample
import kotlin.math.roundToInt

/**
 * The WIN-O-METER: the betting market as a 128-LED board.
 *
 *   EDM          TO WIN          NJD
 *   39%                          61%      big, in team colours
 *   ▬▬▬▬▬▬▬▬▬▬▬▬●▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬       tug of war: the puck slides toward the favourite
 *   ▁▂▃▅▆▅▃▂▁ ▔▔▀▀▔                         momentum: above the line the away team's chance rose,
 *                                          below it the home team's; gold dots are goals
 *
 * Pure, so the app and the widgets draw the same pixels and the spacing is tested.
 */
object OddsMeter {
    const val COLS = 128
    const val ROWS = 54
    private const val BAR_Y = 28
    private const val BAR_H = 5
    private const val CHART_TOP = 38
    private const val CHART_H = 15
    private const val TRACK = 0xFF2A2A2A.toInt()
    private const val AXIS = 0xFF3A3A3A.toInt()
    /** How far from even (in win chance) fills the momentum chart top to bottom. */
    private const val SWING = 0.20f

    fun board(
        away: String, home: String, awayInk: Int, homeInk: Int,
        awayShare: Float?, history: List<OddsSample>, live: Boolean, frame: Int = 0,
    ): LedGrid {
        val g = LedGrid(COLS, ROWS)
        g.text(away.take(3), 2, 1, awayInk)
        g.textRight(home.take(3), COLS - 3, 1, homeInk)
        // The middle label blinks gently while the market is live.
        val label = if (live) "LIVE ODDS" else "TO WIN"
        g.textCenter(label, 1, if (live && frame % 8 < 4) HockeyBoard.LIVE else HockeyBoard.MUTED)

        if (awayShare == null) {
            g.textCenter("NO LINE YET", 16, HockeyBoard.MUTED)
            return g
        }
        val a = (awayShare * 100).roundToInt().coerceIn(1, 99)
        val leadAway = a >= 50
        g.text("$a%", 2, 11, if (leadAway) awayInk else dimmed(awayInk), 2)
        g.textRight("${100 - a}%", COLS - 3, 11, if (a <= 50) homeInk else dimmed(homeInk), 2)

        // Tug of war, centre tick above and below.
        val left = 2
        val width = COLS - 4
        val split = left + (width * awayShare).roundToInt().coerceIn(2, width - 2)
        g.fill(left, BAR_Y, split - left, BAR_H, awayInk)
        g.fill(split, BAR_Y, left + width - split, BAR_H, homeInk)
        g.fill(COLS / 2, BAR_Y - 2, 1, 1, AXIS)
        g.fill(COLS / 2, BAR_Y + BAR_H + 1, 1, 1, AXIS)
        // The puck: white, with a black edge so it reads on any colour.
        g.fill(split - 2, BAR_Y - 1, 4, BAR_H + 2, 0xFF000000.toInt())
        g.fill(split - 1, BAR_Y, 2, BAR_H, HockeyBoard.WHITE)

        chart(g, history, awayInk, homeInk)
        return g
    }

    /** Momentum: one column per moment, filled up from the even line for the away side, down for home. */
    private fun chart(g: LedGrid, history: List<OddsSample>, awayInk: Int, homeInk: Int) {
        val mid = CHART_TOP + CHART_H / 2
        g.fill(2, mid, COLS - 4, 1, AXIS)
        if (history.size < 2) return
        val cols = COLS - 4
        // The whole game fits: squeeze when there are more samples than columns, stretch when fewer.
        val n = history.size
        val half = CHART_H / 2
        g.shape()
        for (c in 0 until cols) {
            val s = history[((c.toLong() * (n - 1)) / (cols - 1)).toInt()]
            // NHL win chances mostly sit between 30% and 70%, so ±20 points fills the chart: swings show.
            val lean = ((s.awayShare - 0.5f) / SWING).coerceIn(-1f, 1f)
            val h = (kotlin.math.abs(lean) * half).roundToInt().coerceAtLeast(if (lean != 0f) 1 else 0)
            val ink = if (lean > 0) awayInk else homeInk
            // A soft fill with a bright edge, so it reads as a line chart of the swing.
            for (k in 1..h) g.put(2 + c, if (lean > 0) mid - k else mid + k, if (k == h) ink else HeatMap.blend(0xFF000000.toInt(), ink, 0.45f))
        }
        // Goals: a gold dot on the scoring side's edge, at the moment the score changed.
        g.shape()
        for (i in 1 until n) {
            val prev = history[i - 1]
            val cur = history[i]
            val c = 2 + ((i.toLong() * (cols - 1)) / (n - 1)).toInt()
            if (cur.awayScore > prev.awayScore) { g.put(c, CHART_TOP - 1, HeatMap.GOAL); g.put(c + 1, CHART_TOP - 1, HeatMap.GOAL) }
            if (cur.homeScore > prev.homeScore) { g.put(c, CHART_TOP + CHART_H, HeatMap.GOAL); g.put(c + 1, CHART_TOP + CHART_H, HeatMap.GOAL) }
        }
    }

    /**
     * The widget's strip, 128 x 9: "EDM 39" bar "61 NJD". The bar's puck sits where the market is.
     */
    fun strip(away: String, home: String, awayInk: Int, homeInk: Int, awayShare: Float): LedGrid {
        val g = LedGrid(COLS, 9)
        val a = (awayShare * 100).roundToInt().coerceIn(1, 99)
        val l = "${away.take(3)} $a"
        val r = "${100 - a} ${home.take(3)}"
        g.text(l, 0, 1, awayInk)
        g.textRight(r, COLS - 1, 1, homeInk)
        val x0 = PixelFont.width(l) + 3
        val x1 = COLS - PixelFont.width(r) - 4
        val w = x1 - x0 + 1
        val split = x0 + (w * awayShare).roundToInt().coerceIn(2, w - 2)
        g.fill(x0, 3, split - x0, 3, awayInk)
        g.fill(split, 3, x1 - split + 1, 3, homeInk)
        g.fill(split - 1, 2, 2, 5, HockeyBoard.WHITE)
        return g
    }

    private fun dimmed(c: Int) = HeatMap.blend(0xFF000000.toInt(), c, 0.55f)
}
