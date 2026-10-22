package com.bit128.bitsports.nhl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class StrengthsTest {
    private fun g(code: String, time: String? = null, intermission: Boolean = false) = Game(
        id = 1, state = GameState.LIVE, scheduleOk = true, startUtc = Instant.EPOCH,
        away = TeamLine(12, "CAR", 1, 10), home = TeamLine(22, "EDM", 1, 10),
        clock = Clock(2, "REG", "08:41", intermission, true), situation = Situation(code, time),
    )

    @Test fun powerPlays() {
        assertEquals("EDM PP 1:23", Strengths.banner(g("1451", "01:23"))!!.text)
        assertEquals("CAR PP 5 ON 3 0:45", Strengths.banner(g("1531", "00:45"))!!.text)
        assertEquals("CAR", Strengths.banner(g("1541"))!!.team)
    }

    @Test fun pulledGoalieIsNotAPowerPlay() {
        val b = Strengths.banner(g("0651"))!!
        assertEquals(Strengths.Kind.EMPTY_NET, b.kind)
        assertEquals("CAR NET EMPTY", b.text)
    }

    @Test fun evenAndQuietStates() {
        assertNull(Strengths.banner(g("1551")))
        assertEquals("4 ON 4", Strengths.banner(g("1441"))!!.text)
        assertNull(Strengths.banner(g("1451", intermission = true)))
    }
}
