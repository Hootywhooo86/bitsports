package com.bit128.bitsports.ui

import com.bit128.bitsports.fantasy.FantasyNight
import com.bit128.bitsports.fantasy.Scoring
import kotlin.math.cos
import kotlin.math.sin

/**
 * The end-of-night Fantasy Report on the 128 x 44 LED board: a title card, then each player who
 * played with his line and points rolling up like a slot machine, gold and a crown for the MVP,
 * a rain cloud for a zero, and the list's total with fireworks for a big night.
 */
object FantasyReportLeds {
    const val COLS = HockeyBoard.COLS
    const val ROWS = HockeyBoard.ROWS
    /** Frames a player card counts up over. */
    const val COUNT = 10
    /** A list night this big gets fireworks. */
    const val BIG_NIGHT = 20.0

    private val CROWN = arrayOf("#..#..#", "#######", "#######")
    private val CLOUD = arrayOf("..####...", ".######..", "#########", ".#######.")

    fun title(listName: String, date: String): LedGrid = LedGrid(COLS, ROWS).apply {
        textCenter("FANTASY", 2, PixelArt.GOLD, 2)
        textCenter("REPORT", 18, HockeyBoard.WHITE, 2)
        textCenter((PixelFont.clean(listName) + " · " + date).take(PixelFont.fits(COLS - 4)), 35, HockeyBoard.MUTED)
    }

    /** One player's card; [frame] < [COUNT] while the points are still rolling up. */
    fun player(r: FantasyNight.Row, mvp: Boolean, frame: Int = COUNT): LedGrid {
        val g = LedGrid(COLS, ROWS)
        val ink = TeamInk.of(r.player.team)
        val name = PixelFont.clean(r.player.lastName).take(PixelFont.fits(80))
        g.text(name, 2, 2, if (mvp) PixelArt.GOLD else HockeyBoard.WHITE)
        g.textRight(r.player.team, COLS - 3, 2, ink)
        if (mvp) g.sprite(CROWN, 2 + PixelFont.width(name) + 4, 4, mapOf('#' to PixelArt.GOLD))
        val stats = PixelFont.clean(FantasyNight.statText(r.line, r.player.isGoalie).replace(" · ", " ")).take(PixelFont.fits(COLS - 4))
        g.text(stats, 2, 13, HockeyBoard.MUTED)
        // The points roll up from zero.
        val shown = if (frame >= COUNT) r.points else r.points * frame / COUNT
        val pts = Scoring.fmt(shown) + " PTS"
        val color = when { mvp -> PixelArt.GOLD; r.points <= 0 -> HockeyBoard.DIM; else -> ink }
        g.text(pts, 2, 25, color, 2)
        if (r.points <= 0 && frame >= COUNT) {
            // A zero gets a little rain cloud.
            val cx = COLS - 24
            g.sprite(CLOUD, cx, 24, mapOf('#' to HockeyBoard.MUTED))
            g.shape()
            for (k in 0 until 4) g.put(cx + 1 + k * 2, 30 + ((frame + k) % 3) * 2, 0xFF4AB8FF.toInt())
        } else if (mvp) {
            g.shape()
            for ((dx, dy) in listOf(0 to 0, 6 to 4, 12 to 1, 18 to 5)) if ((frame + dx) % 4 < 2) g.put(COLS - 24 + dx, 26 + dy, PixelArt.GOLD)
        }
        return g
    }

    /** The night's total for the list, fireworks if it was a big one. */
    fun total(n: FantasyNight.ListNight, frame: Int = COUNT): LedGrid {
        val g = LedGrid(COLS, ROWS)
        val big = n.total >= BIG_NIGHT
        if (big) {
            // Fireworks in the band above the words.
            g.shape()
            for (b in 0 until 3) {
                val age = (frame + b * 4) % 12
                val bx = 20 + b * 44
                for (k in 0 until 12) {
                    val a = k * Math.PI / 6
                    val y = 8 + (sin(a) * age * 0.6).toInt()
                    if (y in 0..12) g.put(bx + (cos(a) * age * 1.5).toInt(), y, listOf(PixelArt.GOLD, 0xFF4AB8FF.toInt(), 0xFFE05CC0.toInt())[b])
                }
            }
        }
        val label = PixelFont.clean(n.list.name).take(PixelFont.fits(COLS - 4))
        g.textCenter(label, 15, HockeyBoard.MUTED)
        val shown = if (frame >= COUNT) n.total else n.total * frame / COUNT
        g.textCenter(Scoring.fmt(shown) + " PTS", 26, if (big) PixelArt.GOLD else HockeyBoard.WHITE, 2)
        return g
    }

    /** The whole report for one list, as notification frames: title, MVP first, the others, the total. */
    fun frames(n: FantasyNight.ListNight, date: String): List<LedGrid> {
        val played = n.rows.filter { it.line != null }
        val mvp = n.mvp
        return listOf(title(n.list.name, date)) + played.take(8).map { player(it, it == mvp) } + total(n)
    }

    /** "NOV 9". */
    fun dateLabel(d: java.time.LocalDate): String =
        listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")[d.monthValue - 1] + " " + d.dayOfMonth

    /** A live alert: GOAL! / ASSIST / WIN / SHUTOUT big in team colour, the player and tonight's line. */
    fun alert(r: FantasyNight.Row, what: String): LedGrid = LedGrid(COLS, ROWS).apply {
        val ink = TeamInk.of(r.player.team)
        val word = (if (what == "GOAL") "GOAL!" else what).take(10)
        val scale = if (PixelFont.width(word, 2) <= COLS - 4) 2 else 1
        textCenter(word, 2, if (what == "GOAL" || what == "SHUTOUT") PixelArt.GOLD else ink, scale)
        textCenter(PixelFont.clean(r.player.lastName).take(PixelFont.fits(COLS - 4)), 20, HockeyBoard.WHITE)
        val stats = PixelFont.clean(FantasyNight.statText(r.line, r.player.isGoalie).replace(" · ", " ")).take(PixelFont.fits(COLS - 4))
        textCenter(stats, 31, HockeyBoard.MUTED)
    }

    /** Before puck drop: OUT, the player and why. */
    fun out(p: com.bit128.bitsports.nhl.PlayerRef, why: String): LedGrid = LedGrid(COLS, ROWS).apply {
        textCenter("OUT", 2, HockeyBoard.LIVE, 2)
        textCenter(PixelFont.clean(p.lastName).take(PixelFont.fits(COLS - 4)), 20, HockeyBoard.WHITE)
        textCenter(PixelFont.clean(why).take(PixelFont.fits(COLS - 4)), 31, HockeyBoard.MUTED)
    }
}
