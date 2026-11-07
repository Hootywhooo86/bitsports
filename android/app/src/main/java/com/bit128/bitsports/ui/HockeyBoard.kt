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
        /** A power play: an hourglass drains as the penalty runs out; the last 10 seconds go red. */
        data class PowerPlay(val text: String, val secondsLeft: Int?, val total: Int = 120) : Zone()
        /** A goalie heads for the bench and EXTRA ATTACKER scrolls past. */
        data class GoaliePulled(val team: String, val ink: Int) : Zone()
        /** The last hour before puck drop: a puck hovers over centre ice, then drops for the faceoff. */
        data class Countdown(val secondsLeft: Long, val label: String? = null) : Zone()
    }

    /** A quick in-game moment drawn over the zone for a couple of seconds. */
    data class Flash(val kind: Kind, val home: Boolean, val ink: Int, val id: Long) {
        enum class Kind { SAVE, HIT }
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
        /** The last minute of a close game: the clock beats like a heart. */
        val heartbeat: Boolean = false,
        /** A rivalry game: flames up the sides of the board. */
        val rivalry: String? = null,
        /** Playoff round 1-4: the board grows a beard, longer each round. */
        val beardRound: Int = 0,
        val flash: Flash? = null,
    ) {
        /** Whether anything on this board moves, so it needs redrawing frame by frame. */
        val animated: Boolean get() = zone is Zone.Zamboni || (zone as? Zone.Line)?.blink == true || zone is Zone.PowerPlay ||
            zone is Zone.GoaliePulled || zone is Zone.Countdown || heartbeat || rivalry != null || flash != null
    }

    private val RIVALRIES: Map<Set<String>, String> = mapOf(
        setOf("EDM", "CGY") to "BATTLE OF ALBERTA",
        setOf("TOR", "MTL") to "ORIGINAL SIX",
        setOf("BOS", "MTL") to "ORIGINAL SIX",
        setOf("CHI", "DET") to "ORIGINAL SIX",
        setOf("NYR", "BOS") to "ORIGINAL SIX",
        setOf("TOR", "OTT") to "BATTLE OF ONTARIO",
        setOf("NYR", "NYI") to "BATTLE OF NEW YORK",
        setOf("NYR", "NJD") to "HUDSON RIVER",
        setOf("PIT", "PHI") to "BATTLE OF PA",
        setOf("PIT", "WSH") to "CROSBY V OVI",
        setOf("LAK", "ANA") to "FREEWAY FACEOFF",
        setOf("COL", "DET") to "BAD BLOOD",
        setOf("VAN", "EDM") to "WESTERN RIVALS",
        setOf("WPG", "MIN") to "PRAIRIE RIVALS",
        setOf("TBL", "FLA") to "SUNSHINE STATE",
    )

    fun rivalry(away: String, home: String): String? = RIVALRIES[setOf(away, home)]

    /** A rival for [team], if it has one, to show off rivalry night. */
    fun rivalryFor(team: String): String? = RIVALRIES.keys.firstOrNull { team in it }?.firstOrNull { it != team }

    /** Playoff round from the game id (2026030411: type 03, round 4), 0 in the regular season. */
    fun playoffRound(gameId: Long): Int {
        val s = gameId.toString()
        return if (s.length == 10 && s.substring(4, 6) == "03") s[7].digitToIntOrNull()?.coerceIn(1, 4) ?: 0 else 0
    }

    /** For an NHL game. [records] (away, home) fill the sub line before puck drop. */
    fun input(g: Game, accent: Int, now: Instant = Instant.now(), records: Pair<String?, String?> = null to null): Input {
        val has = g.state.isLive || g.state.isOver
        val c = g.clock
        val banner = Strengths.banner(g)
        val untilDrop = Duration.between(now, g.startUtc).seconds
        val rival = rivalry(g.away.abbrev, g.home.abbrev)
        val zone = when {
            g.state.isLive && c?.inIntermission == true -> Zone.Zamboni
            banner?.kind == Strengths.Kind.EMPTY_NET && banner.team != null -> Zone.GoaliePulled(banner.team, TeamInk.of(banner.team))
            banner?.kind == Strengths.Kind.PP -> Zone.PowerPlay(banner.text,
                g.situation?.timeRemaining?.let { NhlExtrasParser.seconds(it) })
            banner != null -> Zone.Line(banner.text, WARN)
            has && g.away.sog != null -> Zone.Line("SHOTS ${g.away.sog}-${g.home.sog}", MUTED)
            !g.scheduleOk -> Zone.Line("POSTPONED", WARN)
            !has && untilDrop in -600 until 3600 -> Zone.Countdown(untilDrop.coerceAtLeast(0), rival)
            else -> countdown(g.startUtc, now)?.let { Zone.Line(it, MUTED) } ?: Zone.None
        }
        // The last minute of the third (or overtime) with a goal in it: the clock's heartbeat.
        val late = g.state.isLive && c != null && !c.inIntermission && c.period >= 3 &&
            (NhlExtrasParser.seconds(c.timeRemaining) ?: 999) <= 60 && kotlin.math.abs((g.away.score ?: 0) - (g.home.score ?: 0)) <= 1
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
            heartbeat = late, rivalry = rival.takeIf { !g.state.isOver }, beardRound = playoffRound(g.id),
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

        // Lub-dub: two quick bright beats, then a rest.
        val clockColor = if (i.heartbeat) (if (frame % 8 == 0 || frame % 8 == 2) LIVE else HeatMap.blend(0xFF000000.toInt(), LIVE, 0.45f)) else i.clockColor
        g.textCenter(i.clock.take(20), 24, clockColor)

        // Beard first, so the period bar sits over it.
        if (i.beardRound > 0) beard(g, i.beardRound)
        // Rivalry flames lick up both sides; zone text keeps clear of them.
        val maxZone = if (i.rivalry != null) 18 else 20

        val flashAge = i.flash?.let { frame - flashStart(it, frame) }
        if (i.flash != null && flashAge != null && flashAge < FLASH_FRAMES) {
            flash(g, i.flash, flashAge)
        } else when (val z = i.zone) {
            Zone.None -> {}
            is Zone.Line -> if (!z.blink || frame % 8 < 5) g.textCenter(z.text.take(maxZone), 33, z.color)
            is Zone.PowerPlay -> powerPlay(g, z, frame, maxZone)
            is Zone.GoaliePulled -> goaliePulled(g, z, frame)
            is Zone.Countdown -> countdownZone(g, z, frame, i.rivalry)
            Zone.Zamboni -> {
                // It resurfaces left to right; the ice behind it is fresh and glints, and the
                // scoreboard ahead of it is fogged over until it passes.
                val span = COLS + Sprites.ZAMBONI_W
                val x = (frame % span) - Sprites.ZAMBONI_W
                val fresh = (x + 4).coerceIn(0, COLS)
                g.fill(0, 38, fresh, 2, 0xFF2E4A5C.toInt())
                g.shape()
                for (k in 0 until fresh step 9) if ((k / 9 + frame) % 5 == 0) g.put(k, 38, 0xFFDFF4FF.toInt())
                g.sprite(Sprites.ZAMBONI, x, 32, mapOf('b' to i.accent, 'w' to ICE, 'd' to DIM, 't' to MUTED, 'i' to ICE))
                val fog = 0xFF7F95A6.toInt()
                for (yy in 0 until 31) for (xx in (x + Sprites.ZAMBONI_W + 2).coerceAtLeast(0) until COLS) {
                    val v = g[xx, yy]
                    if (v != 0) g.cells[yy * COLS + xx] = HeatMap.blend(v, fog, 0.6f)
                }
            }
        }

        // Rivalry flames go on last, with a clear column between them and anything scrolling past.
        if (i.rivalry != null) {
            g.fill(0, 26, 7, 16, 0); g.fill(COLS - 8, 26, 8, 16, 0)
            PixelArt.flame(g, -1, 41, 10 + (frame % 3), frame, w = 5)
            PixelArt.flame(g, COLS - 5, 41, 10 + ((frame + 1) % 3), frame + 2, w = 5)
        }

        i.progress?.let { p ->
            g.fill(0, 43, COLS, 1, TRACK)
            g.fill(0, 43, (p * COLS).toInt().coerceIn(1, COLS), 1, i.accent)
        }
        return g
    }

    const val FLASH_FRAMES = 16
    private val flashStarts = HashMap<Long, Int>()
    /** The frame a flash first showed up on, so it plays from its start whenever it arrives. */
    @Synchronized private fun flashStart(f: Flash, frame: Int): Int = flashStarts.getOrPut(f.id) {
        if (flashStarts.size > 50) flashStarts.clear()
        frame
    }

    private val SAND = 0xFFFFD27A.toInt()

    private fun powerPlay(g: LedGrid, z: Zone.PowerPlay, frame: Int, maxZone: Int) {
        val left = z.secondsLeft
        val late = left != null && left <= 10
        val text = z.text.take(maxZone - 2)
        val color = if (late) (if (frame % 4 < 2) LIVE else WARN) else WARN
        val tw = PixelFont.width(text)
        val x0 = (COLS - tw - 12) / 2 + 12
        g.text(text, x0, 33, color)
        // The hourglass, 9 wide, left of the text: top bulb empties, bottom fills.
        val hx = x0 - 14
        val frac = ((left ?: z.total).toFloat() / z.total.coerceAtLeast(left ?: 1)).coerceIn(0f, 1f)
        g.fill(hx, 32, 9, 1, MUTED); g.fill(hx, 41, 9, 1, MUTED)
        g.shape()
        val top = (frac * 4).toInt()
        for (r in 0 until 4) {
            val w = 7 - r * 2
            val inset = 1 + r
            val y = 33 + r
            for (k in 0 until w) g.put(hx + inset + k - 0, y, if (3 - r < top) SAND else DIM)
        }
        val bottom = ((1 - frac) * 4).toInt()
        for (r in 0 until 4) {
            val w = 1 + r * 2
            val inset = 3 - r + 1
            val y = 37 + r
            for (k in 0 until w) g.put(hx + inset + k - 1, y, if (r >= 4 - bottom) SAND else DIM)
        }
        // A grain falling through the neck.
        if (left == null || left > 0) g.put(hx + 4, 37 + (frame % 3), SAND)
    }

    private val MINI_GOALIE = arrayOf(
        "..hhh..",
        ".ggggg.",
        "ggggggg",
        ".ggggg.",
        ".gg.gg.",
        "gg...gg",
        "kk...kk",
    )

    private fun goaliePulled(g: LedGrid, z: Zone.GoaliePulled, frame: Int) {
        // EXTRA ATTACKER scrolls right to left; the goalie skates off the left edge to the bench.
        val msg = "${z.team} EXTRA ATTACKER"
        val w = PixelFont.width(msg)
        val span = COLS + w
        val x = COLS - 20 - (frame * 3) % span
        g.text(msg, x, 33, LIVE)
        g.fill(0, 31, 16, 11, 0)
        val gx = 8 - (frame % 24) / 2
        g.sprite(MINI_GOALIE, gx, 33, mapOf('h' to WHITE, 'g' to z.ink, 'k' to MUTED))
    }

    private fun countdownZone(g: LedGrid, z: Zone.Countdown, frame: Int, rivalry: String?) {
        val s = z.secondsLeft
        // Every few seconds a rivalry game shows its name instead.
        val text = when {
            rivalry != null && frame % 40 >= 28 -> rivalry
            s <= 0 -> "PUCK DROP"
            else -> "DROP IN %d:%02d".format(s / 60, s % 60)
        }
        if (text == rivalry) {
            g.textCenter(text, 33, WARN)
            return
        }
        val tw = PixelFont.width(text)
        val x0 = (COLS - tw - 16) / 2
        g.text(text, x0, 33, if (s <= 0) LIVE else MUTED)
        // Centre ice: a red dot in a blue ring, the puck bobbing above it until it drops.
        val cx = x0 + tw + 10
        g.shape()
        for (k in 0 until 16) {
            val a = k * Math.PI / 8
            g.put(cx + (Math.cos(a) * 4).toInt(), 37 + (Math.sin(a) * 3).toInt(), 0xFF3A6FE8.toInt())
        }
        g.put(cx, 37, LIVE)
        val py = if (s <= 0) 37 else 31 + listOf(0, 1, 1, 2, 1, 1)[frame % 6]
        g.fill(cx - 1, py, 3, 1, WHITE)
    }

    private fun flash(g: LedGrid, f: Flash, age: Int) {
        val side = if (f.home) COLS - 30 else 30
        when (f.kind) {
            Flash.Kind.SAVE -> {
                // The glove snaps shut on the puck, and SAVE! flashes.
                val open = age < 3
                g.shape()
                val gx = side - 5
                if (open) {
                    g.fill(gx, 32, 2, 8, f.ink); g.fill(gx + 8, 32, 2, 8, f.ink); g.fill(gx, 39, 10, 2, f.ink)
                    g.fill(gx + 4 + (2 - age) * 3, 35, 2, 2, WHITE)
                } else {
                    g.fill(gx + 1, 33, 8, 7, f.ink); g.fill(gx + 3, 35, 4, 2, WHITE)
                    if (age < 6) for ((dx, dy) in listOf(-3 to 33, 12 to 33, -3 to 41, 12 to 41)) g.put(gx + dx, dy, WARN)
                }
                val t = "SAVE!"
                val tx = if (f.home) side - 16 - PixelFont.width(t, 1) else side + 14
                if (age % 4 < 3) g.text(t, tx, 33, WARN)
            }
            Flash.Kind.HIT -> {
                // A puff of dust where the hit landed.
                g.shape()
                val t = "HIT!"
                val tx = if (f.home) side - 14 - PixelFont.width(t) else side + 12
                // Dust stays in the zone rows and off the word.
                fun clear(px: Int, py: Int) = py in 33..41 && !(px in tx - 2..tx + PixelFont.width(t) + 1 && py in 31..41)
                val r = 1 + age / 2
                val dust = HeatMap.blend(0xFF000000.toInt(), 0xFFF0E2C4.toInt(), (1.1f - age / 14f).coerceIn(0.3f, 1f))
                for (k in 0 until 18) {
                    val a = k * Math.PI / 9 + age * 0.2
                    val dist = r + (k % 3)
                    if (age < 13) {
                        val px = side + (Math.cos(a) * dist * 1.6).toInt()
                        val py = 37 + (Math.sin(a) * dist).toInt()
                        if (clear(px, py)) g.put(px, py, dust)
                        if (k % 2 == 0 && clear(px + 1, py)) g.put(px + 1, py, dust)
                    }
                }
                // The impact itself: a white star for the first beats.
                if (age < 3) for ((dx, dy) in listOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1)) g.put(side + dx, 37 + dy, WHITE)
                if (age < 10) g.text(t, tx, 33, f.ink)
            }
        }
    }

    private fun beard(g: LedGrid, round: Int) {
        val dark = 0xFF9A6438.toInt()
        val light = 0xFFD09A5C.toInt()
        g.shape()
        // Up to 3 rows (the zone text sits just above): later rounds are longer and fuller.
        for (x in 0 until COLS) {
            val len = (PixelArt.rand(x * 13 + round) * (round + 1) * 0.8f).toInt().coerceIn(if (round >= 4) 1 else 0, 3)
            for (k in 0 until len) g.put(x, 43 - k, if ((x + k) % 3 == 0) light else dark)
        }
    }

    /** A team colour that shows as lit LEDs on black: the brighter of the two, or white. */
    fun led(c: Pair<Int, Int>): Int {
        fun lum(v: Int) = 0.299f * (v shr 16 and 0xFF) / 255f + 0.587f * (v shr 8 and 0xFF) / 255f + 0.114f * (v and 0xFF) / 255f
        val best = if (lum(c.first) >= lum(c.second)) c.first else c.second
        return if (lum(best) < 0.18f) WHITE else best
    }
}
