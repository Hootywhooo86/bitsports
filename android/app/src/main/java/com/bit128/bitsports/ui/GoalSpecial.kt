package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.Strength

/**
 * A goal that deserves more than the usual moment. The biggest one wins: a career milestone,
 * then a hat trick (or more), then an empty-netter, then a power-play goal.
 */
sealed class GoalSpecial {
    /** "1ST NHL GOAL", "100TH GOAL". */
    data class Milestone(val label: String) : GoalSpecial()
    data class HatTrick(val goals: Int) : GoalSpecial()
    object EmptyNet : GoalSpecial()
    object PowerPlay : GoalSpecial()

    companion object {
        /**
         * [inGame]: every goal in the game so far, this one included. [careerGoals]: the scorer's
         * regular-season career total including this goal, when known.
         */
        fun of(goal: GoalEvent, inGame: List<GoalEvent>, careerGoals: Int? = null): GoalSpecial? {
            milestone(careerGoals)?.let { return Milestone(it) }
            val upToNow = inGame.filter { it.eventId <= goal.eventId }
            val mine = upToNow.count { it.scorer == goal.scorer && it.teamAbbrev == goal.teamAbbrev }
            if (mine >= 3 && goal.scorer != "Unknown") return HatTrick(mine)
            return when (goal.strength) {
                Strength.EN -> EmptyNet
                Strength.PP -> PowerPlay
                else -> null
            }
        }

        /** First goal, every 50th up to 200, then every 100th. */
        fun milestone(career: Int?): String? = when {
            career == null || career <= 0 -> null
            career == 1 -> "1ST NHL GOAL"
            career % 100 == 0 || (career < 200 && career % 50 == 0) -> "${ordinal(career)} GOAL"
            else -> null
        }

        fun ordinal(n: Int): String = n.toString() + when {
            n % 100 in 11..13 -> "TH"
            n % 10 == 1 -> "ST"
            n % 10 == 2 -> "ND"
            n % 10 == 3 -> "RD"
            else -> "TH"
        }
    }
}
