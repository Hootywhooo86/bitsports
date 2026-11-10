package com.bit128.bitsports.nhl

import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln

/** One unblocked shot attempt with its chance of going in. */
data class XgShot(
    val home: Boolean, val period: Int, val periodType: String, val second: Int,
    val xg: Double, val goal: Boolean, val onGoal: Boolean, val distance: Double,
    val shooterId: Int?, val goalieId: Int?,
) {
    /** From the slot and the crease: the shots goalies are measured on. */
    val highDanger get() = distance <= 20.0
}

/**
 * Expected goals: how often a shot like this goes in. A logistic model fitted by bitsports on
 * this season's NHL shots (23,445 unblocked attempts, 1,695 goals, AUC 0.74): distance, angle,
 * shot type, and whether the shooter's team had the man advantage (or was short). Shots at an
 * empty net count 0.85.
 */
object Xg {
    private const val INTERCEPT = -0.47154
    private const val DIST = -0.04990
    private const val LOG_DIST = -0.10079
    private const val ANGLE = -0.01615
    private const val PP = 0.31748
    private const val SH = 0.09278
    private val TYPE = mapOf("snap" to 0.34639, "slap" to 0.38536, "backhand" to -0.38457, "tip-in" to -0.95923,
        "deflected" to -0.42109, "wrap-around" to -1.14584)
    private const val EMPTY_NET = 0.85

    fun value(distance: Double, angleDeg: Double, type: String?, pp: Boolean, sh: Boolean): Double {
        val z = INTERCEPT + DIST * distance + LOG_DIST * ln(distance + 1) + ANGLE * angleDeg +
            (if (pp) PP else 0.0) + (if (sh) SH else 0.0) + (TYPE[type?.lowercase()] ?: 0.0)
        return 1 / (1 + exp(-z))
    }

    private val UNBLOCKED = setOf("shot-on-goal", "missed-shot", "goal")

    fun shots(d: GameDetail): List<XgShot> = d.plays
        .filter { it.type in UNBLOCKED && it.periodType != "SO" && it.x != null && it.y != null }
        .map { p ->
            val home = p.teamId == d.game.home.id
            val dx = 89.0 - kotlin.math.abs(p.x!!)
            val dist = hypot(dx, p.y!!.toDouble())
            val angle = if (dx > 0) Math.toDegrees(atan2(kotlin.math.abs(p.y.toDouble()), dx)) else 90.0
            val sc = p.situationCode?.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: "1551"
            val own = (if (home) sc[2] else sc[1]).digitToInt()
            val opp = (if (home) sc[1] else sc[2]).digitToInt()
            val oppGoalie = (if (home) sc[0] else sc[3]).digitToInt()
            val xg = if (oppGoalie == 0) EMPTY_NET else value(dist, angle, p.shotType, own > opp, own < opp)
            val shooter = if (p.details.has("scoringPlayerId")) p.details.optInt("scoringPlayerId") else p.details.optInt("shootingPlayerId", -1)
            XgShot(home, p.period, p.periodType, NhlExtrasParser.seconds(p.timeInPeriod) ?: 0, xg, p.type == "goal", p.type != "missed-shot",
                dist, shooter.takeIf { it > 0 }, if (p.details.has("goalieInNetId")) p.details.optInt("goalieInNetId") else null)
        }
}

/** The game's shots and chances, both sides: what the Team stats card shows. */
data class Chances(
    val awayXg: Double, val homeXg: Double,
    val awayClose: Int, val homeClose: Int,
    val awayAvgDist: Double, val homeAvgDist: Double,
    /** Save % by the goalies of each side (they faced the other side's shots). */
    val awaySavePct: Double?, val homeSavePct: Double?,
    /** Goals saved above expected by each side's goalies. */
    val awayGsax: Double, val homeGsax: Double,
    /** High-danger saves / high-danger shots faced. */
    val awayHd: Pair<Int, Int>, val homeHd: Pair<Int, Int>,
    /** Cumulative xG over the game: (game second, away total, home total). */
    val race: List<Triple<Int, Double, Double>>,
) {
    companion object {
        fun gameSecond(period: Int, second: Int) = (period - 1) * 1200 + second

        fun of(d: GameDetail): Chances {
            val s = Xg.shots(d)
            val a = s.filter { !it.home }; val h = s.filter { it.home }
            fun close(l: List<XgShot>) = l.count { it.distance <= 20 }
            fun avg(l: List<XgShot>) = if (l.isEmpty()) 0.0 else l.sumOf { it.distance } / l.size
            // Each side's goalies faced the other side's shots on goal.
            fun saves(against: List<XgShot>): Double? {
                val on = against.filter { it.onGoal }
                return if (on.isEmpty()) null else 1.0 - on.count { it.goal }.toDouble() / on.size
            }
            fun gsax(against: List<XgShot>) = against.sumOf { it.xg } - against.count { it.goal }
            fun hd(against: List<XgShot>) = against.filter { it.onGoal && it.highDanger }.let { l -> l.count { !it.goal } to l.size }
            var at = 0.0; var ht = 0.0
            val race = listOf(Triple(0, 0.0, 0.0)) + s.sortedBy { gameSecond(it.period, it.second) }.map { x ->
                if (x.home) ht += x.xg else at += x.xg
                Triple(gameSecond(x.period, x.second), at, ht)
            }
            return Chances(a.sumOf { it.xg }, h.sumOf { it.xg }, close(a), close(h), avg(a), avg(h),
                saves(h), saves(a), gsax(h), gsax(a), hd(h), hd(a), race)
        }
    }
}
