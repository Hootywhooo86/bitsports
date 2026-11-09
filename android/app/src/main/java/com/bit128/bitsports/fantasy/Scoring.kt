package com.bit128.bitsports.fantasy

import com.bit128.bitsports.nhl.StatLine
import org.json.JSONObject

/**
 * How a night turns into fantasy points. Two ready-made setups and your own: every weight can be
 * changed. Goalies score from wins, saves, goals against and shutouts.
 */
data class Scoring(
    val name: String,
    val goal: Double, val assist: Double, val plusMinus: Double, val pim: Double, val ppGoal: Double,
    val shot: Double, val hit: Double, val block: Double,
    val win: Double, val save: Double, val goalAgainst: Double, val shutout: Double,
) {
    fun points(s: StatLine, goalie: Boolean): Double =
        if (goalie) {
            val so = s.goalsAgainst == 0 && s.decision == "W"
            (if (s.decision == "W") win else 0.0) + s.saves * save + s.goalsAgainst * goalAgainst + (if (so) shutout else 0.0) +
                s.goals * goal + s.assists * assist
        } else {
            s.goals * goal + s.assists * assist + s.plusMinus * plusMinus + s.pim * pim + s.ppGoals * ppGoal +
                s.shots * shot + s.hits * hit + s.blocks * block
        }

    fun toJson(): String = JSONObject().put("name", name).put("g", goal).put("a", assist).put("pm", plusMinus).put("pim", pim)
        .put("ppg", ppGoal).put("sog", shot).put("hit", hit).put("blk", block).put("w", win).put("sv", save).put("ga", goalAgainst)
        .put("so", shutout).toString()

    companion object {
        /** Goals and assists only: one point each. */
        val POINTS = Scoring("Points", 1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 2.0, 0.0, 0.0, 1.0)
        /** The kind most leagues use: a little for everything a player does. */
        val STANDARD = Scoring("Standard", 3.0, 2.0, 1.0, 0.5, 1.0, 0.5, 0.5, 0.5, 4.0, 0.2, -1.0, 3.0)
        val PRESETS = listOf(STANDARD, POINTS)

        fun fromJson(s: String?): Scoring? = runCatching {
            val o = JSONObject(s ?: return null)
            Scoring(o.optString("name", "Custom"), o.getDouble("g"), o.getDouble("a"), o.getDouble("pm"), o.getDouble("pim"),
                o.getDouble("ppg"), o.getDouble("sog"), o.getDouble("hit"), o.getDouble("blk"), o.getDouble("w"), o.getDouble("sv"),
                o.getDouble("ga"), o.getDouble("so"))
        }.getOrNull()

        /** "9.5" or "10". */
        fun fmt(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else "%.1f".format(java.util.Locale.ROOT, v)
    }
}
