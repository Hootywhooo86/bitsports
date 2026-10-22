package com.bit128.bitsports.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bit128.bitsports.sports.SportIcon

// The 128-pixel kit: everything here is drawn cell by cell on a grid, never scaled
// bitmaps, so it stays crisp at any size. Colour still only ever means data.

/** A 5x7 LED font. Lowercase is drawn as uppercase; unknown characters are blank. */
object PixelFont {
    const val W = 5
    const val H = 7

    private val GLYPHS: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf("####.", "....#", "....#", ".###.", "....#", "....#", "####."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf(".###.", "#....", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "....#", ".###."),
        'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'B' to arrayOf("####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."),
        'C' to arrayOf(".###.", "#...#", "#....", "#....", "#....", "#...#", ".###."),
        'D' to arrayOf("####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####."),
        'E' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#####"),
        'F' to arrayOf("#####", "#....", "#....", "####.", "#....", "#....", "#...."),
        'G' to arrayOf(".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".####"),
        'H' to arrayOf("#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'I' to arrayOf(".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###."),
        'J' to arrayOf("..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."),
        'K' to arrayOf("#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#"),
        'L' to arrayOf("#....", "#....", "#....", "#....", "#....", "#....", "#####"),
        'M' to arrayOf("#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"),
        'N' to arrayOf("#...#", "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#"),
        'O' to arrayOf(".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'P' to arrayOf("####.", "#...#", "#...#", "####.", "#....", "#....", "#...."),
        'Q' to arrayOf(".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#"),
        'R' to arrayOf("####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"),
        'S' to arrayOf(".####", "#....", "#....", ".###.", "....#", "....#", "####."),
        'T' to arrayOf("#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."),
        'U' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."),
        'V' to arrayOf("#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."),
        'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "#.#.#", ".#.#."),
        'X' to arrayOf("#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"),
        'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        'Z' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", "#....", "#####"),
        ':' to arrayOf(".....", "..#..", "..#..", ".....", "..#..", "..#..", "....."),
        '-' to arrayOf(".....", ".....", ".....", ".###.", ".....", ".....", "....."),
        '.' to arrayOf(".....", ".....", ".....", ".....", ".....", ".##..", ".##.."),
        '/' to arrayOf("....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#...."),
        '+' to arrayOf(".....", "..#..", "..#..", "#####", "..#..", "..#..", "....."),
        '!' to arrayOf("..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#.."),
        '@' to arrayOf(".###.", "#...#", "#.###", "#.#.#", "#.###", "#....", ".###."),
        '\'' to arrayOf("..#..", "..#..", ".....", ".....", ".....", ".....", "....."),
        '&' to arrayOf(".##..", "#..#.", "#.#..", ".#...", "#.#.#", "#..#.", ".##.#"),
    )

    fun glyph(c: Char): Array<String>? = GLYPHS[c.uppercaseChar()]

    /** Width in cells at [scale], with one blank column between characters. */
    fun width(text: String, scale: Int = 1) = if (text.isEmpty()) 0 else (text.length * (W + 1) - 1) * scale

    /** Calls [on] for every lit cell of [text] drawn with its top-left at (x, y). */
    inline fun cells(text: String, x: Int, y: Int, scale: Int = 1, on: (Int, Int) -> Unit) {
        text.forEachIndexed { i, ch ->
            val g = glyph(ch) ?: return@forEachIndexed
            val ox = x + i * (W + 1) * scale
            for (row in 0 until H) for (col in 0 until W) {
                if (g[row][col] == '#') {
                    for (sy in 0 until scale) for (sx in 0 until scale) on(ox + col * scale + sx, y + row * scale + sy)
                }
            }
        }
    }
}

/** 3x5 digits for jersey numbers on the on-ice rink. */
object MicroFont {
    private val DIGITS = arrayOf(
        arrayOf("###", "#.#", "#.#", "#.#", "###"), arrayOf(".#.", "##.", ".#.", ".#.", "###"),
        arrayOf("###", "..#", "###", "#..", "###"), arrayOf("###", "..#", "###", "..#", "###"),
        arrayOf("#.#", "#.#", "###", "..#", "..#"), arrayOf("###", "#..", "###", "..#", "###"),
        arrayOf("###", "#..", "###", "#.#", "###"), arrayOf("###", "..#", "..#", ".#.", ".#."),
        arrayOf("###", "#.#", "###", "#.#", "###"), arrayOf("###", "#.#", "###", "..#", "###"),
    )

    inline fun cells(number: String, x: Int, y: Int, on: (Int, Int) -> Unit) {
        number.forEachIndexed { i, ch ->
            val d = ch.digitToIntOrNull() ?: return@forEachIndexed
            val g = digit(d)
            for (row in 0 until 5) for (col in 0 until 3) if (g[row][col] == '#') on(x + i * 4 + col, y + row)
        }
    }

    fun digit(d: Int) = DIGITS[d]
}

/** A team colour that actually shows up as lit LEDs on black: the brighter of the two, or white. */
fun ledColor(c1: Color, c2: Color): Color {
    val c = if (c1.luminance() >= c2.luminance()) c1 else c2
    return if (c.luminance() < 0.08f) Color.White else c
}

/** Something to light on an LED board. */
sealed class Led {
    data class Text(val text: String, val x: Int, val y: Int, val color: Color, val scale: Int = 1) : Led()
    data class Fill(val x: Int, val y: Int, val w: Int, val h: Int, val color: Color) : Led()
}

/**
 * A dot-matrix board, [cols] x [rows] LEDs filling the width. Unlit LEDs glow faintly so the grid
 * always reads as a board. On first show the lights sweep on left to right.
 */
@Composable
fun LedBoard(cols: Int, rows: Int, items: List<Led>, modifier: Modifier = Modifier, unlit: Color = Color(0xFF161616)) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(650, easing = LinearEasing)) }
    val lit = remember(items, cols, rows) {
        val grid = arrayOfNulls<Color>(cols * rows)
        fun put(x: Int, y: Int, c: Color) { if (x in 0 until cols && y in 0 until rows) grid[y * cols + x] = c }
        items.forEach { item ->
            when (item) {
                is Led.Fill -> for (y in item.y until item.y + item.h) for (x in item.x until item.x + item.w) put(x, y, item.color)
                is Led.Text -> PixelFont.cells(item.text, item.x, item.y, item.scale) { x, y -> put(x, y, item.color) }
            }
        }
        grid
    }
    Canvas(modifier.fillMaxWidth().aspectRatio(cols.toFloat() / rows)) {
        val cell = size.width / cols
        val dot = cell * 0.78f
        val inset = (cell - dot) / 2
        val reach = (sweep.value * (cols + 6)).toInt()
        for (y in 0 until rows) for (x in 0 until cols) {
            val c = lit[y * cols + x]?.takeIf { x <= reach } ?: unlit
            drawRoundRect(
                c, topLeft = Offset(x * cell + inset, y * cell + inset), size = Size(dot, dot),
                cornerRadius = CornerRadius(dot * 0.3f),
            )
        }
    }
}

/** Centred x for [text] on a board [cols] wide. */
fun centerX(text: String, cols: Int, scale: Int = 1) = (cols - PixelFont.width(text, scale)) / 2

// ---------- icons ----------

private val ICONS: Map<String, Array<String>> = mapOf(
    "home" to arrayOf(
        "....#....", "...###...", "..#####..", ".#######.", "#########",
        ".##...##.", ".##.#.##.", ".##.#.##.", ".#######.",
    ),
    "sports" to arrayOf(
        "#.#####.#", "#.#####.#", ".#######.", "..#####..", "...###...",
        "....#....", "....#....", "...###...", "..#####..",
    ),
    "settings" to arrayOf(
        "...#.#...", ".#######.", ".##...##.", "##.....##", ".#..#..#.",
        "##.....##", ".##...##.", ".#######.", "...#.#...",
    ),
    "puck" to arrayOf(
        ".........", ".........", "..#####..", ".#######.", "#########",
        "#.......#", ".#######.", "..#####..", ".........",
    ),
    "football" to arrayOf(
        ".........", "...###...", ".#######.", "##.#.#.##", "#.#####.#",
        "##.#.#.##", ".#######.", "...###...", ".........",
    ),
    "basketball" to arrayOf(
        "..#####..", ".##.#.##.", "#.#.#.#.#", "#..###..#", "#########",
        "#..###..#", "#.#.#.#.#", ".##.#.##.", "..#####..",
    ),
    "baseball" to arrayOf(
        "..#####..", ".#.....#.", "#.#...#.#", "#.#...#.#", "#..#.#..#",
        "#.#...#.#", "#.#...#.#", ".#.....#.", "..#####..",
    ),
    "soccer" to arrayOf(
        "..#####..", ".#..#..#.", "#..###..#", "#.#####.#", "####.####",
        "#.#...#.#", "#.......#", ".#.###.#.", "..#####..",
    ),
)

fun sportIconName(i: SportIcon) = when (i) {
    SportIcon.PUCK -> "puck"
    SportIcon.FOOTBALL -> "football"
    SportIcon.BASKETBALL -> "basketball"
    SportIcon.BASEBALL -> "baseball"
    SportIcon.SOCCER -> "soccer"
}

@Composable
fun PixelIcon(name: String, color: Color, size: Dp = 18.dp) {
    val rows = ICONS[name] ?: return
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / rows[0].length
        rows.forEachIndexed { y, row ->
            row.forEachIndexed { x, ch ->
                if (ch == '#') drawRect(color, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

// ---------- team marks ----------

private val JERSEY = arrayOf(
    "..aa....aa..",
    ".aaaabbaaaa.",
    "aaaaabbaaaaa",
    "aaaaaaaaaaaa",
    "bbaaaaaaaabb",
    "aaaaaaaaaaaa",
    "..aaaaaaaa..",
    "..aaaaaaaa..",
    "..bbbbbbbb..",
    "..aaaaaaaa..",
    "..aaaaaaaa..",
    "..aaaaaaaa..",
)

/** A 12x12 pixel jersey in the team's two colours: the big team mark. */
@Composable
fun PixelJersey(c1: Color, c2: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val cell = this.size.width / 12
        drawJersey(c1, c2, 0f, 0f, cell)
    }
}

fun DrawScope.drawJersey(c1: Color, c2: Color, x: Float, y: Float, cell: Float) {
    // A near-black primary would vanish on the black ground, so it's drawn dark grey instead.
    val body = if (c1.luminance() < 0.03f) Color(0xFF2A2A2A) else c1
    JERSEY.forEachIndexed { row, line ->
        line.forEachIndexed { col, ch ->
            val c = when (ch) { 'a' -> body; 'b' -> c2; else -> null }
            if (c != null) drawRect(c, Offset(x + col * cell, y + row * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

// ---------- data bits ----------

/**
 * A head-to-head stat as a row of [blocks] pixel blocks: the away share from the left in the away
 * colour, the rest in the home colour.
 */
@Composable
fun PixelSplitBar(awayShare: Float, away: Color, home: Color, blocks: Int = 24) {
    val awayBlocks = (awayShare.coerceIn(0f, 1f) * blocks).let { kotlin.math.round(it).toInt() }
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val gap = 2f
        val w = (size.width - gap * (blocks - 1)) / blocks
        for (i in 0 until blocks) {
            drawRect(if (i < awayBlocks) away else home, Offset(i * (w + gap), 0f), Size(w, size.height))
        }
    }
}

/** Last-ten results as pixel blocks: a win is a full block, an OT loss half, a loss an outline. */
@Composable
fun ResultStrip(results: List<String>, color: Color, cell: Dp = 10.dp) {
    Row {
        results.forEach { r ->
            Canvas(Modifier.size(cell + 3.dp, cell)) {
                val s = cell.toPx()
                when (r) {
                    "W", "OTW", "SOW" -> drawRect(color, Offset.Zero, Size(s, s))
                    "OTL", "SOL", "OT" -> {
                        drawRect(color.copy(alpha = 0.35f), Offset.Zero, Size(s, s))
                        drawRect(color, Offset.Zero, Size(s, s / 2))
                    }
                    else -> drawRect(color.copy(alpha = 0.25f), Offset.Zero, Size(s, s))
                }
            }
        }
    }
}
