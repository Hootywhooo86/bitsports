package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WatchAndClipsTest {
    @Test fun clipIdFromTheShareLink() {
        assertEquals(6407872711112L, NhlClips.idFrom("https://nhl.com/video/edm-njd-bouchard-scores-goal-against-connor-hellebuyck-6407872711112"))
        assertNull(NhlClips.idFrom(null))
        assertNull(NhlClips.idFrom("https://nhl.com/video/no-id"))
    }

    /** The real answer for Bouchard's goal: the HTTPS 720p MP4 wins. */
    @Test fun picksTheHttpsMp4() {
        val c = NhlClips.parse(fixture("clip-6407872711112.json"))
        assertEquals("Bouchard breaks the ice", c.title)
        assertTrue(c.mp4!!.startsWith("https://"))
        assertTrue(c.playable == c.mp4)
        assertTrue(c.hls!!.startsWith("https://"))
    }

    private val game = Game(2026020239, GameState.LIVE, true, Instant.now(), TeamLine(1, "EDM", 1, 9), TeamLine(2, "NJD", 0, 7), null)

    @Test fun freeFirstThenCanadaThenTheRest() {
        val b = listOf(Broadcast("SN", "CA", "N"), Broadcast("SNW", "CA", "H"), Broadcast("MSG", "US", "H"), Broadcast("CBC", "CA", "N"), Broadcast("ESPN+", "US", "N"))
        val o = WatchOptions.of(game, b, NhlRadio.forTeam("EDM"), hasClips = true)
        assertEquals("CBC Gem", o.first().name)
        assertTrue(o.take(4).all { it.free })
        assertEquals(1, o.count { it.name == "Sportsnet+" }) // SN and SNW are both Sportsnet+
        val names = o.map { it.name }
        assertTrue(names.indexOf("Sportsnet+") < names.indexOf("ESPN"))
        assertNull(o.first { it.name == "MSG" }.url) // a regional channel: on TV
        assertTrue(o.any { it.kind == WatchOptions.Kind.RADIO && it.url!!.contains("CHEDAM") })
    }
}
