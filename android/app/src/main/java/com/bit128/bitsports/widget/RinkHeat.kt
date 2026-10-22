package com.bit128.bitsports.widget

import com.bit128.bitsports.nhl.ShotMark
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Shot locations binned onto an arena LED board: 25 x 11 cells over the NHL's -100..100 x
 * -42.5..42.5 rink. Each team is drawn attacking the end under its own name on the widget
 * (away left, home right), so a lit end reads as "that team's pressure".
 */
object RinkHeat {
    const val COLS = 25
    const val ROWS = 11
    const val LEVELS = 4
    /** A shot's own cell, and what spills into the four cells around it so pressure reads as a zone. */
    const val HIT = 2
    const val SPILL = 1
    private val CENTRE = colFor(0f)

    class Grid(val away: IntArray, val home: IntArray, val goals: BooleanArray) {
        fun index(col: Int, row: Int) = row * COLS + col
    }

    fun colFor(x: Float): Int = ((x + 100f) / 200f * COLS).toInt().coerceIn(0, COLS - 1)
    fun rowFor(y: Float): Int = ((42.5f - y) / 85f * ROWS).toInt().coerceIn(0, ROWS - 1)

    /** onlyTeam: draw just that team's shots ("just mine" in the widget settings). */
    fun grid(shots: List<ShotMark>, awayAbbrev: String, onlyTeam: String? = null): Grid {
        val away = IntArray(COLS * ROWS)
        val home = IntArray(COLS * ROWS)
        val goals = BooleanArray(COLS * ROWS)
        for (s in shots) {
            if (onlyTeam != null && s.teamAbbrev != onlyTeam) continue
            val isAway = s.teamAbbrev == awayAbbrev
            val x = if (isAway) -abs(s.x.toFloat()) else abs(s.x.toFloat())
            // Mirror y with x so a shot from the left circle stays on the left circle.
            val y = if ((s.x < 0) == isAway) s.y.toFloat() else -s.y.toFloat()
            val col = colFor(x)
            val row = rowFor(y)
            val heat = if (isAway) away else home
            heat[row * COLS + col] += HIT
            for ((c, r) in listOf(col - 1 to row, col + 1 to row, col to row - 1, col to row + 1)) {
                if (c !in 0 until COLS || r !in 0 until ROWS) continue
                if (if (isAway) c >= CENTRE else c <= CENTRE) continue // each team's heat stays in its own end
                heat[r * COLS + c] += SPILL
            }
            if (s.isGoal) goals[row * COLS + col] = true
        }
        return Grid(away, home, goals)
    }

    /** 0 = unlit, 1..LEVELS = brighter. Any shot lights at least level 1. */
    fun level(count: Int, max: Int): Int =
        if (count <= 0 || max <= 0) 0 else ceil(count.toFloat() / max * LEVELS).toInt().coerceIn(1, LEVELS)
}
