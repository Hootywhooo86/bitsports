package com.bit128.bitsports.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAnimsTest {
    @Test fun rankMovesUpIsPositive() {
        assertEquals(mapOf("EDM" to 2, "VAN" to -1), RankMoves.moves(mapOf("EDM" to 3, "VAN" to 1, "CGY" to 2), mapOf("EDM" to 1, "VAN" to 2, "CGY" to 2)))
    }

    @Test fun warmUpEndsFullyLit() {
        val g = LedGrid(10, 4).apply { fill(0, 0, 10, 4, HockeyBoard.WHITE) }
        assertEquals(0, WarmUp.mask(g, 0).cells.count { it != 0 })
        assertTrue(WarmUp.mask(g, WarmUp.FRAMES / 2).cells.count { it != 0 } in 5..35)
        assertEquals(40, WarmUp.mask(g, WarmUp.FRAMES).cells.count { it != 0 })
    }

    @Test fun slapshotWindsBackAsYouPull() {
        val rest = Slapshot.build(0f, false, 0, HockeyBoard.WHITE)
        val wound = Slapshot.build(1f, false, 0, HockeyBoard.WHITE)
        assertTrue(!rest.cells.contentEquals(wound.cells))
    }
}
