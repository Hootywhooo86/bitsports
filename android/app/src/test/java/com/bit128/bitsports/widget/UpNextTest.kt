package com.bit128.bitsports.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class UpNextTest {
    private val edm = ZoneId.of("America/Edmonton")
    private val now = Instant.parse("2026-11-09T18:00:00Z") // Monday 11:00 AM in Edmonton

    @Test fun tonightTomorrowWeekdayThenDate() {
        assertEquals("TONIGHT 7:00 PM", UpNext.whenText(Instant.parse("2026-11-10T02:00:00Z"), now, edm))
        assertEquals("TOMORROW 7:30 PM", UpNext.whenText(Instant.parse("2026-11-11T02:30:00Z"), now, edm))
        assertEquals("FRI 5:00 PM", UpNext.whenText(Instant.parse("2026-11-14T00:00:00Z"), now, edm))
        assertEquals("NOV 18 7:00 PM", UpNext.whenText(Instant.parse("2026-11-19T02:00:00Z"), now, edm))
    }

    @Test fun howLong() {
        assertEquals("in 8h", UpNext.inText(Instant.parse("2026-11-10T02:00:00Z"), now, edm))
        assertEquals("tomorrow", UpNext.inText(Instant.parse("2026-11-11T02:30:00Z"), now, edm))
        assertEquals("in 4 days", UpNext.inText(Instant.parse("2026-11-14T00:00:00Z"), now, edm))
    }
}
