package com.bit128.bitsports.sports

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class EspnParserTest {
    private val nfl = Leagues.byKey("nfl")!!

    @Test fun parsesScoreboard() {
        val games = EspnParser.scoreboard(fixture("espn-nfl.json"), nfl)
        assertEquals(3, games.size)
        val g = games.first()
        assertEquals("NE", g.away.abbrev)
        assertEquals("Chicago Bears", g.home.name)
        assertEquals(EspnState.PRE, g.state)
        assertEquals(Instant.parse("2026-10-23T00:15:00Z"), g.start)
        assertEquals(0xFF0B1C3AL, g.home.color)
        assertEquals("Soldier Field", g.venue)
        assertTrue(g.tv.contains("Prime Video"))
        assertTrue(g.leaders.isNotEmpty())
        assertEquals("4-2", g.home.record)
    }

    @Test fun matchesTeamsLoosely() {
        val g = EspnParser.scoreboard(fixture("espn-nfl.json"), nfl).first()
        assertTrue(g.involves("Chicago Bears"))
        assertTrue(g.involves("bears"))
        assertTrue(g.involves("NE"))
        assertFalse(g.involves("Chicago")) // a city alone could be any of its teams
    }

    @Test fun mapsFreeTypedSports() {
        assertEquals(Leagues.NHL, Leagues.match("Hockey"))
        assertEquals("nfl", Leagues.match("NFL")?.key)
        assertEquals("epl", Leagues.match("Premier League")?.key)
        assertNull(Leagues.match("curling"))
    }

    @Test fun fixesMinutePrecisionTimes() {
        assertEquals("2026-10-23T00:15:00Z", EspnParser.fixIso("2026-10-23T00:15Z"))
        assertEquals("2026-10-23T00:15:00Z", EspnParser.fixIso("2026-10-23T00:15:00Z"))
    }
}
