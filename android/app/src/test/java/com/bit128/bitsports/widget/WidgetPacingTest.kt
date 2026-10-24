package com.bit128.bitsports.widget

import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.TeamLine
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WidgetPacingTest {
    private val now = Instant.parse("2026-10-24T22:00:00Z").toEpochMilli()
    private fun game(state: GameState, startsInMin: Long) = Game(
        1, state, true, Instant.ofEpochMilli(now + startsInMin * 60_000), TeamLine(1, "CAR", 0, 0), TeamLine(2, "EDM", 0, 0), null,
    )

    @Test fun liveLandsOnTheNhlsNextCopy() {
        assertEquals(now + 6_700, WidgetPacing.next(now, listOf(game(GameState.LIVE, -40)), freshInMs = 6_700))
        assertEquals(now + 20_000, WidgetPacing.next(now, listOf(game(GameState.LIVE, -40)))) // no cache timing: the NHL's ~20s
    }

    @Test fun lastHalfHourBeforePuckDropIsEveryMinute() {
        assertEquals(now + 60_000, WidgetPacing.next(now, listOf(game(GameState.FUT, 20))))
        // Past start time but the feed hasn't flipped it to live yet: keep checking every minute.
        assertEquals(now + 60_000, WidgetPacing.next(now, listOf(game(GameState.PRE, -5))))
    }

    @Test fun laterTodayWakesForThePregameWindowOrQuarterHour() {
        assertEquals(now + 15 * 60_000, WidgetPacing.next(now, listOf(game(GameState.FUT, 180))))
        assertEquals(now + 10 * 60_000, WidgetPacing.next(now, listOf(game(GameState.FUT, 40)))) // wakes as the 30-min window opens
    }

    @Test fun nothingOnStillRefreshesEveryQuarterHour() {
        assertEquals(now + 15 * 60_000, WidgetPacing.next(now, emptyList()))
        assertEquals(now + 15 * 60_000, WidgetPacing.next(now, listOf(game(GameState.OFF, -180))))
    }
}
