package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class NhlExtrasParserTest {
    private fun final() = NhlExtrasParser.extras(
        fixture("landing-2026020152.json"), fixture("rail-2026020152.json"),
        fixture("box-2026020152.json"), fixture("shifts-2026020152.json"),
    )

    private fun upcoming() = NhlExtrasParser.extras(
        fixture("landing-2026020163.json"), fixture("rail-2026020163.json"), fixture("box-2026020163.json"), null,
    )

    @Test fun finishedGameHasLineupsBoxScoreAndInfo() {
        val x = final()
        assertTrue(x.hasLineups)
        assertEquals(18, x.away.skaters.size) // PHI: 12 forwards + 6 defense
        assertEquals(2, x.home.goalies.size)
        assertEquals("U. Luukkonen", x.home.goalies.single { it.starter }.name)
        assertEquals("Rick Tocchet", x.away.coach)
        assertEquals(3, x.away.scratches.size)
        assertEquals(4, x.officials.size)
        assertEquals("KeyBank Center", x.venue)
        val shots = x.gameStats.first { it.label == "Shots" }
        assertEquals("27", shots.away)
        assertEquals("28", shots.home)
        assertEquals(27f / 55f, shots.awayShare, 0.001f)
        assertEquals("1/5", x.gameStats.first { it.label == "Power play" }.away)
        assertTrue(x.shifts.isNotEmpty())
    }

    @Test fun upcomingGameHasMatchupButNoLineup() {
        val x = upcoming()
        assertFalse(x.hasLineups)
        assertEquals("8-2-1", x.away.record)
        assertEquals(10, x.away.recent.size)
        assertEquals("W", x.away.recent.last()) // oldest first: CAR last beat VAN, matching its W1 streak
        assertEquals("W1", x.away.streak)
        assertEquals("C. McDavid", x.home.seasonSkaters.first().name)
        assertEquals("Points", x.leaders.first().label)
        assertEquals("A. Svechnikov", x.leaders.first().awayName)
        assertEquals("5", x.leaders.first().awayValue)
        assertEquals(5, x.seasonStats.size)
        assertEquals("31.0%", x.seasonStats.first { it.label == "Power play" }.home)
        assertEquals("Mike Babcock", x.home.coach)
    }

    @Test fun missingPiecesAreJustEmpty() {
        val x = NhlExtrasParser.extras(null, null, null, null)
        assertNull(x.venue)
        assertFalse(x.hasLineups)
        assertTrue(x.leaders.isEmpty())
    }

    @Test fun readsClockStrings() {
        assertEquals(754, NhlExtrasParser.seconds("12:34"))
        assertNull(NhlExtrasParser.seconds("soon"))
    }

    private fun live(remaining: String, intermission: Boolean = false) = Game(
        id = 2026020152, state = GameState.LIVE, scheduleOk = true, startUtc = Instant.EPOCH,
        away = TeamLine(4, "PHI", 1, 10), home = TeamLine(7, "BUF", 1, 10),
        clock = Clock(period = 2, periodType = "REG", timeRemaining = remaining, inIntermission = intermission, running = true),
    )

    @Test fun onIceFromTheShiftChart() {
        val shifts = final().shifts
        val snap = OnIce.snapshot(live("10:00"), shifts)!!
        assertEquals(6, snap.home.size)
        assertEquals(6, snap.away.size)
        assertTrue(snap.home.any { it.name.endsWith("Luukkonen") })
        assertFalse(snap.lagging)
        assertNull(OnIce.snapshot(live("10:00", intermission = true), shifts))
    }

    @Test fun onIceSaysWhenTheChartIsBehind() {
        val early = final().shifts.filter { it.end <= 300 }
        val snap = OnIce.snapshot(live("10:00"), early)!!
        assertTrue(snap.lagging)
        assertTrue(snap.asOf <= 300)
    }

    @Test fun regularSeasonOvertimeIsFiveMinutes() {
        assertEquals(300, OnIce.periodLength(2026020152, "OT"))
        assertEquals(1200, OnIce.periodLength(2026030111, "OT"))
        assertEquals(1200, OnIce.periodLength(2026020152, "REG"))
    }
}
