package com.bit128.bitsports.ui

import com.bit128.bitsports.alerts.AlertPlanner
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.NhlExtrasParser
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.nhl.OnIce
import com.bit128.bitsports.nhl.Strengths
import java.time.Duration
import java.time.Instant

/**
 * The arena jumbotron: 128 LEDs across, 44 down. Used by Home, the game screen and the
 * widgets, so a score looks the same everywhere.
 *
 *   rows  2-22  team codes in the corners, the score in triple-size digits in the middle
 *   rows 10-11  each team's two colours under its code
 *   rows 14-20  shots (or record before puck drop) under each code
 *   row  24     the clock: period and time, FINAL, or puck-drop time
 *   rows 32-41  what's happening: power play, empty net, the intermission Zamboni, shots
 *   row  43     how far through the period we are
 */
object HockeyBoard {
    const val COLS = 128
    const val ROWS = 44

    const val WHITE = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFF8C8C8C.toInt()
    const val DIM = 0xFF4A4A4A.toInt()
    const val LIVE = 0xFFFF4D6D.toInt()
    const val WARN = 0xFFFFD95E.toInt()
    private const val ICE = 0xFF9FD8FF.toInt()
    private const val TRACK = 0xFF2A2A2A.toInt()

    sealed class Zone {
        object None : Zone()
        object Zamboni : Zone()
        data class Line(val text: String, val color: Int, val blink: Boolean = false) : Zone()
    }

    data class Input(
        val away: String,
        val awayColors: Pair<Int, Int>,
        val home: String,
        val homeColors: Pair<Int, Int>,
        val awayScore: Int?,
        val homeScore: Int?,
        val clock: String,
        val clockColor: Int,
        val awaySub: String?,
        val homeSub: String?,
        val zone: Zone,
        /** 0..1 through the current period, or null when there's no clock running. */
        val progress: Float?,
        val accent: Int = WHITE,
        /** NHL: the team's LED colour from [TeamInk]; null works it out from the colours (other sports). */
        val awayInk: Int? = null,
        val homeInk: Int? = null,
    )

    /** For an NHL game. [records] (away, home) fill the sub line before puck drop. */
    fun input(g: Game, accent: Int, now: Instant = Instant.now(), records: Pair<String?, String?> = null to null): Input {
        val has = g.state.isLive || g.state.isOver
        val c = g.clock
        val banner = Strengths.banner(g)
        val zone = when {
            g.state.isLive && c?.inIntermission == true -> Zone.Zamboni
            banner?.kind == Strengths.Kind.EMPTY_NET -> Zone.Line(banner.text, LIVE, blink = true)
            banner != null -> Zone.Line(banner.text, WARN)
            has && g.away.sog != null -> Zone.Line("SHOTS ${g.away.sog}-${g.home.sog}", MUTED)
            !g.scheduleOk -> Zone.Line("POSTPONED", WARN)
            else -> countdown(g.startUtc, now)?.let { Zone.Line(it, MUTED) } ?: Zone.None
        }
        val progress = if (g.state.isLive && c != null && !c.inIntermission) {
            NhlExtrasParser.seconds(c.timeRemaining)?.let { left ->
                val len = OnIce.periodLength(g.id, c.periodType).toFloat()
                ((len - left) / len).coerceIn(0f, 1f)
            }
        } else null
        return Input(
            away = g.away.abbrev, awayColors = colors(g.away.abbrev),
            home = g.home.abbrev, homeColors = colors(g.home.abbrev),
            awayInk = TeamInk.pair(g.away.abbrev, g.home.abbrev).first, homeInk = TeamInk.pair(g.away.abbrev, g.home.abbrev).second,
            awayScore = g.away.score.takeIf { has }, homeScore = g.home.score.takeIf { has },
            clock = clockText(g),
            clockColor = if (g.state.isLive && c?.inIntermission != true) LIVE else MUTED,
            awaySub = if (has) g.away.sog?.toString() else records.first,
            homeSub = if (has) g.home.sog?.toString() else records.second,
            zone = zone, progress = progress, accent = accent,
        )
    }

    fun clockText(g: Game): String {
        val c = g.clock
        return when {
            g.state.isLive && c != null && c.inIntermission -> "INT " + AlertPlanner.periodLabel(c.period, c.periodType).uppercase()
            g.state.isLive && c != null -> AlertPlanner.periodLabel(c.period, c.periodType).uppercase() + " " + c.timeRemaining
            else -> ScoreFormat.status(g).uppercase()
        }
    }

    /** "IN 2H 14M" before a game today, null otherwise. */
    fun countdown(start: Instant, now: Instant): String? {
        val m = Duration.between(now, start).toMinutes()
        return when {
            m < 0 || m >= 12 * 60 -> null
            m < 1 -> "PUCK DROP"
            m < 60 -> "IN ${m}M"
            else -> "IN ${m / 60}H ${m % 60}M"
        }
    }

    fun colors(abbrev: String): Pair<Int, Int> =
        NhlTeams.byAbbrev(abbrev)?.let { it.colors.first.toInt() to it.colors.second.toInt() } ?: (0xFF3A3A3A.toInt() to 0xFF6B6B6B.toInt())

    /** [frame] advances animations (the Zamboni, a blinking empty net): one step is ~120ms. */
    fun build(i: Input, frame: Int = 0): LedGrid {
        val g = LedGrid(COLS, ROWS)
        val a = i.away.take(4)
        val h = i.home.take(4)
        val aw = PixelFont.width(a)
        val hw = PixelFont.width(h)
        val awayLead = (i.awayScore ?: 0) >= (i.homeScore ?: 0)
        val homeLead = (i.homeScore ?: 0) >= (i.awayScore ?: 0)
        g.text(a, 4, 2, i.awayInk ?: led(i.awayColors))
        g.textRight(h, COLS - 5, 2, i.homeInk ?: led(i.homeColors))
        g.fill(4, 10, aw, 1, i.awayColors.first); g.fill(4, 11, aw, 1, i.awayColors.second)
        g.fill(COLS - 4 - hw, 10, hw, 1, i.homeColors.first); g.fill(COLS - 4 - hw, 11, hw, 1, i.homeColors.second)
        i.awaySub?.let { g.text(it.take(6), 4, 14, MUTED) }
        i.homeSub?.let { g.textRight(it.take(6), COLS - 5, 14, MUTED) }

        // The score: each side's number lit by whether it's leading, so you read it from across the room.
        val room = COLS - 2 * (4 + maxOf(aw, hw, 29) + 3)
        if (i.awayScore != null && i.homeScore != null) {
            val left = i.awayScore.toString()
            val right = i.homeScore.toString()
            val all = "$left-$right"
            val scale = when { PixelFont.width(all, 3) <= room -> 3; PixelFont.width(all, 2) <= room -> 2; else -> 1 }
            val x0 = (COLS - PixelFont.width(all, scale)) / 2
            val y = if (scale == 3) 2 else 6
            val step = (PixelFont.W + 1) * scale
            g.text(left, x0, y, if (awayLead) WHITE else MUTED, scale)
            g.text("-", x0 + left.length * step, y, DIM, scale)
            g.text(right, x0 + (left.length + 1) * step, y, if (homeLead) WHITE else MUTED, scale)
        } else {
            g.textCenter("VS", 2, MUTED, 3)
        }

        g.textCenter(i.clock.take(20), 24, i.clockColor)

        when (val z = i.zone) {
            Zone.None -> {}
            is Zone.Line -> if (!z.blink || frame % 8 < 5) g.textCenter(z.text.take(20), 33, z.color)
            Zone.Zamboni -> {
                // It resurfaces left to right; the ice behind it stays fresh until it laps.
                val span = COLS + Sprites.ZAMBONI_W
                val x = (frame % span) - Sprites.ZAMBONI_W
                g.fill(0, 38, (x + 4).coerceIn(0, COLS), 2, 0xFF2E4A5C.toInt())
                g.sprite(Sprites.ZAMBONI, x, 32, mapOf('b' to i.accent, 'w' to ICE, 'd' to DIM, 't' to MUTED, 'i' to ICE))
            }
        }

        i.progress?.let { p ->
            g.fill(0, 43, COLS, 1, TRACK)
            g.fill(0, 43, (p * COLS).toInt().coerceIn(1, COLS), 1, i.accent)
        }
        return g
    }

    /** A team colour that shows as lit LEDs on black: the brighter of the two, or white. */
    fun led(c: Pair<Int, Int>): Int {
        fun lum(v: Int) = 0.299f * (v shr 16 and 0xFF) / 255f + 0.587f * (v shr 8 and 0xFF) / 255f + 0.114f * (v and 0xFF) / 255f
        val best = if (lum(c.first) >= lum(c.second)) c.first else c.second
        return if (lum(best) < 0.18f) WHITE else best
    }
}
