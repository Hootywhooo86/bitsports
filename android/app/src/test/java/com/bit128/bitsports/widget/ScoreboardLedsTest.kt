package com.bit128.bitsports.widget

import com.bit128.bitsports.nhl.Clock
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.Situation
import com.bit128.bitsports.nhl.TeamLine
import com.bit128.bitsports.ui.HockeyBoard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ScoreboardLedsTest {
    private fun game(id: Long, away: String, home: String, state: GameState, clock: Clock? = null, situation: Situation? = null) = Game(
        id = id, state = state, scheduleOk = true, startUtc = Instant.parse("2026-10-23T01:00:00Z"),
        away = TeamLine(1, away, if (state == GameState.FUT) null else 3, 20), home = TeamLine(2, home, if (state == GameState.FUT) null else 1, 18),
        clock = clock, situation = situation,
    )

    private val games = listOf(
        game(1, "CAR", "EDM", GameState.LIVE, Clock(2, "REG", "08:41", false, true), Situation("1451", "01:10")),
        game(2, "NSH", "BOS", GameState.OFF, Clock(4, "OT", "00:00", false, false)),
        game(3, "MIN", "CGY", GameState.FUT),
    )

    @Test fun oneRowPerGameAcross128() {
        val g = ScoreboardLeds.build(games, setOf("EDM"), HockeyBoard.WHITE, maxGames = 8)
        assertEquals(128, g.cols)
        assertEquals(28, g.rows)
        assertEquals(HockeyBoard.WHITE, g[3, 8]) // EDM's game is underlined
        assertEquals(0, g[3, 18]) // NSH-BOS isn't
    }

    @Test fun capsAtWhatFits() {
        assertEquals(8, ScoreboardLeds.build(games, emptySet(), HockeyBoard.WHITE, maxGames = 1).rows)
    }

    @Test fun shortStatuses() {
        assertEquals("P2 8:41", ScoreboardLeds.status(games[0]).first)
        assertEquals("F/OT", ScoreboardLeds.status(games[1]).first)
        assertTrue(ScoreboardLeds.status(games[2]).first.endsWith("M"))
    }

    @Test fun powerPlayLightsUp() {
        val g = ScoreboardLeds.build(games.take(1), emptySet(), HockeyBoard.WHITE, maxGames = 1)
        assertTrue((70..80).any { x -> (0..6).any { y -> g[x, y] == HockeyBoard.WARN } })
    }
}
