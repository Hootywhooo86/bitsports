package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.Clock
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.Situation
import com.bit128.bitsports.nhl.TeamLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class HockeyBoardTest {
    private val start = Instant.parse("2026-10-23T01:00:00Z")

    private fun game(state: GameState, clock: Clock? = null, situation: Situation? = null) = Game(
        id = 2026020163, state = state, scheduleOk = true, startUtc = start,
        away = TeamLine(12, "CAR", if (state == GameState.FUT) null else 2, if (state == GameState.FUT) null else 21),
        home = TeamLine(22, "EDM", if (state == GameState.FUT) null else 1, if (state == GameState.FUT) null else 18),
        clock = clock, situation = situation,
    )

    @Test fun boardIs128Wide() {
        val g = HockeyBoard.build(HockeyBoard.input(game(GameState.FUT), HockeyBoard.WHITE, start.minusSeconds(3600)))
        assertEquals(128, g.cols)
        assertEquals(44, g.rows)
    }

    @Test fun liveGameShowsPowerPlayAndPeriodBar() {
        val i = HockeyBoard.input(game(GameState.LIVE, Clock(2, "REG", "10:00", false, true), Situation("1451", "01:23")), HockeyBoard.WHITE)
        assertEquals(HockeyBoard.Zone.Line("EDM PP 1:23", HockeyBoard.WARN), i.zone)
        assertEquals(0.5f, i.progress!!, 0.001f)
        assertEquals("2ND 10:00", i.clock)
        val g = HockeyBoard.build(i)
        assertEquals(HockeyBoard.WHITE, g[0, 43]) // half the period bar lit...
        assertNotEquals(HockeyBoard.WHITE, g[127, 43]) // ...and half not
    }

    @Test fun intermissionBringsOutTheZamboni() {
        val i = HockeyBoard.input(game(GameState.LIVE, Clock(1, "REG", "00:00", true, false)), HockeyBoard.WHITE)
        assertEquals(HockeyBoard.Zone.Zamboni, i.zone)
        assertNull(i.progress)
        val a = HockeyBoard.build(i, frame = 20).cells
        val b = HockeyBoard.build(i, frame = 60).cells
        assertTrue(!a.contentEquals(b)) // it moves
    }

    @Test fun countdownBeforePuckDrop() {
        assertEquals("IN 2H 14M", HockeyBoard.countdown(start, start.minusSeconds(2 * 3600 + 14 * 60)))
        assertEquals("IN 5M", HockeyBoard.countdown(start, start.minusSeconds(300)))
        assertNull(HockeyBoard.countdown(start, start.minusSeconds(13 * 3600)))
    }
}
