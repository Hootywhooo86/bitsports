package com.bit128.bitsports.ui

import com.bit128.bitsports.fixture
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.NhlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class BoardFlashesTest {
    private val full = NhlParser.playByPlay(fixture("pbp-2026020141.json"), Instant.now()).let { it.copy(game = it.game.copy(state = GameState.LIVE)) }

    @Test fun aShotFromTheSlotIsABigSave() {
        val i = full.plays.indexOfFirst { p -> p.type == "shot-on-goal" && p.x != null && p.y != null &&
            kotlin.math.hypot(89.0 - kotlin.math.abs(p.x!!), p.y!!.toDouble()) <= BoardFlashes.SLOT_FT }
        val f = BoardFlashes.detect(full.copy(plays = full.plays.take(i)), full.copy(plays = full.plays.take(i + 1)))!!
        assertEquals(HockeyBoard.Flash.Kind.SAVE, f.kind)
        // The goalie who made it is on the other side from the shooter.
        assertEquals(full.plays[i].teamId != full.game.home.id, f.home)
    }

    @Test fun aHit() {
        val i = full.plays.indexOfFirst { it.type == "hit" }
        val f = BoardFlashes.detect(full.copy(plays = full.plays.take(i)), full.copy(plays = full.plays.take(i + 1)))!!
        assertEquals(HockeyBoard.Flash.Kind.HIT, f.kind)
    }

    @Test fun nothingOnFirstLookOrWhenNothingHappened() {
        assertNull(BoardFlashes.detect(null, full))
        assertNull(BoardFlashes.detect(full, full))
    }
}
