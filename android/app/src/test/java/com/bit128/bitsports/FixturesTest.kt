package com.bit128.bitsports

import org.junit.Assert.assertTrue
import org.junit.Test

class FixturesTest {
    @Test fun fixturesLoad() {
        assertTrue(fixture("score-2026-10-20.json").contains("\"games\""))
    }
}
