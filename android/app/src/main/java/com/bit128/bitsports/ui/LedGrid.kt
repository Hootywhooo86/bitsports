package com.bit128.bitsports.ui

/**
 * A board of [cols] x [rows] LEDs, each lit with an ARGB colour or off (0). Pure Kotlin so the
 * app (Compose) and the home-screen widgets (Bitmaps) draw exactly the same pixels.
 */
class LedGrid(val cols: Int, val rows: Int) {
    val cells = IntArray(cols * rows)

    operator fun get(x: Int, y: Int) = cells[y * cols + x]

    fun put(x: Int, y: Int, argb: Int) {
        if (x in 0 until cols && y in 0 until rows) cells[y * cols + x] = argb
    }

    fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        for (yy in y until y + h) for (xx in x until x + w) put(xx, yy, argb)
    }

    fun text(s: String, x: Int, y: Int, argb: Int, scale: Int = 1) =
        PixelFont.cells(s, x, y, scale) { cx, cy -> put(cx, cy, argb) }

    /** Right-aligned so the last letter ends at [right] (inclusive). */
    fun textRight(s: String, right: Int, y: Int, argb: Int, scale: Int = 1) =
        text(s, right - PixelFont.width(s, scale) + 1, y, argb, scale)

    fun textCenter(s: String, y: Int, argb: Int, scale: Int = 1) =
        text(s, (cols - PixelFont.width(s, scale)) / 2, y, argb, scale)

    /** [art] rows of palette keys; '.' is off. */
    fun sprite(art: Array<String>, x: Int, y: Int, palette: Map<Char, Int>) {
        art.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch -> palette[ch]?.let { put(x + c, y + r, it) } }
        }
    }
}

object Sprites {
    /** The intermission Zamboni, 18x8, driving right. b = body, w = window, d = dark trim, t = tyre, i = fresh ice. */
    val ZAMBONI = arrayOf(
        "......wwww........",
        "......w..w........",
        "..bbbbbbbbbbbbbb..",
        "..bbbbbbbbbbbbbbb.",
        "..bbbddddddddbbbbb",
        "..bbbbbbbbbbbbbbbb",
        "iiiittiiiiiiittiii",
        "iiiittiiiiiiittiii",
    )
    const val ZAMBONI_W = 18

    /** A puck, 4x2. */
    val PUCK = arrayOf(".kk.", "kkkk")
}
