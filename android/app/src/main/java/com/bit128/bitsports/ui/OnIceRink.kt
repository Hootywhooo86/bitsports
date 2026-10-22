package com.bit128.bitsports.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/** One skater or goalie to draw: jersey number, position letter (C L R D G) and team colours. */
data class RinkPlayer(val number: Int?, val pos: String, val colors: Pair<Color, Color>)

/**
 * The 128-pixel rink with whoever is on the ice, in formation: away defends the left net, home
 * the right. Players past the usual six (a power play's extra skater, a pulled goalie's extra
 * attacker) fill spare spots.
 */
@Composable
fun OnIceRink(away: List<RinkPlayer>, home: List<RinkPlayer>) {
    val grid = remember(away, home) {
        val g = PixelRink.copy()
        team(g, away, leftSide = true)
        team(g, home, leftSide = false)
        g
    }
    PixelGrid(grid)
}

// Spots in rink feet for the team defending the left net; the other side mirrors x.
private val GOALIE = -84f to 0f
private val D_SPOTS = listOf(-62f to 15f, -62f to -15f, -50f to 0f)
private val F_SPOTS = listOf(-28f to 0f, -32f to 26f, -32f to -26f, -46f to 30f, -46f to -30f)

private fun team(g: LedGrid, players: List<RinkPlayer>, leftSide: Boolean) {
    fun spot(p: Pair<Float, Float>) = PixelRink.at(if (leftSide) p.first else -p.first, p.second)
    val goalies = players.filter { it.pos == "G" }
    val d = players.filter { it.pos == "D" }
    // Forwards in C, L, R order so they land in the middle, top and bottom lanes.
    val f = players.filter { it.pos != "G" && it.pos != "D" }
        .sortedBy { listOf("C", "L", "R").indexOf(it.pos).let { i -> if (i < 0) 3 else i } }
    goalies.take(1).forEach { jersey(g, it, spot(GOALIE)) }
    val freeF = F_SPOTS.toMutableList()
    val freeD = D_SPOTS.toMutableList()
    f.forEach { p -> (freeF.removeFirstOrNull() ?: freeD.removeLastOrNull())?.let { jersey(g, p, spot(it)) } }
    d.forEach { p -> (freeD.removeFirstOrNull() ?: freeF.removeLastOrNull())?.let { jersey(g, p, spot(it)) } }
}

/** A 9x7 jersey block with a stripe in the second colour and the number on top in whichever ink reads. */
private fun jersey(g: LedGrid, p: RinkPlayer, at: Pair<Int, Int>) {
    val (cx, cy) = at
    val (c1, c2) = p.colors
    val body = if (c1.luminance() < 0.03f) Color(0xFF2A2A2A) else c1
    g.fill(cx - 4, cy - 3, 9, 7, body.toArgb())
    g.fill(cx - 4, cy - 3, 9, 1, c2.toArgb())
    val ink = (if (body.luminance() > 0.45f) Color.Black else Color.White).toArgb()
    val num = (p.number ?: 0).toString().take(2)
    val w = num.length * 4 - 1
    MicroFont.cells(num, cx - w / 2, cy - 1) { x, y -> g.put(x, y, ink) }
}
