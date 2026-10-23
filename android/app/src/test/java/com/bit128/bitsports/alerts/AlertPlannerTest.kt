package com.bit128.bitsports.alerts

import com.bit128.bitsports.fixture
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.NhlParser
import com.bit128.bitsports.nhl.Play
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AlertPlannerTest {
    private val full = NhlParser.playByPlay(fixture("pbp-2026020141.json"), Instant.EPOCH)
    private val allOn = AlertPrefs(pregame = true, goals = true, replays = true, periods = true, recap = true, final = true)
    private val fresh = WatchState.fresh(full.game.id)

    /** The game as a poll would have seen it right after play index [i], still live. */
    private fun upTo(i: Int, transform: (Play) -> Play = { it }): GameDetail =
        full.copy(plays = full.plays.take(i + 1).map(transform), game = full.game.copy(state = GameState.LIVE))

    private val firstGoalIdx = full.plays.indexOfFirst { it.type == "goal" }
    private val firstGoalId = full.plays[firstGoalIdx].eventId
    private fun withoutClip(p: Play) =
        if (p.eventId != firstGoalId) p
        else p.copy(details = JSONObject(p.details.toString()).apply { remove("highlightClipSharingUrl") })

    @Test fun firstPollAlertsEachGoalOnce() {
        val (a1, s1) = AlertPlanner.step(fresh, upTo(firstGoalIdx), allOn)
        assertEquals(1, a1.filterIsInstance<Alert.Goal>().size)
        assertEquals("Macklin Celebrini", a1.filterIsInstance<Alert.Goal>().single().goal.scorer)
        val (a2, _) = AlertPlanner.step(s1, upTo(firstGoalIdx), allOn)
        assertTrue(a2.isEmpty())
    }

    @Test fun survivesRestart() {
        val (_, s1) = AlertPlanner.step(fresh, upTo(firstGoalIdx), allOn)
        val restored = WatchState.fromJson(s1.toJson())
        assertEquals(s1, restored)
        assertTrue(AlertPlanner.step(restored, upTo(firstGoalIdx), allOn).first.isEmpty())
    }

    @Test fun revisedGoalDoesNotRealert() {
        val (_, s1) = AlertPlanner.step(fresh, upTo(firstGoalIdx), allOn)
        val revised = upTo(firstGoalIdx) { p ->
            if (p.eventId == firstGoalId) p.copy(details = JSONObject(p.details.toString()).put("scoringPlayerId", 8483515)) else p
        }
        assertTrue(AlertPlanner.step(s1, revised, allOn).first.none { it is Alert.Goal })
    }

    @Test fun removedGoalIsDroppedSilently() {
        val (_, s1) = AlertPlanner.step(fresh, upTo(firstGoalIdx), allOn)
        val removed = upTo(firstGoalIdx).let { d -> d.copy(plays = d.plays.filter { it.eventId != firstGoalId }) }
        val (a, s2) = AlertPlanner.step(s1, removed, allOn)
        assertTrue(a.isEmpty())
        assertTrue(firstGoalId in s2.alertedGoals)
    }

    @Test fun replayOnceWhenClipAppears() {
        val (a1, s1) = AlertPlanner.step(fresh, upTo(firstGoalIdx, ::withoutClip), allOn)
        assertEquals(1, a1.count { it is Alert.Goal })
        assertEquals(0, a1.count { it is Alert.Replay })
        val (a2, s2) = AlertPlanner.step(s1, upTo(firstGoalIdx), allOn)
        assertEquals(1, a2.count { it is Alert.Replay })
        assertTrue(AlertPlanner.step(s2, upTo(firstGoalIdx), allOn).first.isEmpty())
    }

    @Test fun clipPresentOnFirstSightSendsNoSeparateReplay() {
        // The goal alert itself can carry the link; no second alert for the same clip.
        val (a1, _) = AlertPlanner.step(fresh, upTo(firstGoalIdx), allOn)
        assertEquals(0, a1.count { it is Alert.Replay })
    }

    @Test fun periodEndOnce() {
        val endIdx = full.plays.indexOfFirst { it.type == "period-end" }
        // Watching from the start: one poll just before the buzzer, then the buzzer.
        val (_, s0) = AlertPlanner.step(AlertPlanner.step(fresh, upTo(0), allOn).second, upTo(endIdx - 1), allOn)
        val (a1, s1) = AlertPlanner.step(s0, upTo(endIdx), allOn)
        val pe = a1.filterIsInstance<Alert.PeriodEnd>().single()
        assertEquals("1st", pe.label)
        assertEquals(1, pe.stats.period)
        assertTrue(AlertPlanner.step(s1, upTo(endIdx), allOn).first.none { it is Alert.PeriodEnd })
    }

    @Test fun otPeriodLabel() {
        assertEquals("OT", AlertPlanner.periodLabel(4, "OT"))
        assertEquals("3rd", AlertPlanner.periodLabel(3, "REG"))
        assertEquals("2nd", AlertPlanner.periodLabel(2, "REG"))
        assertEquals("2OT", AlertPlanner.periodLabel(5, "OT"))
    }

    @Test fun finalOnce() {
        val (a1, s1) = AlertPlanner.step(fresh, full, allOn)
        assertEquals(1, a1.count { it is Alert.Final })
        // Seen for the first time already over: just the result, not seven old goals.
        assertEquals(0, a1.count { it is Alert.Goal || it is Alert.CatchUp || it is Alert.PeriodEnd })
        assertTrue(AlertPlanner.step(s1, full, allOn).first.isEmpty())
    }

    @Test fun twoQuickGoalsWhileWatchingAreTwoAlerts() {
        val goalIdx = full.plays.withIndex().filter { it.value.type == "goal" && it.value.periodType != "SO" }.map { it.index }
        val (_, s0) = AlertPlanner.step(fresh, upTo(0), allOn) // watching from puck drop
        val (a, _) = AlertPlanner.step(s0, upTo(goalIdx[1]), allOn) // two goals land between checks
        assertEquals(2, a.count { it is Alert.Goal })
        assertTrue(a.none { it is Alert.CatchUp })
    }

    @Test fun midGameStartCatchesUpOnceThenAlertsEachNewGoal() {
        val goalIdx = full.plays.withIndex().filter { it.value.type == "goal" && it.value.periodType != "SO" }.map { it.index }
        val live = { i: Int -> upTo(i).let { it.copy(game = it.game.copy(state = GameState.LIVE)) } }
        val (a1, s1) = AlertPlanner.step(fresh, live(goalIdx[2]), allOn)
        assertEquals(1, a1.count { it is Alert.CatchUp })
        assertEquals(3, (a1.single { it is Alert.CatchUp } as Alert.CatchUp).goals.size)
        assertTrue(a1.none { it is Alert.Goal || it is Alert.PeriodEnd })
        val (a2, _) = AlertPlanner.step(s1, live(goalIdx[3]), allOn)
        assertEquals(1, a2.count { it is Alert.Goal })
        assertTrue(a2.none { it is Alert.CatchUp })
    }

    @Test fun prefsOffSuppressButRecord() {
        val off = allOn.copy(goals = false)
        val (a, s) = AlertPlanner.step(fresh, upTo(firstGoalIdx), off)
        assertTrue(a.none { it is Alert.Goal })
        assertTrue(firstGoalId in s.alertedGoals)
        assertTrue(AlertPlanner.step(s, upTo(firstGoalIdx), allOn).first.none { it is Alert.Goal })
    }

    private val start = Instant.parse("2026-10-20T23:00:00Z")
    private val fut = full.game.copy(state = GameState.FUT, startUtc = start)

    @Test fun postponedGameGetsNoPregame() {
        assertNull(AlertPlanner.pregame(fresh, fut.copy(scheduleOk = false), start.minus(Duration.ofMinutes(30)), allOn).first)
    }

    @Test fun pregameWindow() {
        assertNull(AlertPlanner.pregame(fresh, fut, start.minus(Duration.ofMinutes(31)), allOn).first)
        val (alert, s) = AlertPlanner.pregame(fresh, fut, start.minus(Duration.ofMinutes(30)), allOn)
        assertNotNull(alert)
        assertNull(AlertPlanner.pregame(s, fut, start.minus(Duration.ofMinutes(29)), allOn).first)
    }

    @Test fun watcherGivesUpAfter90MinWithoutPuckDrop() {
        assertTrue(AlertPlanner.shouldStopWatching(fut, start.plus(Duration.ofMinutes(91))))
        assertFalse(AlertPlanner.shouldStopWatching(fut, start.plus(Duration.ofMinutes(60))))
        assertFalse(AlertPlanner.shouldStopWatching(fut.copy(state = GameState.LIVE), start.plus(Duration.ofMinutes(200))))
        assertTrue(AlertPlanner.shouldStopWatching(fut.copy(state = GameState.OFF), start))
    }

    @Test fun staleLabelAfter45s() {
        val t = Instant.parse("2026-10-20T23:30:00Z")
        assertNull(AlertPlanner.staleLabel(t, t.plusSeconds(44)))
        assertEquals("updated 1m 05s ago", AlertPlanner.staleLabel(t, t.plusSeconds(65)))
        assertEquals("updated 50s ago", AlertPlanner.staleLabel(t, t.plusSeconds(50)))
    }
}
