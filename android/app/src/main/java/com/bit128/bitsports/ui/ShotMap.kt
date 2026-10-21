package com.bit128.bitsports.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.bit128.bitsports.nhl.NhlTeam
import com.bit128.bitsports.nhl.ShotMark
import kotlin.math.abs

/**
 * A pixel rink: 100x44 cells over the NHL's -100..100 x -42.5..42.5 coordinates.
 * Each team is drawn attacking one end for the whole game (away right, home left),
 * so the map reads as two heat zones instead of flipping every period.
 */
@Composable
fun ShotMap(shots: List<ShotMark>, away: NhlTeam?, home: NhlTeam?) {
    val awayAbbrev = away?.abbrev
    Canvas(Modifier.fillMaxWidth().aspectRatio(100f / 44f)) {
        val cell = size.width / 100f
        fun rect(x: Float, y: Float, w: Float, h: Float, color: Color) =
            drawRect(color, topLeft = Offset(x * cell, y * cell), size = Size(w * cell, h * cell))
        rect(0f, 0f, 100f, 44f, Color(0xFF0D0D0D))
        rect(1f, 1f, 98f, 42f, Color(0xFF141414))
        rect(49f, 1f, 2f, 42f, Color(0xFF3A1A22))      // centre red line
        rect(37f, 1f, 1f, 42f, Color(0xFF1A2433)); rect(62f, 1f, 1f, 42f, Color(0xFF1A2433)) // blue lines
        rect(5f, 1f, 1f, 42f, Color(0xFF3A1A22)); rect(94f, 1f, 1f, 42f, Color(0xFF3A1A22))   // goal lines
        rect(3f, 20f, 2f, 4f, Color(0xFF2A2A2A)); rect(95f, 20f, 2f, 4f, Color(0xFF2A2A2A))   // nets
        val sorted = shots.sortedBy { it.isGoal } // goals on top
        for (s in sorted) {
            val attackingRight = s.teamAbbrev == awayAbbrev
            val nx = if (attackingRight) abs(s.x) else -abs(s.x)
            val cx = ((nx + 100f) / 200f * 100f).coerceIn(1f, 97f)
            val cy = ((42.5f - s.y) / 85f * 44f).coerceIn(1f, 41f)
            val team = if (attackingRight) away else home
            val color = if (s.isGoal) Color.White else Color(team?.colors?.first ?: 0xFF8C8C8C)
            val sz = if (s.isGoal) 3f else 2f
            rect(cx - sz / 2, cy - sz / 2, sz, sz, color)
        }
    }
}
