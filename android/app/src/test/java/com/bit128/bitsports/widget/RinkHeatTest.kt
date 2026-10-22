package com.bit128.bitsports.widget

import com.bit128.bitsports.nhl.ShotMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RinkHeatTest {
    private fun shot(team: String, x: Int, y: Int, goal: Boolean = false) = ShotMark(team, x, y, goal, period = 1, shooter = null)
    private val mid = RinkHeat.ROWS / 2

    @Test fun eachTeamLightsTheEndUnderItsOwnName() {
        // Away is drawn on the left, so away shots land left whichever end they came from.
        val g = RinkHeat.grid(listOf(shot("EDM", 80, 0), shot("EDM", -80, 0), shot("CGY", 80, 0), shot("CGY", -80, 0)), awayAbbrev = "EDM")
        val left = g.index(RinkHeat.colFor(-80f), mid)
        val right = g.index(RinkHeat.colFor(80f), mid)
        assertEquals(2 * RinkHeat.HIT, g.away[left])
        assertEquals(0, g.away[right])
        assertEquals(2 * RinkHeat.HIT, g.home[right])
        assertEquals(0, g.home[left])
    }

    @Test fun aShotWarmsItsNeighboursSoPressureReadsAsAZone() {
        val g = RinkHeat.grid(listOf(shot("CGY", 80, 0)), awayAbbrev = "EDM")
        val col = RinkHeat.colFor(80f)
        assertEquals(RinkHeat.HIT, g.home[g.index(col, mid)])
        for ((c, r) in listOf(col - 1 to mid, col + 1 to mid, col to mid - 1, col to mid + 1)) {
            assertEquals(RinkHeat.SPILL, g.home[g.index(c, r)])
        }
        assertEquals(0, g.home[g.index(col + 1, mid + 1)])
    }

    @Test fun heatNeverSpillsAcrossCentreIce() {
        val centre = RinkHeat.colFor(0f)
        val g = RinkHeat.grid(listOf(shot("CGY", 5, 0)), awayAbbrev = "EDM") // lands just right of centre
        assertEquals(RinkHeat.colFor(5f), centre + 1)
        assertEquals(0, g.home[g.index(centre, mid)])
    }

    @Test fun goalsAreMarkedOnTheirCell() {
        val g = RinkHeat.grid(listOf(shot("CGY", 85, -5, goal = true)), awayAbbrev = "EDM")
        val c = g.index(RinkHeat.colFor(85f), RinkHeat.rowFor(-5f))
        assertTrue(g.goals[c])
        assertEquals(RinkHeat.HIT, g.home[c])
    }

    @Test fun edgesStayOnTheBoard() {
        val g = RinkHeat.grid(listOf(shot("EDM", 100, 42), shot("EDM", -100, -42)), awayAbbrev = "EDM")
        assertEquals(0, RinkHeat.colFor(-100f))
        assertEquals(RinkHeat.COLS - 1, RinkHeat.colFor(100f))
        assertEquals(0, RinkHeat.rowFor(42.5f))
        assertEquals(RinkHeat.ROWS - 1, RinkHeat.rowFor(-42.5f))
        assertEquals(2 * RinkHeat.HIT + 4 * RinkHeat.SPILL, g.away.sum()) // corner cells have two neighbours each
    }

    @Test fun levelsStepFromDimToFull() {
        assertEquals(0, RinkHeat.level(0, 5))
        assertEquals(1, RinkHeat.level(1, 8))
        assertEquals(RinkHeat.LEVELS, RinkHeat.level(8, 8))
        assertEquals(RinkHeat.LEVELS, RinkHeat.level(1, 1))
    }

    @Test fun onlyMineHidesTheOtherTeam() {
        val g = RinkHeat.grid(listOf(shot("EDM", 80, 0), shot("CGY", 80, 0)), awayAbbrev = "EDM", onlyTeam = "CGY")
        assertEquals(0, g.away.sum())
        assertTrue(g.home.sum() > 0)
    }
}
