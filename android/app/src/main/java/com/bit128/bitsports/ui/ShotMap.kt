package com.bit128.bitsports.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.bit128.bitsports.nhl.NhlTeam
import com.bit128.bitsports.nhl.ShotMark
import kotlin.math.abs

/**
 * Shots on the 128-pixel rink. Each team is drawn attacking one end for the whole game (away
 * right, home left), so the map reads as two pressure zones instead of flipping every period.
 */
@Composable
fun ShotMap(shots: List<ShotMark>, away: NhlTeam?, home: NhlTeam?) {
    val grid = remember(shots, away, home) {
        val g = PixelRink.copy()
        val awayLed = away?.let { HockeyBoard.led(it.colors.first.toInt() to it.colors.second.toInt()) } ?: HockeyBoard.MUTED
        val homeLed = home?.let { HockeyBoard.led(it.colors.first.toInt() to it.colors.second.toInt()) } ?: HockeyBoard.MUTED
        for (s in shots.sortedBy { it.isGoal }) { // goals on top
            val right = s.teamAbbrev == away?.abbrev
            val x = if (right) abs(s.x.toFloat()) else -abs(s.x.toFloat())
            // Mirror y with x so a shot from the left circle stays on the left circle.
            val y = if ((s.x < 0) == right) -s.y.toFloat() else s.y.toFloat()
            val (cx, cy) = PixelRink.at(x, y)
            if (s.isGoal) g.fill(cx - 1, cy - 1, 3, 3, HockeyBoard.WHITE)
            else g.fill(cx, cy, 2, 2, if (right) awayLed else homeLed)
        }
        g
    }
    PixelGrid(grid)
}

/** A [LedGrid] as flat square pixels, edge to edge. */
@Composable
fun PixelGrid(grid: LedGrid) {
    Canvas(Modifier.fillMaxWidth().aspectRatio(grid.cols.toFloat() / grid.rows)) {
        val cell = size.width / grid.cols
        for (y in 0 until grid.rows) for (x in 0 until grid.cols) {
            val v = grid[x, y]
            if (v != 0) drawRect(Color(v), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
