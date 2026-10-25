package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.NhlTeams
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeamInkTest {
    @After fun clear() = TeamInk.reset()

    private fun lum(v: Int) = 0.299f * (v shr 16 and 0xFF) / 255f + 0.587f * (v shr 8 and 0xFF) / 255f + 0.114f * (v and 0xFF) / 255f

    @Test fun everyTeamReadsOnBlackAndNoneIsWhite() {
        for (t in NhlTeams.all) {
            val (main, spare) = TeamInk.DEFAULTS.getValue(t.abbrev)
            for (c in listOf(main, spare)) {
                assertTrue("${t.abbrev} too dark", lum(c) > 0.30f)
                assertTrue("${t.abbrev} is white", TeamInk.alike(c, HockeyBoard.WHITE).not())
                assertFalse("${t.abbrev} looks like a goal", c == HeatMap.GOAL)
            }
        }
    }

    @Test fun leafsRedWingsAndLightningAreNotWhiteAnyMore() {
        for (a in listOf("TOR", "DET", "TBL", "WPG")) assertFalse(a, TeamInk.alike(TeamInk.of(a), HockeyBoard.WHITE))
    }

    @Test fun noGameHasTwoLookalikeTeams() {
        for (a in NhlTeams.all) for (h in NhlTeams.all) if (a != h) {
            val (x, y) = TeamInk.pair(a.abbrev, h.abbrev)
            assertFalse("${a.abbrev} @ ${h.abbrev}", TeamInk.alike(x, y))
        }
    }

    @Test fun yourPickWins() {
        TeamInk.reset(mapOf("EDM" to 0xFF7ED957.toInt()))
        assertEquals(0xFF7ED957.toInt(), TeamInk.of("edm"))
        assertEquals(0xFF7ED957.toInt(), TeamInk.pair("CGY", "EDM").second)
    }
}
