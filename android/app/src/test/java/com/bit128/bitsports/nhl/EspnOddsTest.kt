package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/** Real ESPN responses from 3 November 2026, Dallas at Washington in the 1st period. */
class EspnOddsTest {
    private fun game(id: Long, away: String, home: String, start: String) = Game(
        id = id, state = GameState.LIVE, scheduleOk = true, startUtc = Instant.parse(start),
        away = TeamLine(1, away, 1, 5), home = TeamLine(2, home, 0, 4), clock = null, situation = null,
    )

    @Test fun matchesEspnGamesToNhlGames() {
        val games = listOf(
            game(2026020243L, "DAL", "WSH", "2026-11-04T00:00:00Z"),
            game(2026020238L, "DET", "TBL", "2026-11-04T00:00:00Z"), // ESPN says TB
            game(2026020239L, "EDM", "NJD", "2026-11-04T00:30:00Z"), // ESPN says NJ
        )
        val ids = EspnOdds.eventIds(fixture("espn-nhl-scoreboard-2026-11-03.json"), games)
        assertEquals("401892640", ids[2026020243L])
        assertEquals("401892635", ids[2026020238L])
        assertEquals("401892636", ids[2026020239L])
    }

    @Test fun readsTheLiveLineNotThePregameOne() {
        val o = EspnOdds.live(fixture("espn-odds-DAL-WSH-live.json"), 2026020243L)!!
        assertEquals("DraftKings live", o.partner)
        assertEquals(-245, o.away.moneyLine)
        assertEquals(185, o.home.moneyLine)
        assertEquals("-1.5", o.away.puckLine)
        assertEquals(120, o.away.puckLinePrice)
        assertEquals("5.5", o.totalLine)
        assertEquals(110, o.over)
        assertEquals(-140, o.under)
    }

    @Test fun noLiveLineMeansNull() {
        assertNull(EspnOdds.live("""{"items":[{"provider":{"name":"DraftKings"},"awayTeamOdds":{"moneyLine":-110}}]}""", 1L))
    }
}
