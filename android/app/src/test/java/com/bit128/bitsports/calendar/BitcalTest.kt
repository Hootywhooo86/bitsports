package com.bit128.bitsports.calendar

import com.bit128.bitsports.fixture
import com.bit128.bitsports.nhl.NhlSeasonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Oilers' real 2026-27 schedule, as it lands in Bitcal. */
class BitcalTest {
    private val season = NhlSeasonParser.season(fixture("season-EDM.json"))
    private val plan = GameCalendar.plan(mapOf("EDM" to season))

    @Test fun everyRegularSeasonGameAndNoPreseason() {
        assertEquals(88, season.size)
        assertEquals(84, plan.size)
        assertEquals(plan.sortedBy { it.startMs }, plan)
    }

    @Test fun finalsReadFromYourSide() {
        val ot = plan.first { it.gameId == 2026020004L }
        assertEquals("Oilers 5–6 Canucks · OTL (OT)", ot.title)
        assertEquals("Rogers Place", ot.location)
        assertTrue(ot.description.startsWith("TV: SN\nRadio: 880 CHED"))
        assertEquals("Oilers 9–7 Canucks · W", plan.first { it.gameId == 2026020015L }.title)
        assertEquals(GameCalendar.LENGTH_MS, ot.endMs - ot.startMs)
    }

    @Test fun upcomingShowHomeOrAway() {
        val ahead = plan.filter { !it.title.contains("–") }
        assertTrue(ahead.isNotEmpty())
        assertTrue(ahead.all { it.title.startsWith("Oilers vs ") || it.title.startsWith("Oilers @ ") })
    }

    @Test fun gameBetweenTwoOfYourTeamsOnce() {
        val twice = GameCalendar.plan(mapOf("EDM" to season, "VAN" to season.filter { it.game.home.abbrev == "VAN" || it.game.away.abbrev == "VAN" }))
        assertEquals(84, twice.size)
    }

    @Test fun rowsMatchBitcalMirror() {
        val row = BitcalRows.rows(plan).first { it.getString("ext_id") == "nhl:2026020004" }
        assertEquals("2026-09-30T02:00:00Z", row.getString("starts_at"))
        assertEquals("2026-09-30T04:40:00Z", row.getString("ends_at"))
        assertEquals("UTC", row.getString("tz"))
        assertFalse(row.getBoolean("all_day"))
        assertEquals("bitsports", row.getJSONObject("ext").getString("provider"))
        assertEquals(setOf("ext_id", "title", "starts_at", "ends_at", "all_day", "tz", "location", "notes", "ext"), row.keys().asSequence().toSet())
    }

    @Test fun unfollowedGamesAreCancelled() {
        val c = BitcalRows.cancelled(listOf(2026020004L)).single()
        assertEquals("nhl:2026020004", c.getString("ext_id"))
        assertTrue(c.getBoolean("cancelled"))
    }
}
