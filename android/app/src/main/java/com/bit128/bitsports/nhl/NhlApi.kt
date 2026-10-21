package com.bit128.bitsports.nhl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

// The NHL's public web API: free, no key, unofficial (can change without notice).
// Responses are cached ~17s upstream, so callers never poll faster than every 15s.
interface NhlSource {
    suspend fun scoreboard(): List<Game>
    suspend fun playByPlay(gameId: Long): GameDetail
}

class DirectNhlSource(private val base: String = "https://api-web.nhle.com/v1") : NhlSource {
    override suspend fun scoreboard(): List<Game> = NhlParser.scoreboard(get("score/now"))

    override suspend fun playByPlay(gameId: Long): GameDetail =
        NhlParser.playByPlay(get("gamecenter/$gameId/play-by-play"), Instant.now())

    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val conn = URL("$base/$path").openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = true // score/now answers with a 307 to today's date
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) throw IOException("NHL feed answered $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
