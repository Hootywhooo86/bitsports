package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.Clock
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.PeriodStats
import com.bit128.bitsports.nhl.Strength
import com.bit128.bitsports.nhl.TeamLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LedScenesTest {
    private val game = Game(
        id = 2026020163, state = GameState.LIVE, scheduleOk = true, startUtc = Instant.EPOCH,
        away = TeamLine(12, "CAR", 1, 20), home = TeamLine(22, "EDM", 2, 18),
        clock = Clock(2, "REG", "08:41", false, true),
    )
    private val goal = GoalEvent(
        gameId = game.id, eventId = 1, teamAbbrev = "EDM", scorer = "Connor McDavid", scorerTotal = 12,
        assists = listOf("Leon Draisaitl" to 20), strength = Strength.PP, shotType = "wrist", goalie = null,
        period = 2, periodType = "REG", timeInPeriod = "11:19", awayScore = 1, homeScore = 2, clipUrl = null,
    )

    private fun check(s: LedScenes.Scene) {
        assertTrue(s.frames.isNotEmpty())
        assertTrue(s.strips.isNotEmpty())
        s.frames.forEach { assertEquals(128, it.cols); assertEquals(44, it.rows) }
        s.strips.forEach { assertEquals(128, it.cols); assertEquals(16, it.rows) }
        // Every frame actually differs from the one before, or it isn't an animation.
        s.frames.zipWithNext().forEach { (a, b) -> assertFalse(a.cells.contentEquals(b.cells)) }
        // Small enough for a notification: board frames at cell 3 are ~100 KB each in RGB_565.
        assertTrue(s.frames.size <= 4 && s.strips.size <= 4)
    }

    @Test fun goalScene() = check(LedScenes.goal(goal, game, HockeyBoard.WHITE))

    @Test fun periodScene() = check(LedScenes.periodEnd(
        PeriodStats(2, "REG", 11, 9, 0, 1, 8, 12, "0/1", "1/2", 47, 53), "2nd", game, 1, 2, HockeyBoard.WHITE,
    ))

    @Test fun finalSceneHasAFramePerStar() {
        val s = LedScenes.final(game.copy(state = GameState.OFF), false, listOf("Connor McDavid", "Seth Jarvis", "Stuart Skinner"), HockeyBoard.WHITE)
        check(s)
        assertEquals(4, s.frames.size)
    }

    @Test fun liveSceneShowsTheLastGoal() {
        assertEquals(2, LedScenes.live(game, goal, HockeyBoard.WHITE).frames.size)
        assertEquals(4, LedScenes.live(game.copy(clock = Clock(1, "REG", "00:00", true, false)), null, HockeyBoard.WHITE).frames.size)
    }

    @Test fun boardTextLosesAccents() {
        assertEquals("MONTREAL", PixelFont.clean("Montréal"))
        assertEquals("STUTZLE", PixelFont.clean("Stützle"))
        assertEquals("A B", PixelFont.clean("a ~ b"))
    }
}
