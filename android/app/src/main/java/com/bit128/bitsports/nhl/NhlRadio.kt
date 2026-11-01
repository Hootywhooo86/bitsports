package com.bit128.bitsports.nhl

import java.net.URLEncoder

/**
 * Where to listen to a team's radio call. Only stations checked by hand are listed. Each has its
 * own official listen-live page and, where the station publishes one, the audio stream its play
 * button uses, which bitsports plays itself (ads and any blackout stay the broadcaster's). Every
 * other team gets a TuneIn search for its radio call, and you can set your own in Settings: a
 * direct stream link plays in the app, a web page opens in the browser.
 */
object NhlRadio {
    data class Station(val name: String, val url: String, val official: Boolean, val stream: String? = null)

    private val KNOWN = mapOf(
        // Oilers radio moved from 630 to 880 AM in October 2024; Corus renewed the rights in March 2026.
        // The stream is the one globalnews.ca's 880 CHED player plays (checked November 2026).
        "EDM" to Station("880 CHED", "https://globalnews.ca/radio/880ched/player/", official = true,
            stream = "https://corus.leanstream.co/CHEDAM-MP3?type=.mp3&playertype=dplayer"),
    )

    fun forTeam(abbrev: String, custom: String? = null): Station {
        custom?.trim()?.takeIf { it.startsWith("http") }?.let {
            return Station("Your station", it, official = false, stream = it.takeIf(::looksLikeStream))
        }
        KNOWN[abbrev.uppercase()]?.let { return it }
        val team = NhlTeams.byAbbrev(abbrev)?.fullName ?: abbrev
        return Station("Find the $team radio call", "https://tunein.com/search/?query=" + URLEncoder.encode("$team radio", "UTF-8"), official = false)
    }

    /** An audio stream rather than a web page: an audio file type, or a typical stream host or path. */
    fun looksLikeStream(url: String): Boolean {
        val u = url.lowercase().substringBefore('#')
        val path = u.substringBefore('?')
        return Regex("""\.(mp3|aac|m4a|ogg|opus|m3u8|pls|m3u)$""").containsMatchIn(path) ||
            Regex("""type=\.(mp3|aac)""").containsMatchIn(u) ||
            listOf("leanstream.co", "streamtheworld.com", "streamguys", "icecast", "shoutcast", "/stream", ":8000/", "live.mp3")
                .any { it in u }
    }
}
