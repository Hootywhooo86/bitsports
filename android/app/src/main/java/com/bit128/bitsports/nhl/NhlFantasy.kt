package com.bit128.bitsports.nhl

import org.json.JSONArray
import org.json.JSONObject

/** A player you can put on a fantasy list. */
data class PlayerRef(val id: Int, val name: String, val team: String, val pos: String, val number: Int? = null) {
    val isGoalie get() = pos == "G"
    val lastName get() = name.substringAfter(' ', name)
}

/** One player's line in one game, from the box score. Goalies use the goalie fields. */
data class StatLine(
    val playerId: Int,
    val gameId: Long,
    val goals: Int = 0,
    val assists: Int = 0,
    val plusMinus: Int = 0,
    val pim: Int = 0,
    val ppGoals: Int = 0,
    val shots: Int = 0,
    val hits: Int = 0,
    val blocks: Int = 0,
    val toi: String? = null,
    // Goalies
    val saves: Int = 0,
    val goalsAgainst: Int = 0,
    val decision: String? = null,
    val starter: Boolean = false,
) {
    val points get() = goals + assists
}

object NhlFantasy {
    /** The NHL's player search ("mcd" finds McDavid). */
    fun search(json: String): List<PlayerRef> {
        val a = JSONArray(json)
        return (0 until a.length()).mapNotNull { i ->
            val o = a.getJSONObject(i)
            val id = o.optString("playerId").toIntOrNull() ?: return@mapNotNull null
            PlayerRef(id, o.optString("name"), o.optString("teamAbbrev").ifBlank { o.optString("lastTeamAbbrev") },
                o.optString("positionCode"), o.optInt("sweaterNumber").takeIf { it > 0 })
        }
    }

    /** Every player's line in a game's box score, by player id. */
    fun boxScore(json: String, gameId: Long): Map<Int, StatLine> {
        val o = JSONObject(json).optJSONObject("playerByGameStats") ?: return emptyMap()
        val out = HashMap<Int, StatLine>()
        for (side in listOf("awayTeam", "homeTeam")) {
            val t = o.optJSONObject(side) ?: continue
            for (group in listOf("forwards", "defense")) {
                val a = t.optJSONArray(group) ?: continue
                for (i in 0 until a.length()) {
                    val p = a.getJSONObject(i)
                    val id = p.optInt("playerId")
                    out[id] = StatLine(id, gameId, p.optInt("goals"), p.optInt("assists"), p.optInt("plusMinus"), p.optInt("pim"),
                        p.optInt("powerPlayGoals"), p.optInt("sog"), p.optInt("hits"), p.optInt("blockedShots"), p.optString("toi").ifBlank { null })
                }
            }
            val g = t.optJSONArray("goalies") ?: continue
            for (i in 0 until g.length()) {
                val p = g.getJSONObject(i)
                val id = p.optInt("playerId")
                out[id] = StatLine(id, gameId, p.optInt("goals"), p.optInt("assists"), pim = p.optInt("pim"), toi = p.optString("toi").ifBlank { null },
                    saves = p.optInt("saves"), goalsAgainst = p.optInt("goalsAgainst"), decision = p.optString("decision").ifBlank { null },
                    starter = p.optBoolean("starter"))
            }
        }
        return out
    }

    /** Games in a row with a point, newest first in the log; 0 if the last game had none. */
    fun pointStreak(gameLog: String): Int {
        val a = JSONObject(gameLog).optJSONArray("gameLog") ?: return 0
        var n = 0
        for (i in 0 until a.length()) { if (a.getJSONObject(i).optInt("points") > 0) n++ else break }
        return n
    }
}
