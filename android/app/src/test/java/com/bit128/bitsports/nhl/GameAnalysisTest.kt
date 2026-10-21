package com.bit128.bitsports.nhl

import com.bit128.bitsports.fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GameAnalysisTest {
    private val d = NhlParser.playByPlay(fixture("pbp-2026020141.json"), Instant.EPOCH)

    @Test fun firstGoalHasFullInfo() {
        val g = GameAnalysis.goals(d).first()
        assertEquals("SJS", g.teamAbbrev)
        assertEquals("Macklin Celebrini", g.scorer)
        assertEquals(4, g.scorerTotal)
        assertTrue(g.assists.isEmpty())
        assertEquals(Strength.EV, g.strength)
        assertEquals("snap", g.shotType)
        assertEquals("Jakub Dobes", g.goalie)
        assertEquals(1, g.period)
        assertEquals("13:49", g.timeInPeriod)
        assertEquals(1, g.awayScore)
        assertEquals(0, g.homeScore)
        assertTrue(g.clipUrl!!.startsWith("https://nhl.com/video/"))
    }

    @Test fun assistsCarrySeasonTotals() {
        val g = GameAnalysis.goals(d)[1]
        assertEquals(5, g.assists.first().second)
        assertEquals(d.roster[8483515], g.assists.first().first)
    }

    @Test fun goalCountMatchesFinalScore() {
        assertEquals(7, GameAnalysis.goals(d).size)
    }

    @Test fun periodStatsAddUp() {
        val periods = (1..4).map { GameAnalysis.periodStats(d, it) }
        assertEquals(30, periods.sumOf { it.awayShots })
        assertEquals(34, periods.sumOf { it.homeShots })
        assertEquals(4, periods.sumOf { it.awayGoals })
        assertEquals(3, periods.sumOf { it.homeGoals })
        assertEquals("OT", periods[3].periodType)
    }

    @Test fun shotMapHasEveryShotAndGoal() {
        val shots = GameAnalysis.shots(d)
        assertEquals(7, shots.count { it.isGoal })
        assertEquals(64, shots.size)
    }

    @Test fun shootoutGoalsAreNotGoals() {
        val so = NhlParser.playByPlay(fixture("pbp-shootout.json"), Instant.EPOCH)
        val goals = GameAnalysis.goals(so)
        assertTrue(goals.none { it.periodType == "SO" })
        assertEquals(6, goals.size)
    }

    @Test fun strengthFromSituationCode() {
        // digits: away goalie, away skaters, home skaters, home goalie
        assertEquals(Strength.PP, GameAnalysis.strength("1451", scoringIsHome = true))
        assertEquals(Strength.SH, GameAnalysis.strength("1451", scoringIsHome = false))
        assertEquals(Strength.EN, GameAnalysis.strength("0651", scoringIsHome = true))
        assertEquals(Strength.EV, GameAnalysis.strength("1551", scoringIsHome = false))
        assertEquals(Strength.EV, GameAnalysis.strength(null, scoringIsHome = false))
    }
}
