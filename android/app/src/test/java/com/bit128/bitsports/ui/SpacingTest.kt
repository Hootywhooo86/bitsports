package com.bit128.bitsports.ui

import com.bit128.bitsports.messageBoard
import com.bit128.bitsports.nhl.Clock
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.NhlTeams
import com.bit128.bitsports.nhl.PeriodStats
import com.bit128.bitsports.nhl.Situation
import com.bit128.bitsports.nhl.Strength
import com.bit128.bitsports.nhl.TeamLine
import com.bit128.bitsports.widget.ScoreboardLeds
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** No LED letter may touch a line, stripe, bar, sprite or other text, even diagonally, on any board. */
class SpacingTest {
    private val failures = ArrayList<String>()

    private fun check(name: String, g: LedGrid) {
        val t = g.touches()
        if (t.isNotEmpty()) failures += "$name: ${t.size} touching, first ${t.first()}"
    }

    private val start = Instant.parse("2026-10-23T01:00:00Z")
    private fun game(away: String, home: String, state: GameState, a: Int?, h: Int?, clock: Clock? = null, sit: String? = null, sog: Int? = 20) = Game(
        id = 2026020163, state = state, scheduleOk = true, startUtc = start,
        away = TeamLine(1, away, a, if (a == null) null else sog), home = TeamLine(2, home, h, if (h == null) null else sog),
        clock = clock, situation = sit?.let { Situation(it, "01:23") },
    )

    private val pairs = listOf("CAR" to "EDM", "NJD" to "VGK", "MTL" to "WSH", "CBJ" to "TBL")
    private val scores = listOf(0 to 0, 2 to 1, 10 to 2, 11 to 10, 1 to 12)
    private val clocks = listOf(
        Clock(2, "REG", "08:41", false, true), Clock(3, "REG", "00:04", false, true),
        Clock(4, "OT", "03:12", false, true), Clock(1, "REG", "00:00", true, false), Clock(6, "OT", "11:59", false, true),
    )

    private fun allGames(): List<Game> = pairs.flatMap { (a, h) ->
        listOf(game(a, h, GameState.FUT, null, null), game(a, h, GameState.OFF, 4, 3, Clock(4, "OT", "00:00", false, false))) +
            scores.flatMap { (sa, sh) ->
                clocks.flatMap { c -> listOf(null, "1451", "1541", "0651", "1441", "1531").map { s -> game(a, h, GameState.LIVE, sa, sh, c, s, sog = 48) } }
            }
    }

    @Test fun jumbotron() {
        for (g in allGames()) for (frame in listOf(0, 7, 40, 90)) {
            check("board ${g.away.abbrev}-${g.home.abbrev} ${g.state} ${g.away.score}-${g.home.score} ${g.clock} ${g.situation} f$frame",
                HockeyBoard.build(HockeyBoard.input(g, HockeyBoard.WHITE, start.minusSeconds(5000), "12-10-3" to "8-2-1"), frame))
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    /** Every animation in the Settings gallery, every frame, for a few teams. */
    @Test fun gallery() {
        for (team in listOf("EDM", "TOR", "CGY", "UTA", "SJS")) {
            val rival = HockeyBoard.rivalryFor(team) ?: "CGY"
            for (e in Gallery.entries(team, if (rival == team) "EDM" else rival)) for (f in 0 until e.frames) check("gallery $team ${e.name} f$f", e.build(f))
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun clipOverlays() {
        for (g in allGames().filterIndexed { i, _ -> i % 7 == 0 }) for (n in names) for (st in Strength.values()) for (assists in 0..2) {
            val goal = GoalEvent(g.id, 1, g.home.abbrev, n, 47, names.take(assists).map { it to 3 }, st, "wrist", null, 4, "OT", "11:19", 10, 11, null)
            check("bug ${g.away.abbrev}-${g.home.abbrev}", ClipOverlays.bug(g, goal))
            for (f in 0..8) check("lower $n $st $assists f$f", ClipOverlays.lowerThird(goal, f))
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun widgetBoards() {
        val now = start.minusSeconds(3000)
        for (g in allGames().filterIndexed { i, _ -> i % 9 == 0 }) for (team in listOf(g.away.abbrev, g.home.abbrev)) {
            val goal = GoalEvent(g.id, 1, g.home.abbrev, names[0], 47, emptyList(), Strength.PP, "wrist", null, 4, "OT", "11:19", 10, 11, null)
            for (share in listOf(null, 0.3f, 0.71f)) {
                com.bit128.bitsports.widget.WidgetBoards.team(team, g, null, goal, share, HockeyBoard.WHITE, now).forEach { check("widget $team ${g.state}", it) }
                com.bit128.bitsports.widget.WidgetBoards.team(team, null, g.copy(state = GameState.FUT), null, share, HockeyBoard.WHITE, start.minusSeconds(4 * 86_400)).forEach { check("widget next $team", it) }
            }
        }
        com.bit128.bitsports.widget.WidgetBoards.team("EDM", null, null, null, null, HockeyBoard.WHITE).forEach { check("widget empty", it) }
        for ((a, h) in pairs) for (days in listOf(0L, 1L, 3L, 9L)) for (last in listOf(null, "LAST W 12-11 VGK", "LAST L 0-4 WSH")) {
            val nx = game(a, h, GameState.FUT, null, null).copy(startUtc = start.plusSeconds(days * 86_400 + 3600 * 19))
            check("up next $a-$h $days $last", com.bit128.bitsports.widget.WidgetBoards.upNext(a, nx, last, start))
            check("up next home $a-$h $days $last", com.bit128.bitsports.widget.WidgetBoards.upNext(h, nx, last, start))
        }
        val box = com.bit128.bitsports.nhl.NhlFantasy.boxScore(com.bit128.bitsports.fixture("box-2026020239.json"), 2026020239L)
        val players = box.keys.take(14).mapIndexed { i, id -> com.bit128.bitsports.nhl.PlayerRef(id, names[i % names.size], if (i % 2 == 0) "EDM" else "NJD", if (i % 5 == 0) "G" else "C") }
        val n = com.bit128.bitsports.fantasy.FantasyNight.night(com.bit128.bitsports.fantasy.FantasyList("a", "The Very Long Family Pool Name", players),
            listOf(game("EDM", "NJD", GameState.LIVE, 2, 3).copy(id = 2026020239L)), box, com.bit128.bitsports.fantasy.Scoring.STANDARD)
        for (rows in 2..12) check("fantasy widget $rows", com.bit128.bitsports.widget.WidgetBoards.fantasy(n, rows))
        val league = allGames().filterIndexed { i, _ -> i % 11 == 0 }
        for (rows in listOf(60, 80, 104, 120, 140)) {
            for ((a, h) in pairs) for (share in listOf(null, 0.12f, 0.5f, 0.9f)) for (rec in listOf(null to null, "10-7-1" to "12-3-3", "41-30-11" to "38-34-10")) {
                val g = game(a, h, GameState.FUT, null, null)
                for (cd in listOf("PUCK DROP", "IN 20M", "IN 11H 59M")) for (meet in listOf(null, "LAST MEET W 12-11", "LAST MEET L 0-4", "FIRST MEETING"))
                    check("matchup $a-$h $rows", com.bit128.bitsports.widget.WidgetCards.matchup(g, rec.first, rec.second, meet, share, cd, rows))
            }
            for ((a, h) in pairs) {
                check("league $rows", com.bit128.bitsports.widget.WidgetCards.aroundTheLeague(a, game(a, h, GameState.FUT, null, null), league, setOf(a), HockeyBoard.WHITE, rows))
                check("league none $rows", com.bit128.bitsports.widget.WidgetCards.aroundTheLeague(a, null, league, setOf(a), HockeyBoard.WHITE, rows))
            }
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun liveCards() {
        val shots = (0 until 40).map { com.bit128.bitsports.nhl.ShotMark(if (it % 2 == 0) "EDM" else "CGY", 40 + it, (it * 7) % 70 - 35, it % 9 == 0, 1, null) }
        for (g in allGames().filterIndexed { i, _ -> i % 4 == 0 }) for (rows in listOf(60, 80, 96, 120, 140)) for (on in listOf(false, true)) {
            check("live heat ${g.away.abbrev}-${g.home.abbrev} ${g.situation} $rows", com.bit128.bitsports.widget.LiveCards.heat(g, null, rows, radioOn = on))
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun fantasyReport() {
        val box = com.bit128.bitsports.nhl.NhlFantasy.boxScore(com.bit128.bitsports.fixture("box-2026020239.json"), 2026020239L)
        val g = game("EDM", "NJD", GameState.OFF, 2, 3)
        val players = box.keys.take(30).mapIndexed { i, id -> com.bit128.bitsports.nhl.PlayerRef(id, names[i % names.size], if (i % 2 == 0) "EDM" else "NJD", if (i % 7 == 0) "G" else "C") }
        val list = com.bit128.bitsports.fantasy.FantasyList("a", "The Very Long Family Pool Name", players)
        for (sc in com.bit128.bitsports.fantasy.Scoring.PRESETS) {
            val n = com.bit128.bitsports.fantasy.FantasyNight.night(list, listOf(g.copy(id = 2026020239L)), box, sc)
            check("title", FantasyReportLeds.title(list.name, "NOV 9"))
            for (f in 0..FantasyReportLeds.COUNT + 12) {
                n.rows.forEach { r -> check("player ${r.player.name} f$f", FantasyReportLeds.player(r, r == n.mvp, f)) }
                check("total f$f", FantasyReportLeds.total(n, f))
                check("total big f$f", FantasyReportLeds.total(n.copy(rows = n.rows.map { it.copy(points = 9.0) }), f))
            }
            n.rows.forEach { r -> for (w in listOf("GOAL", "ASSIST", "2 ASSISTS", "WIN", "SHUTOUT")) check("alert $w", FantasyReportLeds.alert(r, w)) }
            players.forEach { p -> check("out", FantasyReportLeds.out(p, "INJURED RESERVE")) }
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun finalMoments() {
        val stars = listOf("Connor McDavid", "Stuart Skinner", "Alexandre Texier-Brodeur")
        for ((a, h) in pairs) for ((sa, sh) in listOf(4 to 0, 0 to 3, 12 to 11, 5 to 2)) for (cup in listOf(false, true)) for (n in listOf(0, 3)) {
            val g = game(a, h, GameState.OFF, sa, sh)
            val info = FinalMoments.Info(g, stars.take(n), "Stuart Skinner".takeIf { minOf(sa, sh) == 0 }, shutout = minOf(sa, sh) == 0, cup = cup, tag = "FINAL/OT")
            for (f in 0 until FinalMoments.frames(info) + 2) check("final $a-$h $sa-$sh cup=$cup n=$n f$f", FinalMoments.build(info, f))
            FinalMoments.badges(info).forEach { check("badge $a-$h", it) }
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun jumbotronExtras() {
        val flashes = listOf(null, HockeyBoard.Flash(HockeyBoard.Flash.Kind.SAVE, true, HockeyBoard.WHITE, 1), HockeyBoard.Flash(HockeyBoard.Flash.Kind.SAVE, false, HockeyBoard.WHITE, 2),
            HockeyBoard.Flash(HockeyBoard.Flash.Kind.HIT, true, HockeyBoard.WHITE, 3), HockeyBoard.Flash(HockeyBoard.Flash.Kind.HIT, false, HockeyBoard.WHITE, 4))
        val nows = listOf(start.minusSeconds(3500), start.minusSeconds(61), start.minusSeconds(5), start.plusSeconds(120))
        for (g in allGames().filterIndexed { i, _ -> i % 5 == 0 }) for (now in nows) for (fl in flashes) for (round in 0..4) for (frame in 0..44 step 3) {
            val i = HockeyBoard.input(g, HockeyBoard.WHITE, now, "12-10-3" to "8-2-1")
                .copy(rivalry = if (round % 2 == 0) "BATTLE OF NEW YORK" else null, beardRound = round, heartbeat = round == 3,
                    flash = fl?.copy(id = fl.id + frame * 100L + round))
            check("extras ${g.away.abbrev}-${g.home.abbrev} ${g.state} ${g.situation} $now $fl r$round f$frame", HockeyBoard.build(i, frame))
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun winOMeter() {
        val history = List(300) { i ->
            com.bit128.bitsports.nhl.OddsSample(i * 20_000L, 0.5f + 0.45f * kotlin.math.sin(i / 30f), i / 60, i / 90)
        }
        for ((a, h) in pairs) for (share in listOf(null, 0.01f, 0.2f, 0.5f, 0.77f, 0.99f)) for (hist in listOf(emptyList(), history.take(3), history)) {
            val (ai, hi) = TeamInk.pair(a, h)
            check("meter $a-$h $share ${hist.size}", OddsMeter.board(a, h, ai, hi, share, hist, live = true, frame = 0))
            if (share != null) for (age in 0..OddsMeter.SWING_FRAMES) for (from in listOf(0.1f, 0.5f, 0.9f)) {
                check("meter swing $a-$h $from->$share a$age", OddsMeter.board(a, h, ai, hi, share, hist, live = true, frame = age, swingFrom = from, swingAge = age))
            }
            if (share != null) check("strip $a-$h $share", OddsMeter.strip(a, h, ai, hi, share))
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun scoreboardWidget() {
        val games = allGames()
        for (chunk in games.chunked(8)) check("scoreboard", ScoreboardLeds.build(chunk, setOf("EDM", "CAR", "TBL"), HockeyBoard.WHITE, 8))
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    private val names = listOf("Connor McDavid", "Jonathan Marchessault", "Alexis Lafrenière", "Tim Stützle", "Bo Horvat")

    @Test fun notifications() {
        for (g in allGames().filter { it.state.isLive }.take(60)) for (n in names) {
            val goal = GoalEvent(g.id, 1, g.home.abbrev, n, 47, listOf(names[1] to 3, names[2] to 9), Strength.SH, "wrist", null, 2, "REG", "11:19", g.away.score ?: 0, g.home.score ?: 0, null)
            LedScenes.goal(goal, g, HockeyBoard.WHITE).let { s -> (s.frames + s.strips).forEachIndexed { i, f -> check("goal $n $i", f) } }
            LedScenes.live(g, goal, HockeyBoard.WHITE).let { s -> (s.frames + s.strips).forEachIndexed { i, f -> check("live $i", f) } }
        }
        val stats = PeriodStats(2, "REG", 18, 22, 3, 1, 14, 12, "1/4", "0/3", 47, 53)
        for (g in allGames().take(40)) {
            for (label in listOf("1st", "2nd", "3rd", "OT", "2OT")) {
                LedScenes.periodEnd(stats, label, g, 11, 10, HockeyBoard.WHITE).let { s -> (s.frames + s.strips).forEachIndexed { i, f -> check("period $label $i", f) } }
            }
            LedScenes.final(g.copy(state = GameState.OFF), true, names.take(3), HockeyBoard.WHITE)
                .let { s -> (s.frames + s.strips).forEachIndexed { i, f -> check("final $i", f) } }
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun goalMoments() {
        for (style in GoalMoments.STYLES) for (team in NhlTeams.all.map { it.abbrev }) for (f in 0 until GoalMoments.FRAMES + 6) {
            check("moment $style $team f$f", GoalMoments.build(style, team, names[f % names.size], 47, f, before = "9-10", after = "10-10"))
        }
        val specials = listOf(GoalSpecial.HatTrick(3), GoalSpecial.HatTrick(5), GoalSpecial.PowerPlay, GoalSpecial.EmptyNet,
            GoalSpecial.Milestone("1ST NHL GOAL"), GoalSpecial.Milestone("1000TH GOAL"))
        for (sp in specials) for (team in NhlTeams.all.map { it.abbrev }) for (f in 0 until GoalMoments.FRAMES + 6) {
            val g = GoalMoments.build("lamp", team, names[f % names.size], 47, f, sp)
            check("special $sp $team f$f", g)
            check("special strip $sp $team f$f", g.crop(HockeyBoard.ROWS))
        }
        assertTrue(failures.take(15).joinToString("\n"), failures.isEmpty())
    }

    @Test fun messages() {
        for ((t, m, b) in listOf(Triple("PICK YOUR", "TEAMS", "TAP HERE"), Triple("LOADING", "", ""), Triple("NEXT UP", "VGK @ CBJ", "Sun 10:30 PM"), Triple("NO GAMES", "TODAY", ""))) {
            check("message $t", messageBoard(t, m, b, HockeyBoard.WHITE))
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }
}
