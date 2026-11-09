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
    /** Today's games plus how long until the NHL publishes a newer copy (null if unknown). */
    suspend fun scoreboardPaced(): Pair<List<Game>, Long?> = scoreboard() to null
    suspend fun playByPlay(gameId: Long): GameDetail
    suspend fun extras(gameId: Long): GameExtras = GameExtras()
    suspend fun nextGames(abbrev: String): List<Game> = emptyList()
    suspend fun season(abbrev: String): List<ScheduleGame> = emptyList()
    suspend fun standings(): List<StandingRow> = emptyList()
    suspend fun leaders(): List<LeaderBoard> = emptyList()
    /** Today's betting odds by game id (empty when the NHL lists none). */
    suspend fun odds(): Map<Long, GameOdds> = emptyMap()
    /** Odds for [games]: the NHL's before puck drop, in-game ones for the live games among them. */
    suspend fun oddsFor(games: List<Game>): Map<Long, GameOdds> = odds()
    /** A scorer's regular-season career goals, counting [seasonGoals] for this season (null if unknown). */
    suspend fun careerGoals(playerId: Int, seasonGoals: Int): Int? = null
    suspend fun searchPlayers(q: String): List<PlayerRef> = emptyList()
    suspend fun boxScore(gameId: Long): Map<Int, StatLine> = emptyMap()
    suspend fun pointStreak(playerId: Int): Int = 0
    suspend fun injuries(): List<Injury> = emptyList()
}

class DirectNhlSource(
    private val base: String = "https://api-web.nhle.com/v1",
    private val stats: String = "https://api.nhle.com/stats/rest/en",
    private val espn: String = "https://site.api.espn.com/apis/site/v2/sports/hockey/nhl",
    private val espnCore: String = "https://sports.core.api.espn.com/v2/sports/hockey/leagues/nhl",
) : NhlSource {
    override suspend fun scoreboard(): List<Game> = scoreboardPaced().first

    override suspend fun scoreboardPaced(): Pair<List<Game>, Long?> {
        val r = getWithCache("$base/score/now")
        return NhlParser.scoreboard(r.body) to CachePacing.nextPollMs(r.maxAge, r.age)
    }

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

    // The NHL's betting partner for Canada (FanDuel): the market bitsports' users are in.
    override suspend fun odds(): Map<Long, GameOdds> = NhlOddsParser.odds(get("$base/partner-game/CA/now"))

    @Volatile private var espnIds: Map<Long, String> = emptyMap()

    override suspend fun oddsFor(games: List<Game>): Map<Long, GameOdds> {
        val out = HashMap(try { odds() } catch (e: IOException) { emptyMap() })
        val live = games.filter { it.state.isLive }
        if (live.isEmpty()) return out
        if (live.any { it.id !in espnIds }) {
            try { espnIds = espnIds + EspnOdds.eventIds(get("$espn/scoreboard"), live) } catch (e: IOException) { }
        }
        // ESPN's CDN can hold odds for minutes; a 15-second bucket keeps them as fresh as the play.
        val bucket = System.currentTimeMillis() / 15_000
        for (g in live) {
            val id = espnIds[g.id] ?: continue
            try {
                EspnOdds.live(get("$espnCore/events/$id/competitions/$id/odds?lang=en&region=us&b=$bucket"), g.id)?.let { out[g.id] = it }
            } catch (e: IOException) { }
        }
        return out
    }

    override suspend fun careerGoals(playerId: Int, seasonGoals: Int): Int? = try {
        NhlPlayer.careerGoals(get("$base/player/$playerId/landing"), seasonGoals)
    } catch (e: Exception) { null }

    override suspend fun searchPlayers(q: String): List<PlayerRef> =
        NhlFantasy.search(get("https://search.d3.nhle.com/api/v1/search/player?culture=en-us&limit=20&active=true&q=" + java.net.URLEncoder.encode(q, "UTF-8")))

    override suspend fun boxScore(gameId: Long): Map<Int, StatLine> = NhlFantasy.boxScore(get("$base/gamecenter/$gameId/boxscore"), gameId)

    override suspend fun injuries(): List<Injury> = Injuries.parse(get("$espn/injuries"))

    override suspend fun pointStreak(playerId: Int): Int = NhlFantasy.pointStreak(get("$base/player/$playerId/game-log/now"))

    override suspend fun season(abbrev: String): List<ScheduleGame> =
        NhlSeasonParser.season(get("$base/club-schedule-season/${abbrev.uppercase()}/now"))

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
