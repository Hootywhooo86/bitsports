package com.bit128.bitsports.ui

import com.bit128.bitsports.fixture
import com.bit128.bitsports.nhl.Clock
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.NhlParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ScoreFormatTest {
    private val games = NhlParser.scoreboard(fixture("score-2026-10-20.json"))
    private val ot = games.first { it.id == 2026020141L }
    private val toronto = ZoneId.of("America/Toronto")

    @Test fun finalsSayHowTheyEnded() {
        assertEquals("FINAL/OT", ScoreFormat.status(ot, toronto))
        assertEquals("FINAL", ScoreFormat.status(ot.copy(clock = ot.clock!!.copy(period = 3, periodType = "REG")), toronto))
        assertEquals("FINAL/SO", ScoreFormat.status(ot.copy(clock = ot.clock!!.copy(period = 5, periodType = "SO")), toronto))
    }

    @Test fun liveShowsPeriodAndClock() {
        val live = ot.copy(state = GameState.LIVE, clock = Clock(2, "REG", "08:44", inIntermission = false, running = true))
        assertEquals("LIVE · 2ND 08:44", ScoreFormat.status(live, toronto))
        assertEquals("INT · 2ND", ScoreFormat.status(live.copy(clock = live.clock!!.copy(inIntermission = true)), toronto))
    }

    @Test fun upcomingShowsLocalPuckDrop() {
        val fut = ot.copy(state = GameState.FUT, clock = null, startUtc = Instant.parse("2026-10-21T23:00:00Z"))
        assertEquals("7:00 PM", ScoreFormat.status(fut, toronto))
        assertEquals("POSTPONED", ScoreFormat.status(fut.copy(scheduleOk = false), toronto))
    }

    @Test fun followedFirstThenLiveThenUpcomingThenFinal() {
        val base = ot.copy(clock = null)
        val fin = base.copy(id = 1, state = GameState.OFF)
        val live = base.copy(id = 2, state = GameState.LIVE)
        val later = base.copy(id = 3, state = GameState.FUT, startUtc = Instant.parse("2026-10-22T02:00:00Z"))
        val soon = base.copy(id = 4, state = GameState.FUT, startUtc = Instant.parse("2026-10-21T23:00:00Z"))
        val mine = base.copy(id = 5, state = GameState.OFF, away = base.away.copy(abbrev = "EDM"))
        assertEquals(listOf(5L, 2L, 4L, 3L, 1L), ScoreFormat.order(listOf(fin, live, later, soon, mine), setOf("EDM")).map { it.id })
    }
}
