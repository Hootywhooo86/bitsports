package com.bit128.bitsports.nhl

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Win probability from the score, the time left and the teams' strength: goals as a Poisson
 * process (about 3 a team per 60 minutes this season), the rates tilted so that at puck drop the
 * chances match the betting market's, and a coin-ish overtime that leans the same way. Pure.
 */
object WinProb {
    const val GOALS_PER_60 = 3.0
    private const val REG = 3600

    data class Point(val second: Int, val home: Double, val awayScore: Int, val homeScore: Int)

    data class Swing(val second: Int, val period: Int, val clock: String, val scorer: String?, val team: String, val change: Double)

    data class Summary(
        val now: Double, val atPuckDrop: Double,
        /** What it would be if the team that's behind (or the away side when tied) scored next. */
        val ifGoal: Double, val goalTeam: String,
        val biggest: Swing?, val lowPoint: Point?, val favouredShare: Double,
        val movers: List<Pair<String, Double>>,
        /** Total swing so far, scaled to a full game, for the excitement pace. */
        val excitementPace: Double,
        val timeline: List<Point>,
    )

    /** Home team's chance of winning with [diff] (home minus away) and [secondsLeft], scoring rates per 60 minutes. */
    fun home(diff: Int, secondsLeft: Int, homeRate: Double, awayRate: Double): Double {
        val t = secondsLeft.coerceAtLeast(0) / 3600.0
        val mh = homeRate * t; val ma = awayRate * t
        fun pmf(m: Double, k: Int): Double = if (m <= 0.0) (if (k == 0) 1.0 else 0.0) else exp(-m + k * ln(m) - lnFact(k))
        val ph = DoubleArray(13) { pmf(mh, it) }; val pa = DoubleArray(13) { pmf(ma, it) }
        var win = 0.0; var tie = 0.0
        for (i in 0..12) for (j in 0..12) {
            val p = ph[i] * pa[j]
            val d = diff + i - j
            if (d > 0) win += p else if (d == 0) tie += p
        }
        val ot = homeRate / (homeRate + awayRate)
        return (win + tie * ot).coerceIn(0.001, 0.999)
    }

    private fun lnFact(k: Int): Double = (2..k).sumOf { ln(it.toDouble()) }

    /** Scoring rates (home, away) that make the puck-drop chances [homeShare]; even rates when unknown. */
    fun rates(homeShare: Double?): Pair<Double, Double> {
        if (homeShare == null) return GOALS_PER_60 to GOALS_PER_60
        var lo = 0.3; var hi = 3.0
        repeat(40) {
            val r = (lo + hi) / 2
            val p = home(0, REG, GOALS_PER_60 * sqrt(r), GOALS_PER_60 / sqrt(r))
            if (p < homeShare) lo = r else hi = r
        }
        val r = (lo + hi) / 2
        return GOALS_PER_60 * sqrt(r) to GOALS_PER_60 / sqrt(r)
    }

    /**
     * The whole game: win chances minute by minute and at every goal, the biggest swing, [mine]'s
     * low point and how much of the game they've been favoured, the players whose goals moved it
     * most. [homeShare]: the home side's chance at puck drop (from the odds), when known.
     */
    fun summary(d: GameDetail, homeShare: Double?, mine: String?): Summary {
        val g = d.game
        val (hr, ar) = rates(homeShare)
        val goals = GameAnalysis.goals(d).filter { it.periodType != "SO" }
        val c = g.clock
        val elapsed = when {
            g.state.isOver -> maxOf(REG, goals.maxOfOrNull { Chances.gameSecond(it.period, NhlExtrasParser.seconds(it.timeInPeriod) ?: 0) } ?: REG)
            c == null -> 0
            else -> Chances.gameSecond(c.period, (OnIce.periodLength(g.id, c.periodType) - (NhlExtrasParser.seconds(c.timeRemaining) ?: 0)).coerceAtLeast(0))
        }.coerceAtLeast(0)
        fun scoreAt(sec: Int) = goals.filter { Chances.gameSecond(it.period, NhlExtrasParser.seconds(it.timeInPeriod) ?: 0) <= sec }
            .lastOrNull()?.let { it.awayScore to it.homeScore } ?: (0 to 0)
        fun wp(sec: Int, a: Int, h: Int): Double = if (sec >= REG && a != h) (if (h > a) 1.0 else 0.0)
            else home(h - a, REG - sec.coerceAtMost(REG), hr, ar)
        val timeline = ArrayList<Point>()
        var s = 0
        while (s <= minOf(elapsed, REG)) { val (a, h) = scoreAt(s); timeline += Point(s, wp(s, a, h), a, h); s += 60 }
        goals.forEach { gl ->
            val sec = Chances.gameSecond(gl.period, NhlExtrasParser.seconds(gl.timeInPeriod) ?: 0)
            timeline += Point(sec, wp(sec, gl.awayScore, gl.homeScore), gl.awayScore, gl.homeScore)
        }
        timeline.sortBy { it.second }
        val (na, nh) = if (g.state.isOver) ((g.away.score ?: 0) to (g.home.score ?: 0)) else scoreAt(elapsed)
        val now = if (g.state.isOver) (if (nh > na) 1.0 else if (na > nh) 0.0 else 0.5) else wp(elapsed, na, nh)
        if (timeline.lastOrNull()?.second != elapsed) timeline += Point(elapsed, now, na, nh)
        val start = wp(0, 0, 0)
        // A goal now: by whoever's behind (the away side when it's tied).
        val goalHome = nh < na
        val ifGoal = wp(elapsed, na + (if (goalHome) 0 else 1), nh + (if (goalHome) 1 else 0))
        // Swings at goals: the change across each goal, from the scoring side's point of view.
        val swings = goals.map { gl ->
            val sec = Chances.gameSecond(gl.period, NhlExtrasParser.seconds(gl.timeInPeriod) ?: 0)
            val homeGoal = gl.teamAbbrev == g.home.abbrev
            val before = wp(sec, gl.awayScore - (if (homeGoal) 0 else 1), gl.homeScore - (if (homeGoal) 1 else 0))
            val after = wp(sec, gl.awayScore, gl.homeScore)
            Swing(sec, gl.period, gl.timeInPeriod, gl.scorer, gl.teamAbbrev, if (homeGoal) after - before else before - after)
        }
        val mineHome = mine == g.home.abbrev
        fun mineWp(p: Point) = if (mine == null || mineHome) p.home else 1 - p.home
        val low = if (mine != null) timeline.minByOrNull(::mineWp) else null
        val favoured = if (timeline.size < 2) 0.0 else timeline.zipWithNext().sumOf { (p, q) -> if (mineWp(p) > 0.5) (q.second - p.second).toDouble() else 0.0 } /
            (timeline.last().second - timeline.first().second).coerceAtLeast(1)
        val movers = swings.filter { it.scorer != null }.groupBy { it.scorer!! }.mapValues { (_, l) -> l.sumOf { it.change } }
            .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
        val total = timeline.zipWithNext().sumOf { (p, q) -> kotlin.math.abs(q.home - p.home) }
        val pace = if (elapsed <= 0) 0.0 else total * REG / minOf(elapsed, REG).coerceAtLeast(600)
        return Summary(now, start, ifGoal, if (goalHome) g.home.abbrev else g.away.abbrev, swings.maxByOrNull { it.change }, low,
            favoured, movers, pace, timeline)
    }

    /**
     * Where an excitement pace sits among this season's games (fraction of games calmer). The
     * deciles come from bitsports running the same model over the season's first 290 games.
     */
    fun excitementPercentile(pace: Double): Double {
        val i = EXCITEMENT_DECILES.indexOfFirst { pace < it }
        return if (i < 0) 1.0 else i / 10.0
    }

    /** Total win-probability movement per game, deciles over 282 finished games (Oct 7 to Nov 9, 2026). */
    val EXCITEMENT_DECILES = doubleArrayOf(0.500, 0.670, 0.808, 0.896, 1.003, 1.200, 1.358, 1.624, 1.838)
}
