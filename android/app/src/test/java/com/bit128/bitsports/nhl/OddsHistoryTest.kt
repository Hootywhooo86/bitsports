package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Test

class OddsHistoryTest {
    @Test fun samplesCloseTogetherReplaceTheLast() {
        var l = emptyList<OddsSample>()
        l = OddsHistory.append(l, OddsSample(0, 0.4f, 0, 0))
        l = OddsHistory.append(l, OddsSample(5_000, 0.42f, 0, 0))
        l = OddsHistory.append(l, OddsSample(25_000, 0.6f, 1, 0))
        assertEquals(listOf(0.42f, 0.6f), l.map { it.awayShare })
    }

    @Test fun keepsAWholeGameAndRoundTrips() {
        var l = emptyList<OddsSample>()
        repeat(OddsHistory.MAX + 50) { l = OddsHistory.append(l, OddsSample(it * 20_000L, 0.5f, 0, 0)) }
        assertEquals(OddsHistory.MAX, l.size)
        val s = listOf(OddsSample(1, 0.613f, 2, 1))
        assertEquals(s, OddsHistory.decode(OddsHistory.encode(s)))
    }

    @Test fun shareTakesTheMarginOut() {
        val o = NhlOddsParser.odds(fixture("odds-CA-2026-11-03.json")).getValue(2026020239L) // EDM +134 @ NJD -162
        assertEquals(0.4f, OddsHistory.share(o)!!, 0.02f)
    }
}
