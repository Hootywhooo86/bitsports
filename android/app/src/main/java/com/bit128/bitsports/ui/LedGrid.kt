package com.bit128.bitsports.ui

/**
 * A board of [cols] x [rows] LEDs, each lit with an ARGB colour or off (0). Pure Kotlin so the
 * app (Compose) and the home-screen widgets (Bitmaps) draw exactly the same pixels.
 */
class LedGrid(val cols: Int, val rows: Int) {
    val cells = IntArray(cols * rows)

    /**
     * Which drawing call lit each LED (0 = none), and which calls were text. Lets a test check
     * that no letter ever touches a line, stripe, bar or another piece of text.
     */
    val owner = IntArray(cols * rows)
    val textOwners = HashSet<Int>()
    private var current = 0
    private fun begin(isText: Boolean = false): Int { current++; if (isText) textOwners += current; return current }

    operator fun get(x: Int, y: Int) = cells[y * cols + x]

    fun put(x: Int, y: Int, argb: Int) {
        if (x in 0 until cols && y in 0 until rows) {
            cells[y * cols + x] = argb
            owner[y * cols + x] = if (argb == 0) 0 else current
        }
    }

    fun fill(x: Int, y: Int, w: Int, h: Int, argb: Int) {
        begin()
        for (yy in y until y + h) for (xx in x until x + w) put(xx, yy, argb)
    }

    fun text(s: String, x: Int, y: Int, argb: Int, scale: Int = 1) {
        begin(isText = true)
        PixelFont.cells(s, x, y, scale) { cx, cy -> put(cx, cy, argb) }
    }

    /** Right-aligned so the last letter ends at [right] (inclusive). */
    fun textRight(s: String, right: Int, y: Int, argb: Int, scale: Int = 1) =
        text(s, right - PixelFont.width(s, scale) + 1, y, argb, scale)

    fun textCenter(s: String, y: Int, argb: Int, scale: Int = 1) =
        text(s, (cols - PixelFont.width(s, scale)) / 2, y, argb, scale)

    /** [art] rows of palette keys; '.' is off. */
    fun sprite(art: Array<String>, x: Int, y: Int, palette: Map<Char, Int>) {
        begin()
        art.forEachIndexed { r, line ->
            line.forEachIndexed { c, ch -> palette[ch]?.let { put(x + c, y + r, it) } }
        }
    }

    /** Starts a new drawing call for direct put()s (beams, rings, dots), so they count as their own shape. */
    fun shape() { begin() }

    /**
     * Every place a letter touches something it isn't part of, including diagonally: a line,
     * a stripe, a sprite, or another piece of text. Empty means the spacing is clean.
     */
    fun touches(): List<String> {
        val out = ArrayList<String>()
        for (y in 0 until rows) for (x in 0 until cols) {
            val me = owner[y * cols + x]
            if (me == 0 || me !in textOwners) continue
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx
                val ny = y + dy
                if ((dx == 0 && dy == 0) || nx !in 0 until cols || ny !in 0 until rows) continue
                val other = owner[ny * cols + nx]
                if (other != 0 && other != me) out += "($x,$y) touches ($nx,$ny)"
            }
        }
        return out
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
