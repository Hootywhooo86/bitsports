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

    val base: LedGrid by lazy { render(W, H) }

    fun copy(): LedGrid = LedGrid(W, H).also { base.cells.copyInto(it.cells) }

    /**
     * The rink at any size: [W] x [H] for the LED boards, or several times that for the smooth
     * widget, where lines stay thin and circles round instead of growing chunky.
     */
    fun render(w: Int, h: Int): LedGrid {
        val g = LedGrid(w, h)
        val k = w / W.toFloat()
        fun col(x: Float) = ((x + 100f) / 200f * w).toInt().coerceIn(0, w - 1)
        fun row(y: Float) = ((42.5f - y) / 85f * h).toInt().coerceIn(0, h - 1)
        fun px(n: Int) = maxOf(1, (n * k).toInt())
        // Ice inside rounded boards (28 ft corner radius).
        val r = 28f
        for (yy in 0 until h) for (xx in 0 until w) {
            val fx = (xx + 0.5f) / w * 200f - 100f
            val fy = 42.5f - (yy + 0.5f) / h * 85f
            val cx = abs(fx) - (100f - r)
            val cy = abs(fy) - (42.5f - r)
            val inside = cx <= 0f || cy <= 0f || sqrt(cx * cx + cy * cy) <= r
            g.put(xx, yy, if (inside) ICE else OUTSIDE)
        }
        // Boards: ice pixels within the board width of the outside.
        val bw = px(1)
        val edge = ArrayList<Pair<Int, Int>>()
        for (yy in 0 until h) for (xx in 0 until w) {
            if (g[xx, yy] != ICE) continue
            var out = false
            loop@ for (d in 1..bw) for ((dx, dy) in listOf(-d to 0, d to 0, 0 to -d, 0 to d)) {
                val nx = xx + dx; val ny = yy + dy
                if (nx !in 0 until w || ny !in 0 until h || g[nx, ny] == OUTSIDE) { out = true; break@loop }
            }
            if (out) edge += xx to yy
        }
        edge.forEach { (xx, yy) -> g.put(xx, yy, BOARDS) }

        fun vline(x: Float, color: Int, width: Int = 1) {
            val c = col(x) - (px(width) - width).coerceAtLeast(0) / 2
            for (yy in 0 until h) for (i in 0 until px(width)) if (c + i in 0 until w && g[c + i, yy] == ICE) g.put(c + i, yy, color)
        }
        vline(0f, RED, 2)
        vline(-25f, BLUE, 2); vline(25f, BLUE, 2)
        vline(-89f, RED); vline(89f, RED)

        // Circles first so the dots sit on top.
        fun circle(x: Float, y: Float, radiusFt: Float, color: Int) {
            val steps = (90 * k).toInt().coerceAtLeast(90) * 2
            for (t in 0 until px(1)) for (i in 0 until steps) {
                val a = i * 2 * Math.PI / steps
                val rr = radiusFt - t * 200f / w
                val cx = col(x + (rr * kotlin.math.cos(a)).toFloat())
                val cy = row(y + (rr * kotlin.math.sin(a)).toFloat())
                if (g[cx, cy] == ICE) g.put(cx, cy, color)
            }
        }
        circle(0f, 0f, 15f, BLUE)
        for (sx in listOf(-69f, 69f)) for (sy in listOf(-22f, 22f)) circle(sx, sy, 15f, RED)
        // Creases: a small blue half-disc in front of each net.
        for (side in listOf(-1f, 1f)) {
            for (yy in 0 until h) for (xx in 0 until w) {
                val fx = (xx + 0.5f) / w * 200f - 100f
                val fy = 42.5f - (yy + 0.5f) / h * 85f
                val dx = (89f - abs(fx))
                if (fx * side > 0 && dx in 0f..6f && abs(fy) <= 4f && g[xx, yy] == ICE) g.put(xx, yy, CREASE)
            }
        }
        // Faceoff dots: centre, four neutral-zone, four end-zone.
        val dots = listOf(0f to 0f) + listOf(-20f, 20f, -69f, 69f).flatMap { x -> listOf(x to -22f, x to 22f) }
        val ds = px(2)
        dots.forEach { (x, y) -> g.fill(col(x) - (ds - 2).coerceAtLeast(0) / 2, row(y) - ds / 2, ds, ds, if (x == 0f) BLUE else RED) }
        // Nets behind each goal line.
        val nw = px(3)
        g.fill(col(-89f) - nw, row(3f), nw, row(-3f) - row(3f) + 1, NET)
        g.fill(col(89f) + px(1), row(3f), nw, row(-3f) - row(3f) + 1, NET)
        return g
    }

    /** Pixel position (in this grid) of an NHL coordinate, rounded. */
    fun at(x: Float, y: Float): Pair<Int, Int> =
        ((x + 100f) / 200f * W).roundToInt().coerceIn(1, W - 2) to ((42.5f - y) / 85f * H).roundToInt().coerceIn(1, H - 2)
}
