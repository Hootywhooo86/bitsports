package com.bit128.bitsports.ui

import com.bit128.bitsports.alerts.AlertCopy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The full-screen GOAL moment as a 128 x 72 LED board, one frame per ~120 ms. Four styles, all in
 * the same LED letters as the rest of the app; the team and scorer always sit along the bottom.
 *
 *   lamp   the red goal light spins and flashes over GOAL!
 *   burst  rings of the team's colours burst out behind GOAL!
 *   puck   a puck slides into the net, the net shakes, GOAL!
 *   text   just the words, blinking between white and the team colour
 */
object GoalMoments {
    const val COLS = 128
    const val ROWS = 72
    /** Frames in one run of the moment (~2.6 s); previews loop them. */
    const val FRAMES = 22

    private const val LAMP = 0xFFFF2A3D.toInt()
    private const val LAMP_OFF = 0xFF3A1418.toInt()
    private const val NET = 0xFF6B6B6B.toInt()
    private const val MESH = 0xFF3A3A3A.toInt()
    private const val ICE = 0xFF1C2C44.toInt()
    private val PUCK = arrayOf("..kkkkk..", ".kkkkkkk.", "kkkkkkkkk", "sssssssss", ".sssssss.")

    /** [scorer] and [total] fill the bottom line; null scorer (a settings preview) shows just the team. */
    fun build(style: String, teamAbbrev: String, scorer: String?, total: Int?, frame: Int): LedGrid {
        val g = LedGrid(COLS, ROWS)
        val c = HockeyBoard.colors(teamAbbrev)
        val led = HockeyBoard.led(c)
        val f = frame.coerceAtLeast(0)
        when (style) {
            "burst" -> burst(g, c, f)
            "puck" -> puck(g, led, f)
            "text" -> g.textCenter("GOAL!", 20, if (f % 4 < 2) HockeyBoard.WHITE else led, 3)
            else -> lamp(g, led, f)
        }
        g.textCenter(PixelFont.clean(AlertCopy.nick(teamAbbrev)).take(PixelFont.fits(COLS)), 50, led)
        if (scorer != null) {
            val line = PixelFont.clean(scorer.substringAfterLast(' ')) + (total?.let { " ($it)" } ?: "")
            g.textCenter(line.take(PixelFont.fits(COLS)), 60, HockeyBoard.MUTED)
        }
        return g
    }

    private fun lamp(g: LedGrid, led: Int, f: Int) {
        val on = f % 4 < 2
        val dome = if (on) LAMP else LAMP_OFF
        g.fill(56, 2, 16, 2, dome); g.fill(54, 4, 20, 8, dome)
        if (on) g.fill(59, 5, 6, 4, HockeyBoard.WHITE)
        g.fill(50, 12, 28, 2, HockeyBoard.DIM)
        // Four beams sweeping round the dome.
        if (on) for (k in 0 until 4) {
            val a = (f * 0.5) + k * Math.PI / 2
            for (r in 14..26) {
                val x = 64 + (cos(a) * r).toInt()
                val y = 8 + (sin(a) * r * 0.45).toInt()
                if (y in 0..16 && (x < 50 || x > 77)) g.put(x, y, LAMP)
            }
        }
        g.textCenter("GOAL!", 20, if (on) led else HockeyBoard.WHITE, 3)
    }

    private fun burst(g: LedGrid, c: Pair<Int, Int>, f: Int) {
        // Rings expand from the centre, alternating the team's two colours.
        for (k in 0 until 3) {
            val r = ((f * 5 + k * 24) % 72).toDouble()
            val col = if (k % 2 == 0) c.first else c.second
            val steps = (r * 3).toInt().coerceAtLeast(12)
            for (i in 0 until steps) {
                val a = i * 2 * Math.PI / steps
                g.put(64 + (cos(a) * r).toInt(), 30 + (sin(a) * r * 0.6).toInt(), if (col == 0xFF000000.toInt()) HockeyBoard.DIM else col)
            }
        }
        // A dark plate behind the word so it always reads.
        g.fill(18, 17, 92, 27, 0xFF0D0D0D.toInt())
        g.textCenter("GOAL!", 20, if (f % 4 < 2) HockeyBoard.WHITE else HockeyBoard.led(c), 3)
    }

    private fun puck(g: LedGrid, led: Int, f: Int) {
        val travel = 10
        val hit = f >= travel
        val shake = if (hit && f < travel + 8) (if (f % 2 == 0) 2 else -2) else 0
        // The net: red posts and crossbar, white mesh, sitting on the ice line.
        val nx = 92 + shake
        g.fill(0, 46, COLS, 2, ICE)
        g.fill(nx, 16, 3, 30, LAMP); g.fill(nx, 16, 32, 3, LAMP); g.fill(nx + 29, 16, 3, 30, NET)
        for (y in 22 until 46 step 4) g.fill(nx + 3, y, 26, 1, MESH)
        for (x in nx + 6 until nx + 29 step 4) g.fill(x, 19, 1, 27, MESH)
        // The puck slides in along the ice, then sits in the twine.
        val px = if (hit) nx + 12 else 2 + (f * (nx + 6)) / travel
        g.sprite(PUCK, px, 41, mapOf('k' to HockeyBoard.WHITE, 's' to HockeyBoard.MUTED))
        // Speed lines behind it while it travels.
        if (!hit) for (k in 1..4) g.fill(px - k * 7, 42 + (k % 2) * 2, 4, 1, HockeyBoard.DIM)
        if (hit) g.text("GOAL!", 4, 6 + abs(shake) / 2, if (f % 4 < 2) led else HockeyBoard.WHITE, 3)
    }
}
