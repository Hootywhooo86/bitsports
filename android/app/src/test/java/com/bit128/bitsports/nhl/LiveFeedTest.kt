package com.bit128.bitsports.nhl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFeedTest {
    @Test fun pollsLandJustAfterTheCdnCopyExpires() {
        assertEquals(19_700L, CachePacing.nextPollMs(maxAge = 19, age = 0)) // brand-new copy: wait out its life
        assertEquals(6_700L, CachePacing.nextPollMs(maxAge = 19, age = 13))
        assertEquals(1_500L, CachePacing.nextPollMs(maxAge = 19, age = 19)) // already expired: never hammer
        assertEquals(1_500L, CachePacing.nextPollMs(maxAge = 19, age = 40))
        assertNull(CachePacing.nextPollMs(maxAge = null, age = 3))
    }

    @Test fun oilersRadioIs880Ched() {
        val s = NhlRadio.forTeam("EDM")
        assertEquals("880 CHED", s.name)
        assertTrue(s.official)
        assertTrue(s.url.startsWith("https://globalnews.ca/radio/880ched"))
    }

    @Test fun otherTeamsGetASearchAndYourOwnLinkWins() {
        val car = NhlRadio.forTeam("CAR")
        assertFalse(car.official)
        assertTrue(car.url.contains("Carolina+Hurricanes+radio"))
        assertEquals("https://example.com/live", NhlRadio.forTeam("EDM", "https://example.com/live").url)
        assertEquals("880 CHED", NhlRadio.forTeam("EDM", "not a link").name)
    }
}
