package com.bit128.bitsports.nhl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NhlRadioTest {
    @Test fun oilersPlay880ChedInTheApp() {
        val s = NhlRadio.forTeam("EDM")
        assertEquals("880 CHED", s.name)
        assertTrue(s.official)
        assertTrue(s.stream!!.startsWith("https://corus.leanstream.co/CHEDAM-MP3"))
    }

    @Test fun teamsWithoutAStationGetASearchPage() {
        val s = NhlRadio.forTeam("CGY")
        assertTrue(s.url.startsWith("https://tunein.com/search/"))
        assertNull(s.stream)
    }

    @Test fun aPastedStreamPlaysInTheAppAndAPageOpens() {
        assertEquals("https://example.com/live.mp3", NhlRadio.forTeam("CGY", "https://example.com/live.mp3").stream)
        assertNull(NhlRadio.forTeam("CGY", "https://www.sportsnet.ca/960/listen-live/").stream)
    }

    @Test fun streamsAreRecognised() {
        for (u in listOf(
            "https://corus.leanstream.co/CHEDAM-MP3?type=.mp3&playertype=dplayer",
            "https://playerservices.streamtheworld.com/api/livestream-redirect/CFFRAM.mp3",
            "https://radio.example.com/hls/live.m3u8",
            "http://1.2.3.4:8000/stream",
        )) assertTrue(u, NhlRadio.looksLikeStream(u))
        for (u in listOf("https://globalnews.ca/radio/880ched/player/", "https://tunein.com/search/?query=x")) assertFalse(u, NhlRadio.looksLikeStream(u))
    }
}
