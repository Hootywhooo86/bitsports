package com.bit128.bitsports.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bit128.bitsports.nhl.StandingRow
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import kotlin.math.roundToInt

/** The time, ticking every [ms] while on screen (for a countdown that counts every second). */
@Composable
fun rememberTicker(ms: Long, enabled: Boolean = true): Instant {
    var now by remember { mutableStateOf(Instant.now()) }
    if (enabled) LaunchedEffect(ms) { while (true) { now = Instant.now(); delay(ms) } }
    return now
}

/**
 * Game day: the board's bulbs come on one at a time, like an arena warming up, over [frames]
 * frames. Each bulb has its own moment, so it sparkles on rather than wiping.
 */
object WarmUp {
    const val FRAMES = 20
    fun mask(grid: LedGrid, frame: Int): LedGrid {
        if (frame >= FRAMES) return grid
        val out = LedGrid(grid.cols, grid.rows)
        val t = frame.toFloat() / FRAMES
        for (y in 0 until grid.rows) for (x in 0 until grid.cols) {
            val v = grid[x, y]
            if (v != 0 && PixelArt.rand(x * 131 + y * 7) < t) out.cells[y * grid.cols + x] = v
        }
        return out
    }
}

/**
 * How far each team has moved in its division since the standings of an earlier day: +2 means up
 * two places. Kept in the app's own storage, a snapshot per day.
 */
object RankMoves {
    fun update(context: Context, rows: List<StandingRow>, today: LocalDate = LocalDate.now()): Map<String, Int> {
        val p = context.applicationContext.getSharedPreferences("bitsports.ranks", Context.MODE_PRIVATE)
        val now = rows.associate { it.abbrev to it.divisionRank }
        val curDay = p.getString("day", null)
        val cur = p.getString("cur", null)?.let(::decode).orEmpty()
        val prev = if (curDay != null && curDay != today.toString()) cur else p.getString("prev", null)?.let(::decode).orEmpty()
        p.edit().putString("day", today.toString()).putString("cur", encode(now)).putString("prev", encode(prev)).apply()
        return moves(prev, now)
    }

    /** Pure: positive is up. */
    fun moves(prev: Map<String, Int>, now: Map<String, Int>): Map<String, Int> =
        now.mapNotNull { (k, r) -> prev[k]?.let { k to (it - r) } }.filter { it.second != 0 }.toMap()

    private fun encode(m: Map<String, Int>) = JSONObject(m as Map<*, *>).toString()
    private fun decode(s: String): Map<String, Int> = runCatching {
        val o = JSONObject(s); o.keys().asSequence().associateWith { o.getInt(it) }
    }.getOrDefault(emptyMap())
}

object Arrows {
    private const val UP = 0xFF7ED957.toInt()
    private const val DOWN = 0xFFFF4D6D.toInt()

    /** A rank arrow, 7 x 12: it shoots in from below (or above) with a fading trail, then holds. */
    fun arrow(move: Int, frame: Int): LedGrid {
        val g = LedGrid(7, 12)
        val up = move > 0
        val c = if (up) UP else DOWN
        val travel = (6 - frame).coerceAtLeast(0)
        val y0 = if (up) 1 + travel else 1 - travel
        val head = if (up) arrayOf("...#...", "..###..", ".#####.", "#######") else arrayOf("#######", ".#####.", "..###..", "...#...")
        val shaft = 4
        g.shape()
        if (up) {
            head.forEachIndexed { r, row -> row.forEachIndexed { x, ch -> if (ch == '#') g.put(x, y0 + r, c) } }
            for (r in 0 until shaft) { g.put(3, y0 + 4 + r, c); g.put(2, y0 + 4 + r, c) }
            for (k in 1..travel) g.put(3, y0 + 4 + shaft + k, HeatMap.blend(0xFF000000.toInt(), c, 1f - k / 7f))
        } else {
            for (r in 0 until shaft) { g.put(3, y0 + r, c); g.put(2, y0 + r, c) }
            head.forEachIndexed { r, row -> row.forEachIndexed { x, ch -> if (ch == '#') g.put(x, y0 + shaft + r, c) } }
            for (k in 1..travel) g.put(3, y0 - k, HeatMap.blend(0xFF000000.toInt(), c, 1f - k / 7f))
        }
        return g
    }
}

/** The arrow next to a team that moved in the standings, animating in once. */
@Composable
fun RankArrow(move: Int) {
    var frame by remember(move) { mutableIntStateOf(0) }
    LaunchedEffect(move) { while (frame < 8) { delay(70); frame++ } }
    val g = remember(move, frame) { Arrows.arrow(move, frame) }
    Box(Modifier.size(width = 9.dp, height = 15.dp)) { PixelGrid(g) }
}

/**
 * Pull to refresh as a slapshot, 128 x 16: pulling winds the stick back ([progress] 0..1), and
 * letting go fires the puck across the board, looping while the refresh runs.
 */
object Slapshot {
    const val COLS = 128
    const val ROWS = 16

    fun build(progress: Float, firing: Boolean, frame: Int, ink: Int): LedGrid {
        val g = LedGrid(COLS, ROWS)
        g.fill(0, 15, COLS, 1, PixelArt.ICE_LINE)
        // The stick pivots at the hands (x 14, y 2): straight down at rest, wound back as you pull.
        val angle = if (firing) (if (frame % 12 < 2) 0.6 else -0.2) else Math.toRadians(-10.0 - 120.0 * progress.coerceIn(0f, 1f))
        g.shape()
        for (r in 0..12) {
            val x = 14 + (Math.sin(-angle) * r).roundToInt()
            val y = 2 + (Math.cos(angle) * r).roundToInt()
            g.put(x, y, PixelArt.STICK)
        }
        // The blade at the end.
        val bx = 14 + (Math.sin(-angle) * 12).roundToInt()
        val by = 2 + (Math.cos(angle) * 12).roundToInt()
        g.put(bx + 1, by, PixelArt.STICK); g.put(bx + 2, by, PixelArt.STICK)
        // Hands, in team colour.
        g.fill(13, 1, 3, 2, ink)
        val puckX = if (firing) 18 + (frame % 12) * 10 else 18
        g.fill(puckX, 13, 4, 2, HockeyBoard.WHITE)
        if (firing) for (k in 1..5) g.put(puckX - k * 3, 13 + (k % 2), HeatMap.blend(0xFF000000.toInt(), HockeyBoard.WHITE, 1f - k / 6f))
        return g
    }
}
