package com.bit128.bitsports.ui

import com.bit128.bitsports.fixture
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlPlayer
import com.bit128.bitsports.nhl.Strength
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoalSpecialTest {
    private fun goal(id: Int, scorer: String, strength: Strength = Strength.EV, team: String = "EDM") =
        GoalEvent(1, id, team, scorer, null, emptyList(), strength, null, null, 1, "REG", "10:00", 0, 0, null)

    @Test fun thirdGoalIsAHatTrick() {
        val goals = listOf(goal(1, "Leon Draisaitl"), goal(2, "Connor McDavid"), goal(3, "Leon Draisaitl"), goal(4, "Leon Draisaitl", Strength.PP))
        assertEquals(GoalSpecial.HatTrick(3), GoalSpecial.of(goals[3], goals))
        // Only goals up to this one count.
        assertNull(GoalSpecial.of(goals[2], goals))
    }

    @Test fun emptyNetAndPowerPlay() {
        assertEquals(GoalSpecial.EmptyNet, GoalSpecial.of(goal(1, "A", Strength.EN), emptyList()))
        assertEquals(GoalSpecial.PowerPlay, GoalSpecial.of(goal(1, "A", Strength.PP), emptyList()))
        assertNull(GoalSpecial.of(goal(1, "A", Strength.SH), emptyList()))
    }

    @Test fun milestones() {
        assertEquals("1ST NHL GOAL", GoalSpecial.milestone(1))
        assertEquals("50TH GOAL", GoalSpecial.milestone(50))
        assertEquals("150TH GOAL", GoalSpecial.milestone(150))
        assertNull(GoalSpecial.milestone(250))
        assertEquals("500TH GOAL", GoalSpecial.milestone(500))
        assertEquals("111TH", GoalSpecial.ordinal(111))
        // A milestone beats a hat trick.
        assertEquals(GoalSpecial.Milestone("500TH GOAL"), GoalSpecial.of(goal(3, "A"), listOf(goal(1, "A"), goal(2, "A"), goal(3, "A")), careerGoals = 500))
    }

    /** McDavid's real page, November 2026: 418 career goals with 9 this season. */
    @Test fun careerGoalsSwapInTheLiveSeasonTotal() {
        val json = fixture("player-8478402.json")
        assertEquals(418, NhlPlayer.careerGoals(json, 9))
        assertEquals(419, NhlPlayer.careerGoals(json, 10))
    }
}
