package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class PlayTextTest {
    private val d = NhlParser.playByPlay(fixture("pbp-2026020141.json"), Instant.EPOCH)

    @Test fun feedIsNewestFirstAndSkipsBookkeeping() {
        val feed = PlayText.feed(d, limit = 500)
        assertTrue(feed.isNotEmpty())
        assertTrue(feed.zipWithNext().all { (a, b) -> a.play.eventId != b.play.eventId })
        assertTrue(feed.none { it.play.type == "period-start" })
        assertEquals(d.plays.last { it.type != "game-end" && it.periodType != "SO" && it.type != "delayed-penalty" && it.type != "period-start" }.eventId, feed.first().play.eventId)
    }

    @Test fun goalsNameScorerAndAssists() {
        val goal = d.plays.first { it.type == "goal" }
        val text = PlayText.describe(d, goal)
        assertTrue(text, text.startsWith("GOAL! "))
        val scorer = d.roster[goal.details.optInt("scoringPlayerId")]!!
        assertTrue(text, text.contains(scorer))
    }

    @Test fun shotsNameTheGoalie() {
        val shot = d.plays.first { it.eventId == 74 }
        assertTrue(PlayText.describe(d, shot).contains("saved by"))
    }
}
