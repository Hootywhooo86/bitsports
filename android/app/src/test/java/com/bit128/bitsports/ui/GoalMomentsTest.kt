package com.bit128.bitsports.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalMomentsTest {
    private val styles = listOf("lamp", "burst", "puck", "text")

    @Test fun everyStyleIs128WideAndMoves() {
        for (s in styles) {
            val a = GoalMoments.build(s, "EDM", "Connor McDavid", 12, 1)
            val b = GoalMoments.build(s, "EDM", "Connor McDavid", 12, 3)
            assertEquals(128, a.cols)
            assertEquals(72, a.rows)
            assertFalse("$s should animate", a.cells.contentEquals(b.cells))
        }
    }

    @Test fun textOnlyIsJustLetters() {
        val g = GoalMoments.build("text", "EDM", null, null, 0)
        // Nothing lit above the word or between it and the team name: only LED letters.
        for (y in (0 until 20) + (41 until 50)) for (x in 0 until 128) assertEquals(0, g[x, y])
        assertTrue((20 until 41).any { y -> (0 until 128).any { x -> g[x, y] != 0 } })
    }

    @Test fun previewLeavesTheScorerLineEmpty() {
        val g = GoalMoments.build("lamp", "EDM", null, null, 0)
        for (y in 60 until 72) for (x in 0 until 128) assertEquals(0, g[x, y])
    }
}
