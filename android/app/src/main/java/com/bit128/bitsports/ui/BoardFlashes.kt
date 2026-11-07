package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.GameDetail
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Spots the quick moments worth a flash on the jumbotron between two polls of a game: a big
 * save (a shot on goal from the slot, within 20 feet) or a hit. Pure, so it's tested.
 */
object BoardFlashes {
    const val SLOT_FT = 20.0

    fun detect(prev: GameDetail?, cur: GameDetail): HockeyBoard.Flash? {
        if (prev == null || prev.game.id != cur.game.id || !cur.game.state.isLive) return null
        val seen = prev.plays.mapTo(HashSet()) { it.eventId }
        val fresh = cur.plays.filter { it.eventId !in seen }
        // A save beats a hit; the latest of each kind.
        fresh.lastOrNull { it.type == "shot-on-goal" && it.x != null && it.y != null && hypot(89.0 - abs(it.x!!), it.y!!.toDouble()) <= SLOT_FT }?.let { p ->
            val shooterHome = p.teamId == cur.game.home.id
            val goalie = if (shooterHome) cur.game.away.abbrev else cur.game.home.abbrev
            return HockeyBoard.Flash(HockeyBoard.Flash.Kind.SAVE, home = !shooterHome, ink = TeamInk.of(goalie), id = cur.game.id * 10_000 + p.eventId)
        }
        fresh.lastOrNull { it.type == "hit" }?.let { p ->
            val home = p.teamId == cur.game.home.id
            val team = if (home) cur.game.home.abbrev else cur.game.away.abbrev
            return HockeyBoard.Flash(HockeyBoard.Flash.Kind.HIT, home = home, ink = TeamInk.of(team), id = cur.game.id * 10_000 + p.eventId)
        }
        return null
    }
}
