package com.bit128.bitsports.nhl

/**
 * The season "tape" for a team, from MoneyPuck's free team data (credit: MoneyPuck.com): how much
 * of the 5-on-5 chances it gets, how well it finishes them, its bounces, and PDO, each with its
 * league rank.
 */
data class Tape(
    val team: String, val games: Int,
    /** 5-on-5 share of expected goals, 0..1. */
    val chanceShare: Double,
    /** Goals minus expected goals, per game. */
    val finishing: Double,
    /** Finishing plus goaltending (expected against minus allowed), per game. */
    val bounces: Double,
    /** 5-on-5 shooting % plus save %, x100. */
    val pdo: Double,
    val ranks: Map<String, Int>,
)

object TapeParser {
    fun parse(csv: String): Map<String, Tape> {
        val lines = csv.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyMap()
        val head = lines.first().split(",")
        fun col(name: String) = head.indexOf(name)
        val ti = col("team"); val si = col("situation"); val gp = col("games_played"); val xgp = col("xGoalsPercentage")
        val gf = col("goalsFor"); val xgf = col("xGoalsFor"); val ga = col("goalsAgainst"); val xga = col("xGoalsAgainst")
        val sf = col("shotsOnGoalFor"); val sa = col("shotsOnGoalAgainst")
        val rows = lines.drop(1).map { it.split(",") }.filter { it.size == head.size }
        fun d(r: List<String>, i: Int) = r[i].toDoubleOrNull() ?: 0.0
        val all = rows.filter { it[si] == "all" }.associateBy { it[ti] }
        val ev = rows.filter { it[si] == "5on5" }.associateBy { it[ti] }
        val tapes = all.mapNotNull { (team, a) ->
            val e = ev[team] ?: return@mapNotNull null
            val g = d(a, gp).coerceAtLeast(1.0)
            val fin = (d(a, gf) - d(a, xgf)) / g
            val bounce = fin + (d(a, xga) - d(a, ga)) / g
            val pdo = ((if (d(e, sf) > 0) d(e, gf) / d(e, sf) else 0.0) + (if (d(e, sa) > 0) 1 - d(e, ga) / d(e, sa) else 0.0)) * 100
            Tape(team, g.toInt(), d(e, xgp), fin, bounce, pdo, emptyMap())
        }
        fun rank(sel: (Tape) -> Double) = tapes.sortedByDescending(sel).mapIndexed { i, t -> t.team to i + 1 }.toMap()
        val rc = rank { it.chanceShare }; val rf = rank { it.finishing }; val rb = rank { it.bounces }; val rp = rank { it.pdo }
        return tapes.associate { t -> t.team to t.copy(ranks = mapOf("chance" to rc[t.team]!!, "finishing" to rf[t.team]!!, "bounces" to rb[t.team]!!, "pdo" to rp[t.team]!!)) }
    }
}
