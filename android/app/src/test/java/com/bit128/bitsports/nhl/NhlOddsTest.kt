package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The NHL's real FanDuel (Canada) odds for 3 November 2026. */
class NhlOddsTest {
    private val odds = NhlOddsParser.odds(fixture("odds-CA-2026-11-03.json"))

    @Test fun readsEveryGame() {
        assertTrue(odds.size >= 10)
        assertTrue(odds.values.all { it.partner == "FanDuel" })
    }

    @Test fun utahAtToronto() {
        val o = odds.getValue(2026020236L)
        assertEquals(-150, o.away.moneyLine)
        assertEquals(125, o.home.moneyLine)
        assertEquals("-1.5", o.away.puckLine)
        assertEquals(154, o.away.puckLinePrice)
        assertEquals("+1.5", o.home.puckLine)
        assertEquals("6.5", o.totalLine)
        assertEquals(-138, o.over)
        assertEquals(112, o.under)
        assertEquals(360, o.draw)
        assertEquals("UTA", o.favourite("UTA", "TOR"))
    }

    @Test fun widgetLinePutsYourTeamFirst() {
        val o = odds.getValue(2026020239L) // EDM @ NJD
        assertEquals("EDM +134 · NJD -162 · O/U ${o.totalLine}", NhlOddsParser.line(o, "EDM", "NJD", mine = "EDM"))
        assertEquals("NJD -162 · EDM +134 · O/U ${o.totalLine}", NhlOddsParser.line(o, "EDM", "NJD", mine = "NJD"))
    }

    @Test fun formatsAndImpliedChance() {
        assertEquals("+125", NhlOddsParser.american(125))
        assertEquals("-150", NhlOddsParser.american(-150))
        assertEquals("EVEN", NhlOddsParser.american(100))
        assertEquals(0.6f, NhlOddsParser.implied(-150), 0.001f)
        assertEquals(0.4444f, NhlOddsParser.implied(125), 0.001f)
    }
}
