package com.bit128.bitsports.nhl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NhlTeamsTest {
    @Test fun all32() {
        assertEquals(32, NhlTeams.all.size)
        assertEquals(32, NhlTeams.all.map { it.abbrev }.toSet().size)
        assertEquals("Mammoth", NhlTeams.byAbbrev("UTA")!!.nickname)
    }

    @Test fun matchesLooseTeamText() {
        listOf("Edmonton Oilers", "oilers", "EDM", "edmonton", "  Oilers  ").forEach {
            assertEquals(it, "EDM", NhlTeams.match(it)?.abbrev)
        }
        assertEquals("UTA", NhlTeams.match("Utah")?.abbrev)
        assertEquals("TOR", NhlTeams.match("Leafs")?.abbrev)
        assertEquals("MTL", NhlTeams.match("Habs")?.abbrev)
        assertEquals("MTL", NhlTeams.match("Montréal Canadiens")?.abbrev)
        assertNull(NhlTeams.match("New York"))
        assertNull(NhlTeams.match("banana"))
        assertNull(NhlTeams.match(""))
    }
}
