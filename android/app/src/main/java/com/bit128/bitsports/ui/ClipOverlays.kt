package com.bit128.bitsports.ui

import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.Strength

/**
 * The LED graphics laid over a goal clip, like a broadcast: a score bug in the corner (the score
 * the moment it went in) and a lower third with the scorer, the assists and when. Pure, so their
 * spacing is tested like every other board.
 */
object ClipOverlays {
    /** Corner bug, 72 x 11: "EDM 1-0 NJD" with each code in its team colour and stripes under. */
    fun bug(game: Game, g: GoalEvent): LedGrid {
        val out = LedGrid(72, 11)
        val (ai, hi) = TeamInk.pair(game.away.abbrev, game.home.abbrev)
        val score = "${g.awayScore}-${g.homeScore}"
        out.text(game.away.abbrev, 1, 1, ai)
        out.textCenter(score, 1, HockeyBoard.WHITE)
        out.textRight(game.home.abbrev, 70, 1, hi)
        out.fill(1, 9, PixelFont.width(game.away.abbrev), 1, ai)
        out.fill(71 - PixelFont.width(game.home.abbrev), 9, PixelFont.width(game.home.abbrev), 1, hi)
        return out
    }

    /**
     * Lower third, 128 x 20: a team-colour edge, the scorer and goal number on top, the assists
     * (or what kind of goal) and the time underneath.
     */
    fun lowerThird(g: GoalEvent, frame: Int = 99): LedGrid {
        val out = LedGrid(128, 20)
        val ink = TeamInk.of(g.teamAbbrev)
        val c = HockeyBoard.colors(g.teamAbbrev)
        // Slides in from the left over the first few frames.
        val shift = ((6 - frame).coerceAtLeast(0)) * -20
        out.fill(0 + shift, 0, 2, 20, if (c.first == 0xFF000000.toInt()) ink else c.first)
        out.fill(2 + shift, 0, 2, 20, ink)
        val name = PixelFont.clean(g.scorer.substringAfterLast(' ')) + (g.scorerTotal?.let { " ($it)" } ?: "")
        out.text(name.take(PixelFont.fits(96)), 8 + shift, 2, HockeyBoard.WHITE)
        val kind = when (g.strength) {
            Strength.PP -> "PPG"; Strength.SH -> "SHG"; Strength.EN -> "ENG"; else -> null
        }
        kind?.let { out.textRight(it, 125 + shift, 2, HockeyBoard.WARN) }
        val assists = g.assists.joinToString(", ") { PixelFont.clean(it.first.substringAfterLast(' ')) }.ifEmpty { "UNASSISTED" }
        val time = AlertPlanner.periodLabel(g.period, g.periodType).uppercase() + " " + g.timeInPeriod
        val room = PixelFont.fits(118 - PixelFont.width(time) - 6)
        out.text(assists.take(room), 8 + shift, 11, HockeyBoard.MUTED)
        out.textRight(time, 125 + shift, 11, HockeyBoard.DIM)
        return out
    }
}
