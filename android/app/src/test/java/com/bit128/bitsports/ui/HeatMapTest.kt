package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.ShotMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatMapTest {
    private val oilers = 0xFF041E42.toInt() to 0xFFFC4C02.toInt()
    private val flames = 0xFFC8102E.toInt() to 0xFFF1BE48.toInt()

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

    @Test fun goalsSparkWhiteAndTheHottestSpotIsBanded() {
        val shots = List(6) { ShotMark("EDM", 82, 2, it == 0, 1, null) }
        val g = HeatMap.draw(HeatMap.heat(shots, "EDM"), oilers, flames)
        val (cx, cy) = PixelRink.at(-82f, 2f)
        assertEquals(HockeyBoard.WHITE, g[cx, cy])
        assertNotEquals(PixelRink.ICE, g[cx + 4, cy]) // heat around it
        assertEquals(5, HeatMap.level(1f, 1f))
        assertEquals(0, HeatMap.level(0.05f, 1f))
    }

    @Test fun justMineLeavesTheOtherEndDark() {
        val shots = listOf(ShotMark("EDM", 80, 0, false, 1, null), ShotMark("CGY", -80, 0, false, 1, null))
        val h = HeatMap.heat(shots, "EDM", onlyTeam = "EDM")
        assertEquals(0f, h.home.maxOrNull())
    }
}
