package com.bit128.bitsports.ui

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The rink as 128 x 54 pixels over the NHL's 200 x 85 ft (-100..100, -42.5..42.5) coordinates:
 * rounded boards, red and blue lines, goal lines and creases, nets, all nine faceoff dots and
 * the five circles. Pure Kotlin; the shot map and on-ice view paint on top of [base].
 */
object PixelRink {
    const val W = 128
    const val H = 54

    const val OUTSIDE = 0xFF000000.toInt()
    const val ICE = 0xFF141414.toInt()
    const val BOARDS = 0xFF3A3A3A.toInt()
    const val RED = 0xFF4A1C26.toInt()
    const val BLUE = 0xFF1C2C44.toInt()
    const val CREASE = 0xFF16263A.toInt()
    const val NET = 0xFF6B6B6B.toInt()

    fun col(x: Float) = ((x + 100f) / 200f * W).toInt().coerceIn(0, W - 1)
    fun row(y: Float) = ((42.5f - y) / 85f * H).toInt().coerceIn(0, H - 1)

    val base: LedGrid by lazy { draw() }

    fun copy(): LedGrid = LedGrid(W, H).also { base.cells.copyInto(it.cells) }

    private fun draw(): LedGrid {
        val g = LedGrid(W, H)
        // Ice inside rounded boards (28 ft corner radius).
        val r = 28f
        for (yy in 0 until H) for (xx in 0 until W) {
            val fx = (xx + 0.5f) / W * 200f - 100f
            val fy = 42.5f - (yy + 0.5f) / H * 85f
            val cx = abs(fx) - (100f - r)
            val cy = abs(fy) - (42.5f - r)
            val inside = cx <= 0f || cy <= 0f || sqrt(cx * cx + cy * cy) <= r
            g.put(xx, yy, if (inside) ICE else OUTSIDE)
        }
        // Boards: any ice pixel touching the outside.
        val edge = ArrayList<Pair<Int, Int>>()
        for (yy in 0 until H) for (xx in 0 until W) {
            if (g[xx, yy] != ICE) continue
            val out = xx == 0 || yy == 0 || xx == W - 1 || yy == H - 1 ||
                g[xx - 1, yy] == OUTSIDE || g[xx + 1, yy] == OUTSIDE || g[xx, yy - 1] == OUTSIDE || g[xx, yy + 1] == OUTSIDE
            if (out) edge += xx to yy
        }
        edge.forEach { (xx, yy) -> g.put(xx, yy, BOARDS) }

        fun vline(x: Float, color: Int, width: Int = 1) {
            val c = col(x)
            for (yy in 0 until H) for (w in 0 until width) if (g[c + w, yy] == ICE) g.put(c + w, yy, color)
        }
        vline(0f, RED, 2)
        vline(-25f, BLUE, 2); vline(25f, BLUE, 2)
        vline(-89f, RED); vline(89f, RED)

        // Circles first so the dots sit on top.
        circle(g, 0f, 0f, 15f, BLUE)
        for (sx in listOf(-69f, 69f)) for (sy in listOf(-22f, 22f)) circle(g, sx, sy, 15f, RED)
        // Creases: a small blue half-disc in front of each net.
        for (side in listOf(-1f, 1f)) {
            for (yy in 0 until H) for (xx in 0 until W) {
                val fx = (xx + 0.5f) / W * 200f - 100f
                val fy = 42.5f - (yy + 0.5f) / H * 85f
                val dx = (89f - abs(fx))
                if (fx * side > 0 && dx in 0f..6f && abs(fy) <= 4f && g[xx, yy] == ICE) g.put(xx, yy, CREASE)
            }
        }
        // Faceoff dots: centre, four neutral-zone, four end-zone. 2x2 each.
        val dots = listOf(0f to 0f) + listOf(-20f, 20f, -69f, 69f).flatMap { x -> listOf(x to -22f, x to 22f) }
        dots.forEach { (x, y) -> g.fill(col(x), row(y) - 1, 2, 2, if (x == 0f) BLUE else RED) }
        // Nets behind each goal line.
        g.fill(col(-89f) - 3, row(3f), 3, row(-3f) - row(3f) + 1, NET)
        g.fill(col(89f) + 1, row(3f), 3, row(-3f) - row(3f) + 1, NET)
        return g
    }

    private fun circle(g: LedGrid, x: Float, y: Float, radiusFt: Float, color: Int) {
        val steps = 90
        for (i in 0 until steps) {
            val a = i * 2 * Math.PI / steps
            val px = col(x + (radiusFt * kotlin.math.cos(a)).toFloat())
            val py = row(y + (radiusFt * kotlin.math.sin(a)).toFloat())
            if (g[px, py] == ICE) g.put(px, py, color)
        }
    }

    /** Pixel position (in this grid) of an NHL coordinate, rounded. */
    fun at(x: Float, y: Float): Pair<Int, Int> =
        ((x + 100f) / 200f * W).roundToInt().coerceIn(1, W - 2) to ((42.5f - y) / 85f * H).roundToInt().coerceIn(1, H - 2)
}
