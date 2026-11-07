package com.bit128.bitsports.nhl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The NHL's official goal clips, played inside bitsports. Each goal in the NHL feed carries a clip
 * id; the NHL's own web player turns that into a video through its video host (Brightcove), with
 * a key the player publishes in its config. bitsports asks the same way, so the clip is the
 * official one, ads-free as on nhl.com.
 */
data class Clip(val id: Long, val title: String, val mp4: String?, val hls: String?, val poster: String?, val durationMs: Long?) {
    /** What the player should open: the MP4 where there is one (plain Android plays it best). */
    val playable: String? get() = mp4 ?: hls
}

object NhlClips {
    const val ACCOUNT = "6415718365001"
    private const val PLAYER = "D3UCGynRWU_default"
    @Volatile private var policyKey: String? = null

    /** The clip id from a share link (".../edm-njd-bouchard-scores-goal-...-6407872711112"). */
    fun idFrom(url: String?): Long? = url?.substringAfterLast('-')?.takeWhile(Char::isDigit)?.takeIf { it.length >= 10 }?.toLongOrNull()

    /** Pure: the playback API's answer as a [Clip]; the best HTTPS MP4, else HTTPS HLS. */
    fun parse(json: String): Clip {
        val o = JSONObject(json)
        val sources = o.optJSONArray("sources")
        var mp4: String? = null; var mp4h = -1; var hls: String? = null
        for (i in 0 until (sources?.length() ?: 0)) {
            val s = sources!!.getJSONObject(i)
            val src = s.optString("src").takeIf { it.startsWith("https://") } ?: continue
            when {
                s.optString("container") == "MP4" -> { val h = s.optInt("height"); if (h > mp4h) { mp4 = src; mp4h = h } }
                s.optString("type") == "application/x-mpegURL" && hls == null -> hls = src
            }
        }
        return Clip(o.optString("id").toLongOrNull() ?: 0, o.optString("name"), mp4, hls,
            o.optString("poster").ifBlank { null }, o.optLong("duration").takeIf { it > 0 })
    }

    suspend fun clip(id: Long): Clip = withContext(Dispatchers.IO) {
        val key = policyKey ?: JSONObject(get("https://players.brightcove.net/$ACCOUNT/$PLAYER/config.json"))
            .getJSONObject("video_cloud").getString("policy_key").also { policyKey = it }
        try {
            parse(get("https://edge.api.brightcove.com/playback/v1/accounts/$ACCOUNT/videos/$id", "application/json;pk=$key"))
        } catch (e: IOException) {
            // The key can change when the NHL updates its player: fetch it fresh once.
            policyKey = null
            throw e
        }
    }

    private fun get(url: String, accept: String = "application/json"): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000; c.readTimeout = 15_000
            c.setRequestProperty("Accept", accept)
            c.setRequestProperty("User-Agent", "bitsports-android")
            if (c.responseCode !in 200..299) throw IOException("Clip host answered ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
