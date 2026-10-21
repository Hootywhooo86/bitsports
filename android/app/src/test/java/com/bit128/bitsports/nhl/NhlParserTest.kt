package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NhlParserTest {
    @Test fun parsesScoreboard() {
        val games = NhlParser.scoreboard(fixture("score-2026-10-20.json"))
        assertEquals(11, games.size)
        val g = games.first { it.id == 2026020141L }
        assertEquals(GameState.OFF, g.state)
        assertEquals("SJS", g.away.abbrev)
        assertEquals(4, g.away.score)
        assertEquals("MTL", g.home.abbrev)
        assertEquals(3, g.home.score)
        assertTrue(g.scheduleOk)
        assertEquals(Instant.parse("2026-10-20T23:00:00Z"), g.startUtc)
    }

    @Test fun parsesPlayByPlay() {
        val d = NhlParser.playByPlay(fixture("pbp-2026020141.json"), Instant.EPOCH)
        assertEquals(344, d.plays.size)
        val shot = d.plays.first { it.eventId == 74 }
        assertEquals("shot-on-goal", shot.type)
        assertEquals(-66, shot.x)
        assertEquals(-20, shot.y)
        assertEquals("snap", shot.shotType)
        assertEquals("Macklin Celebrini", d.roster[8484801])
        assertEquals(30, d.game.away.sog)
        assertEquals(34, d.game.home.sog)
        assertEquals(4, d.game.clock!!.period)
        assertEquals("OT", d.game.clock!!.periodType)
    }
}
