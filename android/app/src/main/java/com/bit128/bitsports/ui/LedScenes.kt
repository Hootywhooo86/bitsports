package com.bit128.bitsports.ui

import com.bit128.bitsports.alerts.AlertCopy
import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.ai.RecapPrompt
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.PeriodStats

/**
 * The animated notifications, frame by frame. Each scene is a few 128x44 boards for the expanded
 * notification and a few 128x16 strips for the collapsed one and the heads-up; the shade flips
 * through them. Pure, so every frame is testable.
 */
object LedScenes {
    const val STRIP_ROWS = 16
    private const val LAMP = 0xFFFF2A3D.toInt()
    private const val LAMP_OFF = 0xFF3A1418.toInt()

    data class Scene(val frames: List<LedGrid>, val strips: List<LedGrid>)

    private fun board() = LedGrid(HockeyBoard.COLS, HockeyBoard.ROWS)
    private fun strip() = LedGrid(HockeyBoard.COLS, STRIP_ROWS)

    private fun lastName(full: String) = PixelFont.clean(full.substringAfterLast(' '))
    private fun nick(abbrev: String) = PixelFont.clean(AlertCopy.nick(abbrev))
    private fun team(abbrev: String) = HockeyBoard.colors(abbrev)

    /** Large text if it fits across, else regular. */
    private fun LedGrid.headline(s: String, y: Int, color: Int, preferred: Int = 2) {
        val scale = (preferred downTo 1).first { PixelFont.width(s, it) <= cols - 4 }
        textCenter(s.take(PixelFont.fits(cols - 4, scale)), y, color, scale)
    }

    private fun LedGrid.stripes(c: Pair<Int, Int>, y: Int) {
        fill(0, y, cols, 2, c.first)
        fill(0, y + 2, cols, 2, c.second)
    }

    private fun stripText(text: String, color: Int, lampOn: Boolean? = null, colors: Pair<Int, Int>? = null): LedGrid {
        val g = strip()
        val t = text.take(PixelFont.fits(g.cols - 16))
        g.textCenter(t, 4, color)
        if (lampOn != null) {
            val lamp = if (lampOn) LAMP else LAMP_OFF
            g.fill(1, 3, 5, 9, lamp); g.fill(122, 3, 5, 9, lamp)
        } else if (colors != null) {
            g.fill(1, 3, 2, 9, colors.first); g.fill(3, 3, 2, 9, colors.second)
            g.fill(123, 3, 2, 9, colors.first); g.fill(125, 3, 2, 9, colors.second)
        }
        return g
    }

    private fun scoreLine(game: Game, away: Int, home: Int) = "${game.away.abbrev} $away-$home ${game.home.abbrev}"

    // ---------- goal ----------

    fun goal(g: GoalEvent, game: Game, accent: Int): Scene {
        val c = team(g.teamAbbrev)
        val led = HockeyBoard.led(c)
        fun lamp(on: Boolean): LedGrid = board().apply {
            // The goal light: a red dome with a bright core, and its glow either side.
            val col = if (on) LAMP else LAMP_OFF
            fill(56, 1, 16, 2, col); fill(54, 3, 20, 6, col)
            if (on) { fill(60, 4, 8, 3, HockeyBoard.WHITE); fill(44, 5, 6, 1, LAMP); fill(78, 5, 6, 1, LAMP) }
            fill(52, 9, 24, 2, HockeyBoard.DIM)
            textCenter("GOAL!", 14, if (on) led else HockeyBoard.WHITE, 3)
            stripes(c, 40)
        }
        val who = board().apply {
            headline(lastName(g.scorer), 3, led)
            val tag = listOfNotNull(
                g.scorerTotal?.let { "GOAL #$it" },
                RecapPrompt.strengthText(g.strength).takeIf { g.strength != com.bit128.bitsports.nhl.Strength.EV }?.uppercase(),
            ).joinToString(" ")
            if (tag.isNotEmpty()) textCenter(PixelFont.clean(tag), 21, HockeyBoard.WARN)
            val assists = g.assists.joinToString(", ") { lastName(it.first) }.ifEmpty { "UNASSISTED" }
            textCenter(PixelFont.clean(assists).take(20), 30, HockeyBoard.MUTED)
            text(AlertPlanner.periodLabel(g.period, g.periodType).uppercase() + " " + g.timeInPeriod, 2, 37, HockeyBoard.DIM)
        }
        val score = HockeyBoard.build(
            HockeyBoard.input(game.copy(
                away = game.away.copy(score = g.awayScore), home = game.home.copy(score = g.homeScore),
            ), accent).copy(zone = HockeyBoard.Zone.Line("GOAL ${nick(g.teamAbbrev)}", led)),
        )
        val strips = listOf(
            stripText("GOAL! ${nick(g.teamAbbrev)}", led, lampOn = true),
            stripText("GOAL! ${nick(g.teamAbbrev)}", HockeyBoard.WHITE, lampOn = false),
            stripText(scoreLine(game, g.awayScore, g.homeScore), HockeyBoard.WHITE, colors = c),
            stripText(lastName(g.scorer) + (g.scorerTotal?.let { " ($it)" } ?: ""), led, colors = c),
        )
        return Scene(listOf(lamp(true), lamp(false), who, score), strips)
    }

    // ---------- end of period ----------

    fun periodEnd(s: PeriodStats, label: String, game: Game, away: Int, home: Int, accent: Int): Scene {
        val ac = team(game.away.abbrev)
        val hc = team(game.home.abbrev)
        val tag = "END OF ${label.uppercase()}"
        val score = HockeyBoard.build(
            HockeyBoard.input(game.copy(away = game.away.copy(score = away), home = game.home.copy(score = home)), accent)
                .copy(clock = tag, clockColor = HockeyBoard.WARN, progress = 1f, zone = HockeyBoard.Zone.Line("SHOTS ${s.awayShots}-${s.homeShots}", HockeyBoard.MUTED)),
        )
        fun bars(rows: List<Triple<String, Pair<String, String>, Float>>): LedGrid = board().apply {
            rows.forEachIndexed { i, (name, values, share) ->
                val y = 1 + i * 15
                text(values.first, 2, y, HockeyBoard.led(ac))
                textCenter(name, y, HockeyBoard.MUTED)
                textRight(values.second, cols - 3, y, HockeyBoard.led(hc))
                val split = (share.coerceIn(0f, 1f) * (cols - 4)).toInt()
                fill(2, y + 9, split, 3, HockeyBoard.led(ac))
                fill(2 + split, y + 9, cols - 4 - split, 3, HockeyBoard.led(hc))
                put(cols / 2, y + 8, HockeyBoard.WHITE); put(cols / 2, y + 12, HockeyBoard.WHITE) // centre mark
            }
        }
        fun share(a: Int, h: Int) = if (a + h == 0) 0.5f else a.toFloat() / (a + h)
        fun ppGoals(pp: String) = pp.substringBefore("/").toIntOrNull() ?: 0
        val f1 = bars(listOf(
            Triple("SHOTS", "${s.awayShots}" to "${s.homeShots}", share(s.awayShots, s.homeShots)),
            Triple("HITS", "${s.awayHits}" to "${s.homeHits}", share(s.awayHits, s.homeHits)),
            Triple("GOALS", "${s.awayGoals}" to "${s.homeGoals}", share(s.awayGoals, s.homeGoals)),
        ))
        val f2 = bars(listOf(
            Triple("FACEOFF", "${s.awayFaceoffPct}%" to "${s.homeFaceoffPct}%", share(s.awayFaceoffPct, s.homeFaceoffPct)),
            Triple("PP", s.awayPp to s.homePp, share(ppGoals(s.awayPp), ppGoals(s.homePp))),
        ))
        val strips = listOf(
            stripText(tag, HockeyBoard.WARN),
            stripText(scoreLine(game, away, home), HockeyBoard.WHITE, colors = if (away >= home) ac else hc),
            stripText("SHOTS ${s.awayShots}-${s.homeShots}", HockeyBoard.MUTED),
        )
        return Scene(listOf(score, f1, f2), strips)
    }

    // ---------- final ----------

    fun final(game: Game, shootout: Boolean, stars: List<String>, accent: Int): Scene {
        val a = game.away.score ?: 0
        val h = game.home.score ?: 0
        val winner = if (a >= h) game.away.abbrev else game.home.abbrev
        val wc = team(winner)
        val tag = when {
            shootout -> "FINAL/SO"
            game.clock?.periodType == "OT" || (game.clock?.period ?: 0) > 3 -> "FINAL/OT"
            else -> "FINAL"
        }
        val score = HockeyBoard.build(
            HockeyBoard.input(game, accent).copy(
                clock = tag, clockColor = HockeyBoard.WHITE, progress = null,
                zone = HockeyBoard.Zone.Line("${nick(winner)} WIN", HockeyBoard.led(wc)),
            ),
        )
        val starFrames = stars.take(3).mapIndexed { i, name ->
            board().apply {
                val rank = listOf("1ST", "2ND", "3RD")[i]
                // Gold stars, as many as the rank is worth: three for the first star.
                val count = 3 - i
                val w = count * 12 - 2
                for (k in 0 until count) text("*", (cols - w) / 2 + k * 12, 2, HockeyBoard.WARN, 2)
                textCenter("$rank STAR", 19, HockeyBoard.MUTED)
                headline(lastName(name), 29, HockeyBoard.WHITE, preferred = 1)
                stripes(wc, 40)
            }
        }
        val strips = listOf(
            stripText(tag, HockeyBoard.WHITE, colors = wc),
            stripText(scoreLine(game, a, h), HockeyBoard.WHITE, colors = wc),
        ) + stars.take(1).map { stripText("* " + lastName(it), HockeyBoard.WARN) }
        return Scene(listOf(score) + starFrames, strips)
    }

    // ---------- the live mini scoreboard ----------

    /**
     * The ongoing scoreboard: the board as it stands, then the last goal; between periods the
     * Zamboni makes its lap across the frames instead.
     */
    fun live(game: Game, lastGoal: GoalEvent?, accent: Int): Scene {
        val input = HockeyBoard.input(game, accent)
        val frames = if (input.zone is HockeyBoard.Zone.Zamboni) {
            (0 until 4).map { HockeyBoard.build(input, frame = 12 + it * 34) }
        } else {
            listOfNotNull(
                HockeyBoard.build(input),
                lastGoal?.let { g ->
                    HockeyBoard.build(input.copy(zone = HockeyBoard.Zone.Line(
                        "GOAL ${lastName(g.scorer)} ${AlertPlanner.periodLabel(g.period, g.periodType).uppercase()}",
                        HockeyBoard.led(team(g.teamAbbrev)),
                    )))
                },
            )
        }
        val clock = HockeyBoard.clockText(game)
        val strips = listOf(
            stripText("${game.away.abbrev} ${game.away.score ?: 0}-${game.home.score ?: 0} ${game.home.abbrev}", HockeyBoard.WHITE),
            stripText(clock, if (game.state.isLive && game.clock?.inIntermission != true) HockeyBoard.LIVE else HockeyBoard.MUTED),
        )
        return Scene(frames, strips)
    }
}
