package com.bit128.bitsports.alerts

import com.bit128.bitsports.UiPrefs
import com.bit128.bitsports.alertPrefs
import com.bit128.bitsports.fixture
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.NhlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class AlertCopyTest {
    private val d = NhlParser.playByPlay(fixture("pbp-2026020141.json"), Instant.EPOCH)
    private val goals = GameAnalysis.goals(d)

    @Test fun goalTitleIsFromTheScorersSide() {
        assertEquals("GOAL! Sharks 1–0", AlertCopy.goalTitle(goals[0], d.game))
        assertEquals("GOAL! Canadiens 1–1", AlertCopy.goalTitle(goals[1], d.game))
    }

    @Test fun goalTextCarriesScorerStrengthAndTime() {
        assertEquals("Macklin Celebrini (4) · even strength · 1st 13:49", AlertCopy.goalText(goals[0]))
    }

    @Test fun goalDetailHasAssistsShotAndGoalie() {
        val unassisted = AlertCopy.goalDetail(goals[0])
        assertTrue(unassisted, unassisted.contains("Unassisted"))
        assertTrue(unassisted, unassisted.contains("Shot: snap"))
        assertTrue(unassisted, unassisted.contains("On Jakub Dobes"))
        val assisted = AlertCopy.goalDetail(goals[1])
        val a = goals[1].assists.first()
        assertTrue(assisted, assisted.contains("Assists: ${a.first} (${a.second})"))
    }

    @Test fun periodEndCopy() {
        val stats = GameAnalysis.periodStats(d, 1)
        assertEquals("End of 1st · Sharks 1, Canadiens 1", AlertCopy.periodTitle(stats, "1st", d.game, goals))
        assertEquals("Shots ${stats.awayShots}–${stats.homeShots} · PP ${stats.awayPp} / ${stats.homePp} · Hits ${stats.awayHits}–${stats.homeHits} · Faceoffs ${stats.awayFaceoffPct}%–${stats.homeFaceoffPct}%",
            AlertCopy.periodText(stats))
    }

    @Test fun finalCopy() {
        assertEquals("FINAL/OT · Sharks 4, Canadiens 3", AlertCopy.finalTitle(d.game, shootout = false))
        assertEquals("FINAL/SO · Sharks 4, Canadiens 3", AlertCopy.finalTitle(d.game, shootout = true))
    }

    @Test fun pregameCopy() {
        assertEquals("Sharks @ Canadiens starts in 30 min", AlertCopy.pregameTitle(d.game))
        assertEquals("Puck drop 7:00 PM", AlertCopy.pregameText(d.game, ZoneId.of("America/Toronto")))
    }

    @Test fun replayCopy() {
        assertEquals("▶ Watch Macklin Celebrini's goal", AlertCopy.replayTitle(goals[0]))
    }

    @Test fun alertPrefsDefaultAllOn() {
        val p = UiPrefs().alertPrefs()
        assertTrue(p.pregame && p.goals && p.replays && p.periods && p.recap && p.final)
        assertEquals("lamp", UiPrefs().goalStyle)
        assertTrue(UiPrefs().goalSound)
    }
}
