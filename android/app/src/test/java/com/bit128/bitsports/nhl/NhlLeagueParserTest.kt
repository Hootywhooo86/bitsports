package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NhlLeagueParserTest {
    @Test fun parsesStandings() {
        val rows = NhlLeagueParser.standings(fixture("standings-2026-10-22.json"))
        assertEquals(32, rows.size)
        val edm = rows.first { it.abbrev == "EDM" }
        assertEquals("Pacific", edm.division)
        assertEquals(2, edm.divisionRank)
        assertEquals(15, edm.points)
        assertEquals("7-1-1", edm.record)
        assertEquals("W2", edm.streak)
        assertEquals(8, rows.filter { it.division == "Pacific" }.size)
    }

    @Test fun parsesLeaders() {
        val boards = NhlLeagueParser.leaders(fixture("skater-leaders.json"), fixture("goalie-leaders.json"))
        assertEquals(listOf("Points", "Goals", "Assists", "Goalie wins", "Save %", "Goals against avg"), boards.map { it.label })
        val top = boards.first().leaders.first()
        assertEquals("Connor McDavid", top.name)
        assertEquals("26", top.value)
        assertEquals("EDM", top.team)
        assertEquals(".945", boards.first { it.label == "Save %" }.leaders.first().value)
    }

    @Test fun leadersSurviveAMissingHalf() {
        val boards = NhlLeagueParser.leaders(fixture("skater-leaders.json"), null)
        assertEquals(3, boards.size)
        assertTrue(boards.all { it.leaders.isNotEmpty() })
    }
}
