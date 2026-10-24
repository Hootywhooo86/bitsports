package com.bit128.bitsports.nhl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

// The NHL's public web API: free, no key, unofficial (can change without notice).
// Responses are cached for 19s at the NHL's CDN, so a new copy exists at most every ~20s: polling
// faster only re-reads the same copy. Live polls instead land just after each copy expires.
interface NhlSource {
    suspend fun scoreboard(): List<Game>
    suspend fun playByPlay(gameId: Long): GameDetail
    suspend fun extras(gameId: Long): GameExtras = GameExtras()
    suspend fun nextGames(abbrev: String): List<Game> = emptyList()
    suspend fun standings(): List<StandingRow> = emptyList()
    suspend fun leaders(): List<LeaderBoard> = emptyList()
}

class DirectNhlSource(
    private val base: String = "https://api-web.nhle.com/v1",
    private val stats: String = "https://api.nhle.com/stats/rest/en",
) : NhlSource {
    override suspend fun scoreboard(): List<Game> = NhlParser.scoreboard(get("$base/score/now"))

    override suspend fun playByPlay(gameId: Long): GameDetail {
        val r = getWithCache("$base/gamecenter/$gameId/play-by-play")
        return NhlParser.playByPlay(r.body, Instant.now()).copy(freshInMs = CachePacing.nextPollMs(r.maxAge, r.age))
    }

    /** Everything around the play-by-play. Each piece is optional: one failing never hides the rest. */
    override suspend fun extras(gameId: Long): GameExtras {
        suspend fun optional(url: String) = try { get(url) } catch (e: IOException) { null }
        val landing = optional("$base/gamecenter/$gameId/landing")
        val rail = optional("$base/gamecenter/$gameId/right-rail")
        val box = optional("$base/gamecenter/$gameId/boxscore")
        val shifts = optional("$stats/shiftcharts?cayenneExp=gameId=$gameId")
        return NhlExtrasParser.extras(landing, rail, box, shifts)
    }

    override suspend fun standings(): List<StandingRow> = NhlLeagueParser.standings(get("$base/standings/now"))

    override suspend fun leaders(): List<LeaderBoard> {
        suspend fun optional(url: String) = try { get(url) } catch (e: IOException) { null }
        val skaters = optional("$base/skater-stats-leaders/current?categories=points,goals,assists&limit=10")
        val goalies = optional("$base/goalie-stats-leaders/current?categories=wins,savePctg,goalsAgainstAverage&limit=10")
        if (skaters == null && goalies == null) throw IOException("NHL leaders unreachable")
        return NhlLeagueParser.leaders(skaters, goalies)
    }

    override suspend fun nextGames(abbrev: String): List<Game> =
        NhlParser.scoreboard(get("$base/club-schedule/${abbrev.uppercase()}/week/now"))

    /**
     * A phone drops requests all the time (lift, tunnel, waking from sleep), so one miss gets two
     * quick retries before it counts as the feed being down.
     */
    private suspend fun get(url: String): String = getWithCache(url).body

    private class Response(val body: String, val maxAge: Int?, val age: Int?)

    private suspend fun getWithCache(url: String): Response {
        var last: IOException? = null
        for (wait in RETRY_WAITS) {
            if (wait > 0) delay(wait)
            try {
                return fetch(url)
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("NHL feed unreachable")
    }

    private suspend fun fetch(url: String): Response = withContext(Dispatchers.IO) {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = true // score/now answers with a 307 to today's date
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("User-Agent", "bitsports-android")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("NHL feed answered $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            Response(
                body,
                maxAge = conn.getHeaderField("Cache-Control")?.let { Regex("max-age=(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() },
                age = conn.getHeaderField("Age")?.trim()?.toIntOrNull(),
            )
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        val RETRY_WAITS = longArrayOf(0, 1_500, 4_000)
    }
}

/** When to poll next so each check lands just after the CDN's copy expires. */
object CachePacing {
    /** Slack after expiry, so our request is the one that fetches the new copy rather than a beat too early. */
    private const val SLACK_MS = 700L

    fun nextPollMs(maxAge: Int?, age: Int?): Long? {
        if (maxAge == null || maxAge <= 0) return null
        val left = (maxAge - (age ?: 0)).coerceAtLeast(0)
        return (left * 1000L + SLACK_MS).coerceIn(1_500L, 20_000L)
    }
}
