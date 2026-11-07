package com.bit128.bitsports.ui

import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameDetail

/**
 * The final horn, as a 128 x 72 LED moment in acts:
 *
 *   wipe     the winner's colours sweep across, then pull away to show the final score, the
 *            loser's number dimmed to grey
 *   stars    the three stars shoot in from the right, one at a time, and sparkle as they land
 *            beside each name
 *   shutout  (if there was one) a padlock drops over the goalie's name and clicks shut
 *   cup      (if this won the Stanley Cup) the Cup rises through falling confetti
 *
 * Pure, so every frame is tested for spacing like the rest of the boards.
 */
object FinalMoments {
    const val COLS = 128
    const val ROWS = 72
    private const val WIPE = 14
    private const val STAR_EACH = 8
    private const val SHUTOUT = 16
    private const val CUP = 24

    data class Info(
        val game: Game,
        val stars: List<String>,
        /** The winning goalie's name when the loser didn't score. */
        val shutoutGoalie: String? = null,
        val shutout: Boolean = false,
        val cup: Boolean = false,
        val tag: String = "FINAL",
    )

    fun info(d: GameDetail, cup: Boolean = false): Info {
        val g = d.game
        val a = g.away.score ?: 0
        val h = g.home.score ?: 0
        val ot = g.clock?.periodType == "OT" || (g.clock?.period ?: 0) > 3
        val so = g.clock?.periodType == "SO" || d.plays.any { it.periodType == "SO" }
        return Info(g, d.threeStars, shutoutGoalie(d), shutout = minOf(a, h) == 0 && a != h, cup = cup,
            tag = if (so) "FINAL/SO" else if (ot) "FINAL/OT" else "FINAL")
    }

    /** The goalie who faced every shot the losing side took, when they never scored. Shared shutouts get no name. */
    fun shutoutGoalie(d: GameDetail): String? {
        val g = d.game
        val a = g.away.score ?: 0
        val h = g.home.score ?: 0
        if (minOf(a, h) != 0 || a == h) return null
        val loserId = if (a == 0) g.away.id else g.home.id
        val goalies = d.plays.filter { it.teamId == loserId && it.type in setOf("shot-on-goal", "missed-shot") && it.details.has("goalieInNetId") }
            .map { it.details.optInt("goalieInNetId") }.toSet()
        return goalies.singleOrNull()?.let { d.roster[it] }
    }

    fun frames(i: Info): Int = WIPE + STAR_EACH * i.stars.size.coerceAtMost(3) + 6 + (if (i.shutout) SHUTOUT else 0) + (if (i.cup) CUP else 0)

    fun build(i: Info, frame: Int): LedGrid {
        val g = LedGrid(COLS, ROWS)
        val f = frame.coerceAtLeast(0)
        val starsEnd = WIPE + STAR_EACH * i.stars.size.coerceAtMost(3) + 6
        val shutEnd = starsEnd + if (i.shutout) SHUTOUT else 0
        when {
            i.cup && f >= shutEnd -> cup(g, i, f - shutEnd)
            i.shutout && f >= starsEnd -> { result(g, i); shutout(g, i, f - starsEnd) }
            f < WIPE -> wipe(g, i, f)
            else -> { result(g, i); stars(g, i, f - WIPE) }
        }
        return g
    }

    private fun winner(i: Info) = if ((i.game.away.score ?: 0) >= (i.game.home.score ?: 0)) i.game.away.abbrev else i.game.home.abbrev

    /** FINAL, then the score with the loser dimmed, the codes beneath in team colour. */
    private fun result(g: LedGrid, i: Info) {
        val a = i.game.away.score ?: 0
        val h = i.game.home.score ?: 0
        val (ai, hi) = TeamInk.pair(i.game.away.abbrev, i.game.home.abbrev)
        g.textCenter(i.tag, 1, HockeyBoard.WHITE)
        val step = (PixelFont.W + 1) * 3
        val all = "$a-$h"
        val x0 = (COLS - PixelFont.width(all, 3)) / 2
        g.text("$a", x0, 11, if (a >= h) ai else HockeyBoard.DIM, 3)
        g.text("-", x0 + "$a".length * step, 11, HockeyBoard.DIM, 3)
        g.text("$h", x0 + ("$a".length + 1) * step, 11, if (h >= a) hi else HockeyBoard.DIM, 3)
        g.text(i.game.away.abbrev, 4, 14, ai)
        g.textRight(i.game.home.abbrev, COLS - 5, 14, hi)
    }

    /** The winner's stripes sweep in from the left, then pull off to the right, uncovering the score. */
    private fun wipe(g: LedGrid, i: Info, f: Int) {
        val c = HockeyBoard.colors(winner(i)).let { (x, y) ->
            (if (x == 0xFF000000.toInt()) HockeyBoard.DIM else x) to (if (y == 0xFF000000.toInt()) HockeyBoard.DIM else y)
        }
        val inEdge = if (f < 7) (f + 1) * 20 else COLS
        val outEdge = if (f < 7) 0 else (f - 6) * 20
        if (f >= 7) {
            result(g, i)
            // Keep two dark columns between what's uncovered and the curtain.
            g.fill(outEdge - 2, 0, 2, ROWS, 0)
        }
        g.shape()
        for (x in outEdge until inEdge.coerceAtMost(COLS)) for (y in 0 until ROWS) {
            g.put(x, y, if (((x + y) / 6) % 2 == 0) c.first else c.second)
        }
    }

    private val STAR_BIG = arrayOf("...#...", "...#...", "#######", ".#####.", "..###..", ".##.##.", "##...##")
    private val STAR_SMALL = arrayOf("..#..", ".###.", "#####", ".###.", ".#.#.")

    /** Three stars shoot in along their rows and sparkle where they land, then the names light. */
    private fun stars(g: LedGrid, i: Info, f: Int) {
        i.stars.take(3).forEachIndexed { k, name ->
            val y = 34 + k * 12
            val age = f - k * STAR_EACH
            if (age < 0) return@forEachIndexed
            val art = if (k == 0) STAR_BIG else STAR_SMALL
            val landX = 4
            val x = if (age < 6) COLS - (age + 1) * ((COLS - landX) / 6) else landX
            // The tail: gold fading to dark behind it while it flies.
            if (age < 6) {
                g.shape()
                for (t in 1..24) {
                    val c = HeatMap.blend(0xFF000000.toInt(), PixelArt.GOLD, 1f - t / 24f)
                    g.put(x + art[0].length + t, y + art.size / 2, c)
                    if (t % 3 == 0) g.put(x + art[0].length + t, y + art.size / 2 - 1 + (t % 2) * 2, c)
                }
            }
            g.sprite(art, x, y, mapOf('#' to PixelArt.GOLD))
            if (age in 6..9) {
                g.shape()
                for ((dx, dy) in listOf(-2 to -2, 9 to -2, -2 to 8, 9 to 8, 3 to -3)) g.put(landX + dx, y + dy, HockeyBoard.WHITE)
            }
            if (age >= 6) {
                val n = PixelFont.clean(name.substringAfterLast(' ')).take(PixelFont.fits(COLS - 20))
                g.text(n, 18, y, if (k == 0) HockeyBoard.WHITE else HockeyBoard.MUTED)
            }
        }
    }

    /** The padlock drops over the goalie's name and clicks shut. */
    private fun shutout(g: LedGrid, i: Info, f: Int) {
        g.fill(0, 33, COLS, ROWS - 33, 0)
        val shut = f >= 8
        val y = (34 + f).coerceAtMost(40)
        g.sprite(PixelArt.padlock(shut), 8, y, mapOf('s' to PixelArt.SILVER, 'b' to PixelArt.GOLD, 'k' to 0xFF5A3E10.toInt()))
        if (f in 8..10) {
            g.shape()
            for ((dx, dy) in listOf(-3 to 2, 18 to 2, -3 to 12, 18 to 12)) g.put(8 + dx, y + dy, HockeyBoard.WHITE)
        }
        g.text("SHUTOUT", 32, 40, if (shut && f % 4 < 2) HockeyBoard.WARN else HockeyBoard.WHITE, 2)
        val who = i.shutoutGoalie?.let { PixelFont.clean(it.substringAfterLast(' ')) } ?: PixelFont.clean(com.bit128.bitsports.alerts.AlertCopy.nick(winner(i)))
        if (shut) g.text(who.take(PixelFont.fits(COLS - 34)), 32, 58, TeamInk.of(winner(i)))
    }

    /** The Cup rises in the winner's colours through falling confetti. */
    private fun cup(g: LedGrid, i: Info, f: Int) {
        val ink = TeamInk.of(winner(i))
        val colors = listOf(ink, PixelArt.GOLD, HockeyBoard.WHITE, 0xFF4AB8FF.toInt(), 0xFFE05CC0.toInt())
        g.shape()
        for (k in 0 until 60) {
            val x = (PixelArt.rand(k * 3) * COLS).toInt()
            val y = ((PixelArt.rand(k * 3 + 1) * ROWS).toInt() + f * (1 + k % 3)) % ROWS
            g.put(x, y, colors[k % colors.size])
        }
        val top = (ROWS - f * 4).coerceAtLeast(12)
        g.fill(42, top - 2, 44, ROWS - top + 2, 0)
        g.sprite(PixelArt.CUP, 44, top, mapOf('s' to PixelArt.SILVER, 'd' to ink), 2)
        val label = "STANLEY CUP!"
        PixelArt.clearForCenter(g, label, 2)
        g.textCenter(label, 2, if (f % 4 < 2) PixelArt.GOLD else ink)
    }

    /** One frame each for the notification (128 x 44): the padlock or the Cup, beside the words. */
    fun badges(i: Info): List<LedGrid> = listOfNotNull(
        if (i.shutout) LedGrid(COLS, HockeyBoard.ROWS).apply {
            sprite(PixelArt.padlock(true), 8, 14, mapOf('s' to PixelArt.SILVER, 'b' to PixelArt.GOLD, 'k' to 0xFF5A3E10.toInt()))
            text("SHUTOUT", 34, 10, HockeyBoard.WARN, 2)
            val who = i.shutoutGoalie?.let { PixelFont.clean(it.substringAfterLast(' ')) } ?: PixelFont.clean(com.bit128.bitsports.alerts.AlertCopy.nick(winner(i)))
            text(who.take(PixelFont.fits(COLS - 36)), 34, 28, TeamInk.of(winner(i)))
        } else null,
        if (i.cup) LedGrid(COLS, HockeyBoard.ROWS).apply {
            sprite(PixelArt.CUP, 8, 8, mapOf('s' to PixelArt.SILVER, 'd' to TeamInk.of(winner(i))))
            text("STANLEY", 36, 8, PixelArt.GOLD, 2)
            text("CUP!", 36, 26, TeamInk.of(winner(i)), 2)
        } else null,
    )
}
