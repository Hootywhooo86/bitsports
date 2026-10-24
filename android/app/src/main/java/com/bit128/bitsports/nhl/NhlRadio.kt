package com.bit128.bitsports.nhl

import java.net.URLEncoder

/**
 * Where to listen to a team's radio call. Only stations checked by hand are listed, each with its
 * own official listen-live page (so rights, ads and any geo-blocking stay the broadcaster's);
 * every other team gets a TuneIn search for its radio call, and you can set your own in Settings.
 */
object NhlRadio {
    data class Station(val name: String, val url: String, val official: Boolean)

    private val KNOWN = mapOf(
        // Oilers radio moved from 630 to 880 AM in October 2024; Corus renewed the rights in March 2026.
        "EDM" to Station("880 CHED", "https://globalnews.ca/radio/880ched/player/", official = true),
    )

    fun forTeam(abbrev: String, custom: String? = null): Station {
        custom?.trim()?.takeIf { it.startsWith("http") }?.let { return Station("Your station", it, official = false) }
        KNOWN[abbrev.uppercase()]?.let { return it }
        val team = NhlTeams.byAbbrev(abbrev)?.fullName ?: abbrev
        return Station("Find the $team radio call", "https://tunein.com/search/?query=" + URLEncoder.encode("$team radio", "UTF-8"), official = false)
    }
}
