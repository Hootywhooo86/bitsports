package com.bit128.bitsports.nhl

import org.json.JSONObject
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Betting odds as the NHL itself publishes them (its betting partner for a country: FanDuel in
 * Canada), free and without a key. The NHL refreshes the list every few minutes before a game and
 * keeps it current while the game is on; its CDN holds a copy for about 8 seconds.
 */
data class TeamOdds(
    /** American odds to win, overtime and shootout included: -150, +125. */
    val moneyLine: Int?,
    /** Regulation only: -1.5 / +1.5 goals, and the price. */
    val puckLine: String?,
    val puckLinePrice: Int?,
    /** "O6.5" or "U6.5" with its price; each team carries one side of the total. */
    val total: String?,
    val totalPrice: Int?,
)

data class GameOdds(
    val gameId: Long,
    val partner: String,
    val updated: Instant?,
    val away: TeamOdds,
    val home: TeamOdds,
    /** The draw price in a 3-way (regulation) line, if offered. */
    val draw: Int?,
) {
    /** The goal total the over/under is set at, e.g. 6.5. */
    val totalLine: String? get() = (away.total ?: home.total)?.drop(1)
    val over: Int? get() = listOf(away, home).firstOrNull { it.total?.startsWith("O") == true }?.totalPrice
    val under: Int? get() = listOf(away, home).firstOrNull { it.total?.startsWith("U") == true }?.totalPrice

    /** Who the market favours, null when it's even. */
    fun favourite(awayAbbrev: String, homeAbbrev: String): String? {
        val a = away.moneyLine ?: return null
        val h = home.moneyLine ?: return null
        return when { a < h -> awayAbbrev; h < a -> homeAbbrev; else -> null }
    }
}

object NhlOddsParser {
    fun odds(json: String): Map<Long, GameOdds> {
        val root = JSONObject(json)
        val partner = root.optJSONObject("bettingPartner")?.optString("name")?.ifBlank { null } ?: "NHL betting partner"
        val updated = root.optString("lastUpdatedUTC").takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val games = root.optJSONArray("games") ?: return emptyMap()
        val out = LinkedHashMap<Long, GameOdds>()
        for (i in 0 until games.length()) {
            val g = games.getJSONObject(i)
            val id = g.optLong("gameId").takeIf { it > 0 } ?: continue
            var draw: Int? = null
            fun team(key: String): TeamOdds {
                val list = g.optJSONObject(key)?.optJSONArray("odds")
                var ml: Int? = null; var pl: String? = null; var plp: Int? = null; var tot: String? = null; var totp: Int? = null
                for (k in 0 until (list?.length() ?: 0)) {
                    val o = list!!.getJSONObject(k)
                    val v = o.optDouble("value").takeUnless { it.isNaN() }?.roundToInt()
                    val q = o.optString("qualifier")
                    when (o.optString("description")) {
                        "MONEY_LINE_2_WAY" -> ml = v
                        "PUCK_LINE" -> { pl = q.ifBlank { null }; plp = v }
                        "OVER_UNDER" -> { tot = q.ifBlank { null }; totp = v }
                        "MONEY_LINE_3_WAY" -> if (q == "Draw") draw = v
                    }
                }
                return TeamOdds(ml, pl, plp, tot, totp)
            }
            val away = team("awayTeam")
            val home = team("homeTeam")
            if (listOf(away, home).all { it.moneyLine == null && it.puckLine == null && it.total == null }) continue
            out[id] = GameOdds(id, partner, updated, away, home, draw)
        }
        return out
    }

    /** +125, -150, EVEN. */
    fun american(v: Int?): String = when {
        v == null -> "–"
        v == 100 || v == -100 -> "EVEN"
        v > 0 -> "+$v"
        else -> "$v"
    }

    /** The chance the price implies, before the book's margin: -150 → 60%. */
    fun implied(v: Int): Float = if (v < 0) -v / (-v + 100f) else 100f / (v + 100f)

    /**
     * The widget's one line: "EDM +134 · NJD -162 · O/U 6.5". Your team first when you follow one
     * of them, so it reads like the score line above it.
     */
    fun line(o: GameOdds, awayAbbrev: String, homeAbbrev: String, mine: String? = null): String {
        val a = "$awayAbbrev ${american(o.away.moneyLine)}"
        val h = "$homeAbbrev ${american(o.home.moneyLine)}"
        val teams = if (mine == homeAbbrev) "$h · $a" else "$a · $h"
        return listOfNotNull(teams, o.totalLine?.let { "O/U $it" }).joinToString(" · ")
    }
}
