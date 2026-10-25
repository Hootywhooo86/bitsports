package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.ShotMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatMapTest {
    private val inks = TeamInk.pair("EDM", "CGY")

    @Test fun eachTeamHeatsItsOwnEnd() {
        val shots = listOf(ShotMark("EDM", 80, 0, false, 1, null), ShotMark("CGY", -80, 0, false, 1, null))
        val h = HeatMap.heat(shots, awayAbbrev = "EDM")
        val (ax, ay) = PixelRink.at(-80f, 0f)
        val (hx, hy) = PixelRink.at(80f, 0f)
        assertTrue(h.away[ay * PixelRink.W + ax] > 0f) // away attacks the left end
        assertEquals(0f, h.away[hy * PixelRink.W + hx])
        assertTrue(h.home[hy * PixelRink.W + hx] > 0f)
    }

    @Test fun heatStaysOnItsSideOfCentre() {
        val h = HeatMap.heat(listOf(ShotMark("EDM", 2, 0, false, 1, null)), awayAbbrev = "EDM")
        for (y in 0 until PixelRink.H) for (x in PixelRink.W / 2 until PixelRink.W) assertEquals(0f, h.away[y * PixelRink.W + x])
    }

    @Test fun goalsSparkGoldInABlackRingAndTheHottestSpotIsBanded() {
        val shots = List(6) { ShotMark("EDM", 82, 2, it == 0, 1, null) }
        val heat = HeatMap.heat(shots, "EDM")
        val g = HeatMap.draw(heat, inks)
        val (cx, cy) = heat.goals.single()
        assertEquals(HockeyBoard.WHITE, g[cx, cy])
        assertEquals(HeatMap.GOAL, g[cx + 2, cy])
        assertEquals(PixelRink.OUTSIDE, g[cx + 3, cy]) // ringed in black
        assertNotEquals(PixelRink.ICE, g[cx + 5, cy]) // heat around it
        assertEquals(5, HeatMap.level(1f, 1f))
        assertEquals(0, HeatMap.level(0.05f, 1f))
    }

    @Test fun justMineLeavesTheOtherEndDark() {
        val shots = listOf(ShotMark("EDM", 80, 0, false, 1, null), ShotMark("CGY", -80, 0, false, 1, null))
        val h = HeatMap.heat(shots, "EDM", onlyTeam = "EDM")
        assertEquals(0f, h.home.maxOrNull())
    }

    /** The Leafs used to heat the ice white, and their white goal sparks disappeared in it. */
    @Test fun leafsGoalsStandOutOnTheirOwnHeat() {
        val shots = List(8) { ShotMark("TOR", 82, 2, it == 0, 1, null) }
        val heat = HeatMap.heat(shots, "TOR")
        val g = HeatMap.draw(heat, TeamInk.pair("TOR", "TBL"))
        val (cx, cy) = heat.goals.single()
        assertEquals(HockeyBoard.WHITE, g[cx, cy])
        assertTrue(!TeamInk.alike(g[cx + 5, cy], HeatMap.GOAL))
        assertEquals(PixelRink.OUTSIDE, g[cx, cy - 3])
    }
}
