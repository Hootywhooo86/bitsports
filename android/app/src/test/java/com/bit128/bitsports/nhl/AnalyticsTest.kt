package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AnalyticsTest {
    @Test fun winProbabilityBasics() {
        val r = WinProb.GOALS_PER_60
        assertEquals(0.5, WinProb.home(0, 3600, r, r), 0.001)
        // Up two with five minutes left: very likely.
        assertTrue(WinProb.home(2, 300, r, r) > 0.95)
        // Down one with a minute left: not much hope.
        assertTrue(WinProb.home(-1, 60, r, r) < 0.1)
        // A better team starts ahead.
        assertTrue(WinProb.home(0, 3600, r * 1.2, r / 1.2) > 0.55)
    }

    @Test fun ratesMatchTheOddsAtPuckDrop() {
        val (h, a) = WinProb.rates(0.62)
        assertEquals(0.62, WinProb.home(0, 3600, h, a), 0.005)
    }

    @Test fun xgLooksLikeHockey() {
        // From the crease beats from the point; a tip-in from the same spot is less likely than a wrister.
        assertTrue(Xg.value(8.0, 10.0, "wrist", false, false) > 0.15)
        assertTrue(Xg.value(55.0, 30.0, "slap", false, false) < 0.05)
        assertTrue(Xg.value(8.0, 10.0, "tip-in", false, false) < Xg.value(8.0, 10.0, "wrist", false, false))
        assertTrue(Xg.value(25.0, 20.0, "wrist", true, false) > Xg.value(25.0, 20.0, "wrist", false, false))
    }

    @Test fun aRealGamesChancesAndSwings() {
        val d = NhlParser.playByPlay(fixture("pbp-2026020141.json"), Instant.now())
        val c = Chances.of(d)
        assertTrue(c.awayXg > 0.5 && c.homeXg > 0.5)
        assertEquals(c.awayXg, c.race.last().second, 0.0001)
        val s = WinProb.summary(d, 0.55, d.game.home.abbrev)
        assertTrue(s.now == 0.0 || s.now == 1.0 || s.now == 0.5)
        assertTrue(s.timeline.size > 60)
        assertTrue(s.biggest != null && s.biggest!!.change > 0)
    }

    /** MoneyPuck's real file, 9 November 2026. */
    @Test fun tapeFromMoneyPuck() {
        val t = TapeParser.parse(fixture("moneypuck-teams-2026.csv"))
        assertEquals(32, t.size)
        val edm = t.getValue("EDM")
        assertEquals(0.49, edm.chanceShare, 0.001)
        assertEquals(1.07, edm.finishing, 0.01)
        assertEquals(103.1, edm.pdo, 0.1)
        assertTrue(edm.ranks.getValue("finishing") <= 3)
        assertEquals((1..32).toSet(), t.values.map { it.ranks.getValue("pdo") }.toSet())
    }
}
