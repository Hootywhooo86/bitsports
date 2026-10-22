package com.bit128.bitsports.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.bit128.bitsports.nhl.NhlTeam

/**
 * Draws the heat map as an arena LED board: unlit cells for the ice, dim cells for the
 * red, blue and goal lines, each team's shots lit in its own colour, goals lit white.
 * "solid" closes the gaps between cells for a flat pixel look instead.
 */
object RinkArt {
    private const val CELL = 24
    private const val GAP = 4

    private const val GROUND = 0xFF0D0D0D.toInt() // the widget card, so gaps vanish into it
    private const val UNLIT = 0xFF1A1A1A.toInt()
    private const val RED_LINE = 0xFF3A1A22.toInt()
    private const val BLUE_LINE = 0xFF1A2433.toInt()
    private const val GOAL_LINE = 0xFF2E1A1F.toInt()
    private const val NET = 0xFF4A4A4A.toInt()
    private const val GOAL = 0xFFFFFFFF.toInt()
    private val STRENGTH = floatArrayOf(0f, 0.3f, 0.5f, 0.75f, 1f)

    private val CENTRE = RinkHeat.colFor(0f)
    private val BLUE = setOf(RinkHeat.colFor(-25f), RinkHeat.colFor(25f))
    private val GOALS = setOf(RinkHeat.colFor(-89f), RinkHeat.colFor(89f))

    fun render(grid: RinkHeat.Grid, away: NhlTeam?, home: NhlTeam?, solid: Boolean): Bitmap {
        val gap = if (solid) 0 else GAP
        val bmp = Bitmap.createBitmap(RinkHeat.COLS * CELL, RinkHeat.ROWS * CELL, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(GROUND)
        val paint = Paint()
        val awayLed = ledColor(away)
        val homeLed = ledColor(home)
        val awayMax = grid.away.maxOrNull() ?: 0
        val homeMax = grid.home.maxOrNull() ?: 0
        for (row in 0 until RinkHeat.ROWS) for (col in 0 until RinkHeat.COLS) {
            val i = grid.index(col, row)
            val a = RinkHeat.level(grid.away[i], awayMax)
            val h = RinkHeat.level(grid.home[i], homeMax)
            paint.color = when {
                grid.goals[i] -> GOAL
                a > 0 || h > 0 -> if (a >= h) blend(UNLIT, awayLed, STRENGTH[a]) else blend(UNLIT, homeLed, STRENGTH[h])
                else -> markings(col, row)
            }
            val x = (col * CELL).toFloat()
            val y = (row * CELL).toFloat()
            c.drawRect(x + gap / 2f, y + gap / 2f, x + CELL - gap / 2f, y + CELL - gap / 2f, paint)
        }
        return bmp
    }

    private fun markings(col: Int, row: Int): Int = when {
        col in GOALS && row == RinkHeat.ROWS / 2 -> NET
        col == CENTRE -> RED_LINE
        col in BLUE -> BLUE_LINE
        col in GOALS -> GOAL_LINE
        else -> UNLIT
    }

    /**
     * The colour a team lights up in: its primary when that reads on black, else its secondary,
     * else the primary lifted toward white. Never white itself, which means a goal.
     */
    fun ledColor(team: NhlTeam?): Int {
        team ?: return 0xFF8C8C8C.toInt()
        fun bright(c: Long): Float {
            val r = (c shr 16 and 0xFF) / 255f; val g = (c shr 8 and 0xFF) / 255f; val b = (c and 0xFF) / 255f
            return 0.299f * r + 0.587f * g + 0.114f * b
        }
        fun ok(c: Long) = bright(c) in 0.22f..0.88f
        val (p, s) = team.colors
        return when {
            ok(p) -> p.toInt()
            ok(s) -> s.toInt()
            else -> blend(p.toInt(), 0xFFFFFFFF.toInt(), 0.45f)
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun ch(v: Int, shift: Int) = v shr shift and 0xFF
        fun mix(shift: Int) = (ch(from, shift) + (ch(to, shift) - ch(from, shift)) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
    }
}
