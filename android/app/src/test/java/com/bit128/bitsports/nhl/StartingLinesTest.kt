package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** The Oilers' real page for NYR @ EDM, 9 November 2026 (lines via Jason Gregor). */
class StartingLinesTest {
    private val lines = StartingLinesParser.parse(fixture("dfo-edmonton-oilers.html"), "EDM")!!

    @Test fun linesPairsGoaliesAndUnits() {
        assertEquals(listOf("Vasily Podkolzin", "Connor McDavid", "Leon Draisaitl"), lines.forwards[0])
        assertEquals(4, lines.forwards.size)
        assertEquals(listOf("Mattias Ekholm", "Evan Bouchard"), lines.defense[0])
        assertEquals(3, lines.defense.size)
        assertEquals("Devon Levi", lines.goalies.first())
        assertEquals(listOf("Zach Hyman", "Ryan Nugent-Hopkins", "Leon Draisaitl", "Connor McDavid", "Evan Bouchard"), lines.pp[0])
        assertEquals(2, lines.pk.size)
        assertTrue("Frederik Andersen" in lines.injured)
        assertEquals("Jason Gregor", lines.source)
        assertEquals(Instant.parse("2026-11-09T18:18:25.913Z"), lines.updated)
    }

    /** Daily Faceoff's page names for every team. */
    @Test fun everyTeamsPage() {
        val dfo = mapOf("ANA" to "anaheim-ducks", "MTL" to "montreal-canadiens", "STL" to "st-louis-blues", "UTA" to "utah-mammoth",
            "VGK" to "vegas-golden-knights", "TBL" to "tampa-bay-lightning", "NJD" to "new-jersey-devils", "LAK" to "los-angeles-kings",
            "CBJ" to "columbus-blue-jackets", "TOR" to "toronto-maple-leafs", "DET" to "detroit-red-wings", "SJS" to "san-jose-sharks")
        dfo.forEach { (abbrev, slug) -> assertEquals(slug, StartingLinesParser.slug(NhlTeams.byAbbrev(abbrev)!!.fullName)) }
    }

    @Test fun newLinesAreTheOnesNotTogetherLastTime() {
        val before = lines.copy(forwards = listOf(lines.forwards[0], listOf("A", "B", "C")) + lines.forwards.drop(2))
        assertEquals(setOf("f2"), StartingLinesParser.changed(lines, before))
        assertEquals(emptySet<String>(), StartingLinesParser.changed(lines, null))
    }
}
