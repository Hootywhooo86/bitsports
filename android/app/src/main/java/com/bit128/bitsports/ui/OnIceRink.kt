package com.bit128.bitsports.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** One skater or goalie to draw: jersey number, position letter (C L R D G) and team colours. */
data class RinkPlayer(val number: Int?, val pos: String, val colors: Pair<Color, Color>)

/**
 * The pixel rink with whoever is on the ice, in formation: away defends the left net, home the
 * right. Same 100x44 grid as the shot map. Players past the usual six (a power play's extra
 * skater, a pulled goalie's extra attacker) fill spare spots.
 */
@Composable
fun OnIceRink(away: List<RinkPlayer>, home: List<RinkPlayer>) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(100f / 44f)) {
        val cell = size.width / 100f
        fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) =
            drawRect(color, topLeft = Offset(x * cell, y * cell), size = Size(w * cell + 0.5f, h * cell + 0.5f))
        rect(0f, 0f, 100f, 44f, Color(0xFF0D0D0D))
        rect(1f, 1f, 98f, 42f, Color(0xFF141414))
        rect(49f, 1f, 2f, 42f, Color(0xFF3A1A22))
        rect(37f, 1f, 1f, 42f, Color(0xFF1A2433)); rect(62f, 1f, 1f, 42f, Color(0xFF1A2433))
        rect(5f, 1f, 1f, 42f, Color(0xFF3A1A22)); rect(94f, 1f, 1f, 42f, Color(0xFF3A1A22))
        rect(3f, 20f, 2f, 4f, Color(0xFF2A2A2A)); rect(95f, 20f, 2f, 4f, Color(0xFF2A2A2A))
        // Centre dot.
        rect(49f, 21f, 2f, 2f, Color(0xFF3A1A22))

        fun player(p: RinkPlayer, cx: Int, cy: Int) {
            val (c1, c2) = p.colors
            val body = if (c1.luminance() < 0.03f) Color(0xFF2A2A2A) else c1
            // A 9x7 jersey block with a 1-cell stripe, the number on top in whichever colour reads.
            rect(cx - 4f, cy - 3f, 9f, 7f, body)
            rect(cx - 4f, cy - 3f, 9f, 1f, c2)
            val ink = if (body.luminance() > 0.45f) Color.Black else Color.White
            val num = (p.number ?: 0).toString().take(2)
            val w = num.length * 4 - 1
            MicroFont.cells(num, cx - w / 2, cy - 1) { x, y -> rect(x.toFloat(), y.toFloat(), 1f, 1f, ink) }
        }

        fun team(players: List<RinkPlayer>, leftSide: Boolean) {
            fun x(v: Int) = if (leftSide) v else 100 - v
            val goalieSpot = x(9) to 22
            val dSpots = listOf(x(22) to 12, x(22) to 32, x(30) to 22)
            val fSpots = listOf(x(42) to 22, x(40) to 8, x(40) to 36, x(30) to 6, x(30) to 38)
            val goalies = players.filter { it.pos == "G" }
            val d = players.filter { it.pos == "D" }
            val f = players.filter { it.pos != "G" && it.pos != "D" }
            goalies.take(1).forEach { player(it, goalieSpot.first, goalieSpot.second) }
            // Forwards in C, L, R order so they land in the middle, top and bottom lanes.
            val forwards = f.sortedBy { listOf("C", "L", "R").indexOf(it.pos).let { i -> if (i < 0) 3 else i } }
            val freeF = fSpots.toMutableList()
            val freeD = dSpots.toMutableList()
            forwards.forEach { p -> (freeF.removeFirstOrNull() ?: freeD.removeLastOrNull())?.let { (sx, sy) -> player(p, sx, sy) } }
            d.forEach { p -> (freeD.removeFirstOrNull() ?: freeF.removeLastOrNull())?.let { (sx, sy) -> player(p, sx, sy) } }
        }
        team(away, leftSide = true)
        team(home, leftSide = false)
    }
}
