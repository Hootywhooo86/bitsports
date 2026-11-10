package com.bit128.bitsports.nhl

import org.json.JSONObject
import java.text.Normalizer
import java.time.Instant

/**
 * The lines a team is starting with, as reported before the game (morning skate, warm-ups) and
 * collected by Daily Faceoff with who reported them and when. Read from the team's public
 * line-combinations page.
 */
data class StartingLines(
    val team: String,
    /** Four forward lines, each LW, C, RW. */
    val forwards: List<List<String>>,
    /** Three D pairs, LD then RD. */
    val defense: List<List<String>>,
    /** Starter first. */
    val goalies: List<String>,
    val pp: List<List<String>>,
    val pk: List<List<String>>,
    val injured: List<String>,
    /** Players listed as a game-time decision. */
    val gtd: Set<String>,
    val updated: Instant?,
    /** Who reported them ("Jason Gregor"). */
    val source: String?,
) {
    /** Every unit as a set of names, by key ("f1", "d2", "pp1"), to spot changes. */
    fun units(): Map<String, Set<String>> =
        forwards.mapIndexed { i, l -> "f${i + 1}" to l.toSet() }.toMap() +
            defense.mapIndexed { i, l -> "d${i + 1}" to l.toSet() } + pp.mapIndexed { i, l -> "pp${i + 1}" to l.toSet() }
}

object StartingLinesParser {
    /** The team's page on Daily Faceoff: "edmonton-oilers", "montreal-canadiens", "st-louis-blues". */
    fun slug(fullName: String): String = Normalizer.normalize(fullName, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "").lowercase().replace(".", "").trim().replace(Regex("\\s+"), "-")

    fun parse(html: String, team: String): StartingLines? {
        val json = Regex("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return null
        val c = JSONObject(json).optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("combinations") ?: return null
        val players = c.optJSONArray("players") ?: return null
        data class P(val name: String, val cat: String, val group: String, val pos: String, val gtd: Boolean)
        val all = (0 until players.length()).map { i ->
            val o = players.getJSONObject(i)
            P(o.optString("name"), o.optString("categoryIdentifier"), o.optString("groupIdentifier"), o.optString("positionIdentifier"), o.optBoolean("gameTimeDecision"))
        }
        val order = listOf("lw", "c", "rw", "ld", "rd", "g1", "g2", "sk1", "sk2", "sk3", "sk4", "sk5")
        fun group(cat: String, g: String) = all.filter { it.cat == cat && it.group == g }.sortedBy { order.indexOf(it.pos).let { i -> if (i < 0) 99 else i } }.map { it.name }
        return StartingLines(
            team = team,
            forwards = (1..4).map { group("ev", "f$it") }.filter { it.isNotEmpty() },
            defense = (1..3).map { group("ev", "d$it") }.filter { it.isNotEmpty() },
            goalies = group("ev", "g"),
            pp = (1..2).map { group("pp", "pp$it") }.filter { it.isNotEmpty() },
            pk = (1..2).map { group("pk", "pk$it") }.filter { it.isNotEmpty() },
            injured = all.filter { it.cat == "oi" }.map { it.name },
            gtd = all.filter { it.gtd }.map { it.name }.toSet(),
            updated = c.optString("updatedAt").takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() },
            source = c.optString("sourceName").ifBlank { null },
        )
    }

    /** Units that weren't together last time: compared with [before], the team's previous lines. */
    fun changed(now: StartingLines, before: StartingLines?): Set<String> {
        if (before == null) return emptySet()
        val old = before.units().values.toSet()
        return now.units().filter { (_, names) -> names !in old }.keys
    }
}
