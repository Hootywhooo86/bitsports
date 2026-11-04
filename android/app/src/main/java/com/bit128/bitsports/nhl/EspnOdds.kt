package com.bit128.bitsports.nhl

import org.json.JSONObject
import java.time.Instant
import java.time.Duration

/**
 * In-game odds. The NHL's own odds feed only refreshes every 5 minutes and its scoreboard drops
 * odds once the puck drops, but ESPN's free odds API carries "DraftKings - Live Odds" that move
 * within a minute of the play (checked 3 November 2026). bitsports uses these while a game is
 * live and the NHL's (FanDuel) before it. Pure parsing; DirectNhlSource does the fetching.
 */
object EspnOdds {
    /** ESPN writes a few teams shorter than the NHL does. */
    private val NHL_ABBREV = mapOf("TB" to "TBL", "NJ" to "NJD", "LA" to "LAK", "SJ" to "SJS", "UTAH" to "UTA")
    fun nhlAbbrev(espn: String) = NHL_ABBREV[espn.uppercase()] ?: espn.uppercase()

    /** ESPN event ids from its scoreboard, matched to NHL games by teams and start time. */
    fun eventIds(scoreboard: String, games: List<Game>): Map<Long, String> {
        val events = JSONObject(scoreboard).optJSONArray("events") ?: return emptyMap()
        val out = HashMap<Long, String>()
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            val comp = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
            val teams = comp.optJSONArray("competitors") ?: continue
            var home: String? = null; var away: String? = null
            for (k in 0 until teams.length()) {
                val c = teams.getJSONObject(k)
                val a = nhlAbbrev(c.optJSONObject("team")?.optString("abbreviation").orEmpty())
                if (c.optString("homeAway") == "home") home = a else away = a
            }
            val start = runCatching { Instant.parse(e.optString("date").let { if (it.length == 17) it.dropLast(1) + ":00Z" else it }) }.getOrNull()
            val g = games.firstOrNull { it.home.abbrev == home && it.away.abbrev == away &&
                (start == null || Duration.between(it.startUtc, start).abs() < Duration.ofHours(12)) } ?: continue
            val id = e.optString("id")
            if (id.isNotBlank()) out[g.id] = id
        }
        return out
    }

    /** The live line if ESPN has one ("DraftKings - Live Odds"), else null. */
    fun live(json: String, gameId: Long, now: Instant = Instant.now()): GameOdds? {
        val items = JSONObject(json).optJSONArray("items") ?: return null
        for (i in 0 until items.length()) {
            val it = items.getJSONObject(i)
            val name = it.optJSONObject("provider")?.optString("name").orEmpty()
            if (!name.contains("Live", ignoreCase = true)) continue
            val total = it.optDouble("overUnder").takeUnless { d -> d.isNaN() }?.let { d -> if (d % 1.0 == 0.0) d.toInt().toString() else d.toString() }
            fun price(o: JSONObject?, key: String) = o?.optDouble(key)?.takeUnless { d -> d.isNaN() }?.toInt()
            fun team(key: String, side: String, sidePrice: String): TeamOdds {
                val t = it.optJSONObject(key)
                val spread = t?.optJSONObject("current")?.optJSONObject("pointSpread")?.optString("american")?.ifBlank { null }
                return TeamOdds(
                    moneyLine = price(t, "moneyLine"),
                    puckLine = spread,
                    puckLinePrice = price(t, "spreadOdds"),
                    total = total?.let { v -> side + v },
                    totalPrice = price(it, sidePrice),
                )
            }
            val away = team("awayTeamOdds", "O", "overOdds")
            val home = team("homeTeamOdds", "U", "underOdds")
            if (away.moneyLine == null && home.moneyLine == null) return null
            return GameOdds(gameId, name.substringBefore(" -").trim() + " live", now, away, home, draw = null)
        }
        return null
    }
}
