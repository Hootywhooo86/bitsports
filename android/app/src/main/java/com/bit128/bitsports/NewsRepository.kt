package com.bit128.bitsports

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// Same Supabase edge function the web prototype (index.html) calls. The publishable
// key is safe to ship in the app; that's its purpose. Source toggles (SB Nation,
// Reddit, YouTube, etc.) land server-side in the edge function next.
private const val NEWS_URL = "https://wvflpwtaszpstmjpkrhu.supabase.co/functions/v1/fetch-team-news"
private const val NEWS_KEY = "sb_publishable_dO8uP74cTV1tbuqESgxysw_323C7lWh"

sealed class NewsResult {
    data class Ok(val items: List<NewsItem>) : NewsResult()
    data class Error(val message: String) : NewsResult()
}

object NewsRepository {
    suspend fun fetch(team: String, sport: String): NewsResult = withContext(Dispatchers.IO) {
        try {
            fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
            val url = URL("$NEWS_URL?team=${enc(team)}&sport=${enc(sport)}&league=${enc(sport)}")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $NEWS_KEY")
            conn.setRequestProperty("apikey", NEWS_KEY)
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            conn.disconnect()

            if (code !in 200..299) return@withContext NewsResult.Error("status $code")

            val json = JSONObject(body)
            if (json.has("error")) return@withContext NewsResult.Error(json.getString("error"))

            val arr = json.getJSONArray("items")
            val items = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                NewsItem(
                    title = o.getString("title"),
                    link = o.getString("link"),
                    source = o.optString("source", "News"),
                    publishedAt = o.optString("published_at").ifEmpty { o.optString("publishedAt") }.ifEmpty { null },
                    team = team,
                )
            }
            NewsResult.Ok(items)
        } catch (e: Exception) {
            NewsResult.Error(e.message ?: "network error")
        }
    }
}
