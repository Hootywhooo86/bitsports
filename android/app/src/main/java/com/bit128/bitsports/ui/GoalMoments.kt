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
 *   shockwave  the old score shakes, bursts into team-colour pixels that rain down, and they
 *              settle into the new score
 *   celly  a little skater flies in, drops to one knee and slides, then raises the stick
 *   ripple the puck hits the twine and the back of the net bulges and wobbles
 *
 * Special goals take over whichever style you picked: hats pile up on a hat trick, the penalty
 * box door slams on a power-play goal, the goalie heads for the bench on an empty-netter, and
 * fireworks spell out a career milestone.
 */
object GoalMoments {
    const val COLS = 128
    const val ROWS = 72
    /** Frames in one run of the moment (~3.2 s); previews loop them. */
    const val FRAMES = 27

    private const val LAMP = 0xFFFF2A3D.toInt()
    private const val LAMP_OFF = 0xFF3A1418.toInt()
    private const val NET = 0xFF6B6B6B.toInt()
    private const val MESH = 0xFF3A3A3A.toInt()
    private const val ICE = 0xFF1C2C44.toInt()
    private val PUCK = arrayOf("..kkkkk..", ".kkkkkkk.", "kkkkkkkkk", "sssssssss", ".sssssss.")

    val STYLES = listOf("lamp", "burst", "puck", "text", "shockwave", "celly", "ripple")

    /**
     * [scorer] and [total] fill the bottom line; null scorer (a settings preview) shows just the
     * team. [before]/[after] are the score lines ("2-1") the shockwave blows up and rebuilds.
     */
    fun build(
        style: String, teamAbbrev: String, scorer: String?, total: Int?, frame: Int,
        special: GoalSpecial? = null, before: String? = null, after: String? = null,
    ): LedGrid {
        val g = LedGrid(COLS, ROWS)
        val c = HockeyBoard.colors(teamAbbrev)
        val led = TeamInk.of(teamAbbrev)
        val f = frame.coerceAtLeast(0)
        when {
            special != null -> special(g, special, led, c, f)
            style == "shockwave" -> shockwave(g, led, c, f, before ?: "1-1", after ?: "2-1")
            style == "celly" -> celly(g, led, f)
            style == "ripple" -> ripple(g, led, f)
            else -> classic(g, style, led, c, f)
        }
        g.textCenter(PixelFont.clean(AlertCopy.nick(teamAbbrev)).take(PixelFont.fits(COLS)), 50, led)
        if (scorer != null) {
            val line = PixelFont.clean(scorer.substringAfterLast(' ')) + (total?.let { " ($it)" } ?: "")
            g.textCenter(line.take(PixelFont.fits(COLS)), 60, HockeyBoard.MUTED)
        }
        return g
    }

    private fun classic(g: LedGrid, style: String, led: Int, c: Pair<Int, Int>, f: Int) {
        when (style) {
            "burst" -> burst(g, c, f)
            "puck" -> puck(g, led, f)
            "text" -> g.textCenter("GOAL!", 20, if (f % 4 < 2) HockeyBoard.WHITE else led, 3)
            else -> lamp(g, led, f)
        }
    }

    private fun lamp(g: LedGrid, led: Int, f: Int) {
        val on = f % 4 < 2
        val dome = if (on) LAMP else LAMP_OFF
        g.fill(56, 2, 16, 2, dome); g.fill(54, 4, 20, 8, dome)
        if (on) g.fill(59, 5, 6, 4, HockeyBoard.WHITE)
        g.fill(50, 12, 28, 2, HockeyBoard.DIM)
        // Four beams sweeping round the dome.
        g.shape()
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
        g.shape()
        for (k in 0 until 3) {
            val r = ((f * 5 + k * 24) % 72).toDouble()
            val col = if (k % 2 == 0) c.first else c.second
            val steps = (r * 3).toInt().coerceAtLeast(12)
            for (i in 0 until steps) {
                val a = i * 2 * Math.PI / steps
                g.put(64 + (cos(a) * r).toInt(), 30 + (sin(a) * r * 0.6).toInt(), if (col == 0xFF000000.toInt()) HockeyBoard.DIM else col)
            }
        }
        // Clear a margin round the word and the bottom lines so no ring ever touches a letter.
        g.fill(16, 17, 96, 27, 0)
        g.fill(0, 46, COLS, ROWS - 46, 0)
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

    // ---------- shockwave ----------

    private fun shockwave(g: LedGrid, led: Int, c: Pair<Int, Int>, f: Int, before: String, after: String) {
        val scale = 3
        val y = 14
        val colors = listOf(led, c.second.takeIf { it != 0xFF000000.toInt() } ?: HockeyBoard.WHITE, HockeyBoard.WHITE)
        fun x(s: String) = (COLS - PixelFont.width(s, scale)) / 2
        when {
            // The old score, shaking harder and harder.
            f < 6 -> {
                val shake = if (f % 2 == 0) f / 2 else -(f / 2)
                g.text(before, x(before) + shake, y + if (f > 3) (f % 3) - 1 else 0, HockeyBoard.WHITE, scale)
            }
            // It bursts: every pixel flies outward, then gravity takes them down as team-colour rain.
            f < 16 -> {
                val cells = PixelArt.textCells(before, x(before), y, scale)
                val t = (f - 6).toFloat()
                g.shape()
                cells.forEachIndexed { i, (cx, cy) ->
                    val vx = (PixelArt.rand(i * 3) - 0.5f) * 9f
                    val vy = -PixelArt.rand(i * 3 + 1) * 6f - 1f
                    val px = cx + vx * t
                    val py = cy + vy * t + 0.55f * t * t
                    if (py < 46) g.put(px.toInt(), py.toInt(), colors[i % colors.size])
                }
            }
            // The rain gathers back up into the new score.
            f < 22 -> {
                val targets = PixelArt.textCells(after, x(after), y, scale)
                g.shape()
                val t = (f - 16) / 5f
                targets.forEachIndexed { i, (tx, ty) ->
                    val sx = PixelArt.rand(i * 5 + 2) * COLS
                    val sy = 44f
                    val e = 1f - (1f - t).let { it * it }
                    g.put((sx + (tx - sx) * e).toInt(), (sy + (ty - sy) * e).toInt(), colors[i % colors.size])
                }
            }
            else -> {
                g.text(after, x(after), y, if (f % 4 < 2) led else HockeyBoard.WHITE, scale)
                if (f < 26) {
                    // A last ring of sparks as it locks in.
                    g.shape()
                    val r = (f - 21) * 6
                    for (k in 0 until 24) {
                        val a = k * Math.PI / 12
                        g.put(64 + (cos(a) * (r + 30)).toInt(), 24 + (sin(a) * (r + 30) * 0.4).toInt(), led)
                    }
                    g.fill(0, 9, COLS, 31, 0)
                    g.text(after, x(after), y, led, scale)
                }
            }
        }
        g.fill(0, 46, COLS, 4, 0)
    }

    // ---------- celly ----------

    private fun celly(g: LedGrid, led: Int, f: Int) {
        val pal = mapOf('j' to led, 'h' to HockeyBoard.WHITE, 'p' to PixelArt.PANTS.let { if (led == it) HockeyBoard.DIM else it },
            'k' to PixelArt.SKATE, 's' to PixelArt.STICK)
        g.fill(0, 45, COLS, 1, PixelArt.ICE_LINE)
        when {
            // Flying in on the right foot, at double size.
            f < 8 -> {
                val x = -28 + f * 10
                g.sprite(PixelArt.SKATER, x, 13, pal, 2)
                g.shape()
                for (k in 1..4) g.fill(x - k * 7, 40 - (k % 2) * 3, 4, 1, HockeyBoard.DIM)
            }
            // Down on one knee, sliding to a stop, spraying ice.
            f < 15 -> {
                val x = 46 + (15 - f) * 3 - 6
                g.sprite(PixelArt.KNEE_SLIDE, x, 21, pal, 2)
                g.shape()
                for (k in 0 until 8) g.put(x - 3 - k * 3, 43 - (k * 7 + f) % 5, 0xFFBFE3FF.toInt())
            }
            // Up, stick raised to the crowd, GOAL! beside him.
            else -> {
                g.sprite(PixelArt.STICK_UP, 74, 1, pal, 2)
                if (f % 4 < 2) {
                    g.shape()
                    for ((dx, dy) in listOf(-6 to 8, 32 to 6, -4 to 30, 34 to 26, 30 to 40)) g.put(74 + dx, 1 + dy, HockeyBoard.WARN)
                }
                g.text("GOAL!", 6, 14, if (f % 4 < 2) led else HockeyBoard.WHITE, 2)
            }
        }
    }

    // ---------- net ripple ----------

    private fun ripple(g: LedGrid, led: Int, f: Int) {
        val left = 26; val right = 100; val top = 2; val bottom = 30
        val hit = 5
        val mesh = 0xFF8A8F96.toInt()
        val cx = 64; val cy = 17
        // The mesh: strands every 4 LEDs, each point pushed out from where the puck hit by a
        // fading wave, so the twine bulges and wobbles.
        fun bend(x: Int, y: Int): Pair<Int, Int> {
            if (f < hit) return x to y
            val d = kotlin.math.hypot((x - cx).toDouble(), (y - cy) * 1.6)
            val t = f - hit
            val amp = 5.0 * kotlin.math.exp(-t / 7.0)
            val w = amp * sin(d / 5.0 - t * 1.1) * kotlin.math.exp(-d / 40.0)
            return (x + w * (x - cx) / (d + 1)).toInt() to (y + w * (y - cy) / (d + 1) + if (d < 8) amp * 0.6 else 0.0).toInt()
        }
        g.shape()
        for (y in top + 4 until bottom step 4) for (x in left + 3 until right) { val (bx, by) = bend(x, y); g.put(bx, by.coerceIn(top + 2, bottom - 1), mesh) }
        for (x in left + 5 until right step 4) for (y in top + 2 until bottom) { val (bx, by) = bend(x, y); g.put(bx.coerceIn(left + 3, right - 1), by.coerceIn(top + 2, bottom - 1), mesh) }
        // Posts and crossbar.
        g.fill(left, top, 3, bottom - top, LAMP); g.fill(left, top, right - left + 3, 2, LAMP); g.fill(right, top, 3, bottom - top, LAMP)
        g.fill(left - 4, bottom, right - left + 11, 1, PixelArt.ICE_LINE)
        // The puck: flies at the camera, buries in the twine, drops to the ice.
        if (f < hit) {
            val s = 1 + f
            g.fill(64 - s, 22 - s / 2, s * 2, s, HockeyBoard.WHITE)
        } else {
            val py = if (f < hit + 8) cy + (f - hit) else bottom - 3
            g.fill(61, py, 6, 2, HockeyBoard.WHITE)
        }
        if (f >= hit + 2) g.textCenter("GOAL!", 34, if (f % 4 < 2) led else HockeyBoard.WHITE, 2)
    }

    // ---------- specials ----------

    private fun special(g: LedGrid, s: GoalSpecial, led: Int, c: Pair<Int, Int>, f: Int) = when (s) {
        is GoalSpecial.HatTrick -> hatTrick(g, led, c, f, s.goals)
        is GoalSpecial.PowerPlay -> powerPlay(g, led, f)
        is GoalSpecial.EmptyNet -> emptyNet(g, led, f)
        is GoalSpecial.Milestone -> milestone(g, led, c, f, s.label)
    }

    private val CAP_COLORS = listOf(0xFFE8283C.toInt(), 0xFF3A6FE8.toInt(), 0xFFF0F0F0.toInt(), 0xFF2E9E5E.toInt(), 0xFFFFB81C.toInt(), 0xFF8A6CD8.toInt())

    /** Caps rain onto the ice and pile up; the more goals, the more hats. */
    private fun hatTrick(g: LedGrid, led: Int, c: Pair<Int, Int>, f: Int, goals: Int) {
        val n = 14 + (goals - 3) * 8
        val floor = 45
        val heights = IntArray(COLS)
        for (i in 0 until n) {
            val x = (PixelArt.rand(i * 11 + 3) * (COLS - 9)).toInt()
            val start = (PixelArt.rand(i * 11 + 5) * 14).toInt()
            val speed = 3 + (PixelArt.rand(i * 11 + 7) * 3).toInt()
            val col = if (i % 4 == 0) led else CAP_COLORS[i % CAP_COLORS.size]
            val landAt = floor - 4 - (heights.slice(x until x + 9).maxOrNull() ?: 0)
            val y = if (f < start) continue else minOf(-4 + (f - start) * speed, landAt)
            if (y == landAt) for (k in x until x + 9) heights[k] = floor - y
            g.sprite(PixelArt.CAP, x, y, mapOf('c' to col, 'b' to HeatMap.blend(0xFF000000.toInt(), col, 0.6f)))
        }
        val label = if (goals == 3) "HAT TRICK!" else "$goals GOALS!"
        PixelArt.clearForCenter(g, label, 4, 2)
        g.textCenter(label, 4, if (f % 4 < 2) HockeyBoard.WARN else HockeyBoard.WHITE, 2)
        g.fill(0, 46, COLS, 4, 0)
    }

    /** The penalty box lights up, the door slams shut on the man advantage. */
    private fun powerPlay(g: LedGrid, led: Int, f: Int) {
        val bx = 34; val by = 2; val bw = 60; val bh = 30
        val on = f % 4 < 2
        val frame = if (f < 10) (if (on) HockeyBoard.WARN else HockeyBoard.DIM) else HockeyBoard.MUTED
        g.fill(bx, by, bw, 2, frame); g.fill(bx, by + bh - 2, bw, 2, frame); g.fill(bx, by, 2, bh, frame)
        // The door swings shut on the right side, then shakes.
        val open = (12 - f).coerceIn(0, 12)
        val shake = if (f in 12..15) (if (f % 2 == 0) 1 else -1) else 0
        g.fill(bx + bw - 2 + open + shake, by, 2, bh, if (f >= 12) LAMP else frame)
        g.text("PP", bx + 11, by + 6, if (f < 10 && !on) HockeyBoard.DIM else led, 3)
        if (f >= 12) g.textCenter("POWER PLAY GOAL", 36, if (on) led else HockeyBoard.WHITE)
    }

    /** The goalie heads for the bench as the puck trickles into the open net; EN blinks. */
    private fun emptyNet(g: LedGrid, led: Int, f: Int) {
        g.fill(0, 44, COLS, 1, PixelArt.ICE_LINE)
        // The net on the right, empty.
        g.fill(104, 26, 2, 18, LAMP); g.fill(104, 26, 20, 2, LAMP)
        g.shape(); for (y in 30 until 44 step 3) for (x in 108 until 124 step 3) g.put(x, y, MESH)
        // The goalie skates off to the left, toward the bench.
        val gx = 86 - f * 7
        if (gx > -16) g.sprite(PixelArt.GOALIE, gx, 30, mapOf('g' to HockeyBoard.MUTED, 'h' to HockeyBoard.WHITE, 'p' to PixelArt.PANTS, 'k' to PixelArt.SKATE, 'b' to HockeyBoard.DIM))
        // The puck, slow and lonely, into the twine.
        val px = (20 + f * 5).coerceAtMost(110)
        g.fill(px, 41, 4, 2, HockeyBoard.WHITE)
        g.text("EN", 4, 4, if (f % 4 < 2) led else HockeyBoard.DIM, 3)
        if (px >= 110) g.textRight("GOAL!", COLS - 5, 6, if (f % 4 < 2) led else HockeyBoard.WHITE, 2)
    }

    /** Fireworks burst, then their sparks fly together to spell the milestone. */
    private fun milestone(g: LedGrid, led: Int, c: Pair<Int, Int>, f: Int, label: String) {
        val scale = if (PixelFont.width(label, 2) <= COLS - 6) 2 else 1
        val lx = (COLS - PixelFont.width(label, scale)) / 2
        val ly = 18
        val colors = listOf(led, HockeyBoard.WARN, 0xFF4AB8FF.toInt(), 0xFFE05CC0.toInt(), HockeyBoard.WHITE)
        // Background bursts all the way through.
        g.shape()
        for (b in 0 until 4) {
            val t0 = b * 5
            val age = f - t0
            if (age !in 0..9) continue
            val bx = 16 + (PixelArt.rand(b * 7) * 96).toInt()
            val byy = 6 + (PixelArt.rand(b * 7 + 1) * 30).toInt()
            for (k in 0 until 16) {
                val a = k * Math.PI / 8
                val r = age * 2.2
                g.put(bx + (cos(a) * r).toInt(), byy + (sin(a) * r * 0.7).toInt() + age / 3, colors[(b + k) % colors.size])
            }
        }
        val targets = PixelArt.textCells(label, lx, ly, scale)
        if (f < 14) {
            if (f >= 4) PixelArt.converge(g, targets, 64, 40, (f - 4) / 10f, colors)
        } else {
            PixelArt.clearFor(g, label, lx, ly, scale)
            g.text(label, lx, ly, if (f % 4 < 2) HockeyBoard.WARN else led, scale)
        }
        g.fill(0, 46, COLS, 4, 0)
    }
}
