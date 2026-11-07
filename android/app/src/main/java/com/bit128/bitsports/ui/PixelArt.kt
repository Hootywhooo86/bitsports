package com.bit128.bitsports.ui

/**
 * Little sprites and motion helpers shared by the animations: hockey players, hats, a padlock,
 * the Cup, fireworks. Sprites are rows of palette keys ('.' is off), drawn with [LedGrid.sprite].
 */
object PixelArt {
    const val STICK = 0xFFC89A5A.toInt()
    const val SKATE = 0xFFD8D8D8.toInt()
    const val PANTS = 0xFF2A2A2A.toInt()
    const val SKIN = 0xFFE8B48A.toInt()
    const val GOLD = 0xFFFFD95E.toInt()
    const val SILVER = 0xFFC8CDD2.toInt()
    const val ICE_LINE = 0xFF1C2C44.toInt()

    /** A skater gliding right. j jersey, h helmet, p pants, k skates, s stick. 14 x 16. */
    val SKATER = arrayOf(
        ".....hhh......",
        "....hhhhh.....",
        ".....hhh......",
        "....jjjjj.....",
        "...jjjjjjj....",
        "...jjjjjjjj...",
        "...jjjjj.jjs..",
        "....jjjj...s..",
        "....ppppp...s.",
        "....pp..pp..s.",
        "...pp....pp..s",
        "..pp......pp.s",
        "..pp.......pps",
        ".kkk......kkks",
        "kkkk.....kkkk.",
        "..............",
    )

    /** Down on one knee, sliding, stick out front. 18 x 12. */
    val KNEE_SLIDE = arrayOf(
        "......hhh.........",
        ".....hhhhh........",
        "......hhh.........",
        ".....jjjjj........",
        "....jjjjjjjss.....",
        "....jjjjjj..ss....",
        "....jjjjj.....ss..",
        "....ppppp.......ss",
        "...pppp.pp........",
        "..ppp....ppppp....",
        ".kkk.......kkkk...",
        "kkkk.......kkkk...",
    )

    /** Arms up, stick raised over the head. 14 x 22. */
    val STICK_UP = arrayOf(
        "..........s...",
        ".........s....",
        "........s.....",
        ".......s......",
        "..j...hhh..j..",
        "..j..hhhhh.j..",
        "..jj..hhh.jj..",
        "...jjjjjjjj...",
        "....jjjjjj....",
        "....jjjjjj....",
        "....jjjjjj....",
        "....jjjjjj....",
        "....pppppp....",
        "....pp..pp....",
        "....pp..pp....",
        "....pp..pp....",
        "....pp..pp....",
        "...ppp..ppp...",
        "...kkk..kkk...",
        "..kkkk..kkkk..",
        "..............",
        "..............",
    )

    /** A goalie in the butterfly, pads wide. g gear, h mask, k skates. 16 x 14. */
    val GOALIE = arrayOf(
        "......hhhh......",
        ".....hhhhhh.....",
        "......hhhh......",
        "....gggggggg....",
        "..ggggggggggggb.",
        "..gggggggggggbbb",
        "...gggggggggg.b.",
        "....gggggggg....",
        "....pppppppp....",
        "...ggg....ggg...",
        "..gggg....gggg..",
        "..gggg....gggg..",
        ".kkkk......kkkk.",
        "................",
    )

    /** A ball cap, thrown on the ice for a hat trick. c crown, b brim. 9 x 4. */
    val CAP = arrayOf(
        "..ccccc..",
        ".ccccccc.",
        "ccccccccc",
        "bbbbbbbbb",
    )

    /** The padlock, shackle open (row 0..4 lifted) or shut. 15 x 16. */
    fun padlock(shut: Boolean): Array<String> {
        val shackle = if (shut) arrayOf(
            "....sssssss....",
            "...ss.....ss...",
            "...s.......s...",
            "...s.......s...",
        ) else arrayOf(
            "....sssssss....",
            "...ss.....ss...",
            "...s.......s...",
            "...s...........",
        )
        val gap = if (shut) emptyArray() else arrayOf("...s...........")
        val body = arrayOf(
            ".bbbbbbbbbbbbb.",
            "bbbbbbbbbbbbbbb",
            "bbbbbbkkkbbbbbb",
            "bbbbbbkkkbbbbbb",
            "bbbbbbbkbbbbbbb",
            "bbbbbbbkbbbbbbb",
            "bbbbbbbbbbbbbbb",
            ".bbbbbbbbbbbbb.",
        )
        return shackle + gap + body
    }

    /** The Stanley Cup. s silver, d dark bands. 20 x 30. */
    val CUP = arrayOf(
        "....ssssssssssss....",
        "...s............s...",
        "..ssssssssssssssss..",
        "ss..ssssssssssss..ss",
        "s....ssssssssss....s",
        "ss....ssssssss....ss",
        "........ssss........",
        "........ssss........",
        ".......ssssss.......",
        "......ssssssss......",
        ".....ddddddddd......",
        ".....ssssssssss.....",
        "....ssssssssssss....",
        "....dddddddddddd....",
        "....ssssssssssss....",
        "...ssssssssssssss...",
        "...dddddddddddddd...",
        "...ssssssssssssss...",
        "..ssssssssssssssss..",
        "..dddddddddddddddd..",
        "..ssssssssssssssss..",
        ".ssssssssssssssssss.",
        ".dddddddddddddddddd.",
        ".ssssssssssssssssss.",
        "ssssssssssssssssssss",
        "dddddddddddddddddddd",
        "ssssssssssssssssssss",
        "ssssssssssssssssssss",
        "....................",
        "....................",
    )

    /** A flame, [h] rows tall (1..10), flickering with [frame]. */
    fun flame(g: LedGrid, x: Int, bottom: Int, h: Int, frame: Int, w: Int = 7) {
        g.shape()
        for (row in 0 until h) {
            val y = bottom - row
            val t = row.toFloat() / h
            val half = ((1f - t) * w / 2f + 0.5f).toInt().coerceAtLeast(if (row < h - 1) 1 else 0)
            val wobble = if ((frame + row) % 3 == 0) 1 else if ((frame + row) % 3 == 1) -1 else 0
            val cx = x + w / 2 + if (row > 1) wobble else 0
            for (dx in -half..half) {
                val c = when {
                    t < 0.3f && kotlin.math.abs(dx) <= half / 2 -> 0xFFFFF2B0.toInt()
                    t < 0.6f -> 0xFFFFB020.toInt()
                    else -> 0xFFFF5A1F.toInt()
                }
                g.put(cx + dx, y, c)
            }
        }
    }

    /** Deterministic noise so every frame of an animation is the same each run (and testable). */
    fun rand(seed: Int): Float {
        var x = seed * 374761393 + 668265263
        x = (x xor (x ushr 13)) * 1274126177
        return ((x xor (x ushr 16)) and 0x7fffffff) / 2147483647f
    }

    /** All lit cells of [s] drawn at scale [scale] with its left edge at [x] and top at [y]. */
    fun textCells(s: String, x: Int, y: Int, scale: Int = 1): List<Pair<Int, Int>> {
        val out = ArrayList<Pair<Int, Int>>()
        PixelFont.cells(s, x, y, scale) { cx, cy -> out += cx to cy }
        return out
    }

    /**
     * Pixels flying in from a start point to their places in some text: at [t] = 0 they're all at
     * the start, at 1 they spell it. Drawn as one shape (not text) while moving.
     */
    fun converge(g: LedGrid, targets: List<Pair<Int, Int>>, fromX: Int, fromY: Int, t: Float, colors: List<Int>, seed: Int = 7) {
        g.shape()
        val e = 1f - (1f - t.coerceIn(0f, 1f)).let { it * it * it }
        targets.forEachIndexed { i, (tx, ty) ->
            // Each pixel takes a slightly different curve so they swarm instead of sliding in a block.
            val bend = (rand(seed + i) - 0.5f) * 40f * (1f - e)
            val x = fromX + (tx - fromX) * e + bend
            val y = fromY + (ty - fromY) * e - bend * 0.5f
            g.put(x.toInt(), y.toInt(), colors[i % colors.size])
        }
    }

    /** Clears a box (with a 1-LED margin) so text drawn there touches nothing. */
    fun clearFor(g: LedGrid, s: String, x: Int, y: Int, scale: Int = 1) {
        g.fill(x - 2, y - 2, PixelFont.width(s, scale) + 4, PixelFont.H * scale + 4, 0)
    }

    fun clearForCenter(g: LedGrid, s: String, y: Int, scale: Int = 1) =
        clearFor(g, s, (g.cols - PixelFont.width(s, scale)) / 2, y, scale)
}
