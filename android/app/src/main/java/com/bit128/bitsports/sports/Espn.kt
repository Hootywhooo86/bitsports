package com.bit128.bitsports.sports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

// Every league that isn't the NHL comes from ESPN's public site API: free, no key,
// unofficial, can change without notice (README "Data provider"). The NHL keeps its
// own deeper feed (nhl/), so it's listed here only so the Sports tab can order it.

data class League(val key: String, val label: String, val path: String?, val icon: SportIcon)

enum class SportIcon { PUCK, FOOTBALL, BASKETBALL, BASEBALL, SOCCER }

object Leagues {
    val NHL = League("nhl", "NHL", null, SportIcon.PUCK)
    val all: List<League> = listOf(
        NHL,
        League("nfl", "NFL", "football/nfl", SportIcon.FOOTBALL),
        League("nba", "NBA", "basketball/nba", SportIcon.BASKETBALL),
        League("mlb", "MLB", "baseball/mlb", SportIcon.BASEBALL),
        League("wnba", "WNBA", "basketball/wnba", SportIcon.BASKETBALL),
        League("cfb", "College football", "football/college-football", SportIcon.FOOTBALL),
        League("mls", "MLS", "soccer/usa.1", SportIcon.SOCCER),
        League("epl", "Premier League", "soccer/eng.1", SportIcon.SOCCER),
    )
    val defaultWatched: Set<String> = setOf("nhl", "nfl", "nba", "mlb")

    fun byKey(key: String) = all.firstOrNull { it.key == key }

    /** Free-typed sport text ("nhl", "Hockey", "Premier League") → a league, if it's one we carry. */
    fun match(sport: String): League? {
        val q = sport.trim().lowercase()
        return all.firstOrNull { it.key == q || it.label.lowercase() == q } ?: when (q) {
            "hockey" -> NHL
            "football" -> byKey("nfl")
            "basketball" -> byKey("nba")
            "baseball" -> byKey("mlb")
            "soccer" -> byKey("mls")
            "epl", "english premier league" -> byKey("epl")
            "ncaaf", "college" -> byKey("cfb")
            else -> null
        }
    }
}

enum class EspnState { PRE, LIVE, POST }

data class EspnTeam(
    val id: String,
    val abbrev: String,
    val name: String,
    val shortName: String,
    val color: Long?,
    val altColor: Long?,
    val score: String?,
    val record: String?,
    val winner: Boolean,
) {
    fun matches(text: String): Boolean {
        val q = norm(text)
        return q.isNotEmpty() && listOf(name, shortName, abbrev).any { norm(it) == q }
    }
}

data class EspnLeader(val label: String, val who: String, val line: String)

data class EspnGame(
    val id: String,
    val league: League,
    val start: Instant,
    val state: EspnState,
    val status: String,
    val away: EspnTeam,
    val home: EspnTeam,
    val venue: String?,
    val tv: List<String>,
    val leaders: List<EspnLeader>,
    /** Down and distance, outs and count, last play — whatever ESPN says is happening now. */
    val situation: String?,
) {
    val isLive get() = state == EspnState.LIVE
    val isOver get() = state == EspnState.POST
    fun involves(team: String) = away.matches(team) || home.matches(team)
}

object EspnParser {
    fun scoreboard(json: String, league: League): List<EspnGame> =
        JSONObject(json).optJSONArray("events").objects().mapNotNull { e -> runCatching { game(e, league) }.getOrNull() }

    fun teams(json: String): List<EspnTeam> =
        JSONObject(json).optJSONArray("sports").objects().flatMap { it.optJSONArray("leagues").objects() }
            .flatMap { it.optJSONArray("teams").objects() }
            .mapNotNull { it.optJSONObject("team") }
            .map { team(it, null) }
            .sortedBy { it.name }

    private fun game(e: JSONObject, league: League): EspnGame {
        val c = e.getJSONArray("competitions").getJSONObject(0)
        val teams = c.optJSONArray("competitors").objects()
        val away = teams.firstOrNull { it.optString("homeAway") == "away" } ?: teams[1]
        val home = teams.firstOrNull { it.optString("homeAway") == "home" } ?: teams[0]
        val type = e.optJSONObject("status")?.optJSONObject("type")
        val state = when (type?.optString("state")) {
            "in" -> EspnState.LIVE
            "post" -> EspnState.POST
            else -> EspnState.PRE
        }
        val sit = c.optJSONObject("situation")
        val situation = listOfNotNull(
            sit?.optString("downDistanceText")?.ifBlank { null },
            sit?.takeIf { it.has("outs") }?.let { s ->
                val bases = listOf("onFirst" to "1st", "onSecond" to "2nd", "onThird" to "3rd")
                    .filter { s.optBoolean(it.first) }.map { it.second }
                "${s.optInt("outs")} out" + if (bases.isEmpty()) "" else ", on ${bases.joinToString(" & ")}"
            },
            sit?.optJSONObject("lastPlay")?.optString("text")?.ifBlank { null },
        ).joinToString(" · ").ifBlank { null }
        return EspnGame(
            id = e.optString("id"),
            league = league,
            start = runCatching { Instant.parse(fixIso(e.optString("date"))) }.getOrDefault(Instant.EPOCH),
            state = state,
            status = type?.optString("shortDetail").orEmpty(),
            away = team(away.getJSONObject("team"), away),
            home = team(home.getJSONObject("team"), home),
            venue = c.optJSONObject("venue")?.optString("fullName")?.ifBlank { null },
            tv = c.optJSONArray("broadcasts").objects().flatMap { b -> b.optJSONArray("names").strings() }.distinct(),
            leaders = c.optJSONArray("leaders").objects().mapNotNull { l ->
                val top = l.optJSONArray("leaders").objects().firstOrNull() ?: return@mapNotNull null
                EspnLeader(
                    label = l.optString("shortDisplayName").ifBlank { l.optString("displayName") },
                    who = top.optJSONObject("athlete")?.optString("shortName").orEmpty(),
                    line = top.optString("displayValue"),
                )
            },
            situation = situation,
        )
    }

    private fun team(t: JSONObject, competitor: JSONObject?) = EspnTeam(
        id = t.optString("id"),
        abbrev = t.optString("abbreviation"),
        name = t.optString("displayName"),
        shortName = t.optString("shortDisplayName").ifBlank { t.optString("name") },
        color = hex(t.optString("color")),
        altColor = hex(t.optString("alternateColor")),
        score = competitor?.optString("score")?.ifBlank { null },
        record = competitor?.optJSONArray("records").objects().firstOrNull()?.optString("summary")?.ifBlank { null },
        winner = competitor?.optBoolean("winner") ?: false,
    )

    /** ESPN writes "2026-10-23T00:15Z" (no seconds), which Instant.parse rejects. */
    internal fun fixIso(s: String) = if (Regex("T\\d{2}:\\d{2}Z$").containsMatchIn(s)) s.dropLast(1) + ":00Z" else s

    private fun hex(s: String): Long? = s.takeIf { it.length == 6 }?.toLongOrNull(16)?.let { 0xFF000000 or it }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
}

class EspnSource(private val base: String = "https://site.api.espn.com/apis/site/v2/sports") {
    suspend fun scoreboard(league: League): List<EspnGame> =
        EspnParser.scoreboard(get("$base/${league.path}/scoreboard"), league)

    suspend fun teams(league: League): List<EspnTeam> = EspnParser.teams(get("$base/${league.path}/teams"))

    private suspend fun get(url: String): String {
        var last: IOException? = null
        for (wait in longArrayOf(0, 1_500, 4_000)) {
            if (wait > 0) delay(wait)
            try {
                return fetch(url)
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("ESPN unreachable")
    }

    private suspend fun fetch(url: String): String = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("ESPN answered $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

private fun norm(s: String) = s.trim().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
