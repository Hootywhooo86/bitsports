package com.bit128.bitsports.nhl

/** One TV or streaming broadcast of a game, as the NHL lists it. market: N national, H home, A away. */
data class Broadcast(val network: String, val country: String, val market: String)

/**
 * Every legal way to follow a game right now, free ones first: the NHL's broadcast list turned
 * into the official service that streams each channel, plus the free radio call, the in-app goal
 * clips and NHL.com's GameCenter.
 */
object WatchOptions {
    enum class Kind { STREAM, TV, RADIO, CLIPS, WEB }

    data class Option(
        val name: String,
        val detail: String,
        /** Where to go; null when it's a TV channel with no stream we can link (your cable box). */
        val url: String?,
        val free: Boolean,
        val kind: Kind,
        val country: String = "",
    )

    private data class Service(val name: String, val url: String?, val free: Boolean = false, val kind: Kind = Kind.STREAM)

    /** Network code (as the NHL writes it) to the service that streams it. */
    private fun service(network: String): Service? {
        val n = network.uppercase().trim()
        return when {
            n == "CBC" || n.startsWith("CBC ") || n == "HNIC" -> Service("CBC Gem", "https://gem.cbc.ca/", free = true)
            n.startsWith("SN") && !n.startsWith("SN-") || n == "SPORTSNET" -> Service("Sportsnet+", "https://watch.sportsnet.ca/")
            n.startsWith("TSN") -> Service("TSN+", "https://www.tsn.ca/live")
            n.startsWith("RDS") -> Service("RDS", "https://www.rds.ca/direct")
            n.startsWith("TVAS") || n == "TVA" -> Service("TVA+", "https://www.tvaplus.ca/")
            n == "PRIME" || n == "PRIME VIDEO" -> Service("Prime Video", "https://www.primevideo.com/")
            n == "ESPN+" || n == "ESPN" || n == "ESPN2" || n == "ABC" || n == "DISNEY+" || n == "HULU" ->
                Service(if (n == "ABC") "ABC (ESPN app)" else if (n == "DISNEY+" || n == "HULU") n.lowercase().replaceFirstChar { it.uppercase() } else "ESPN", "https://www.espn.com/watch/")
            n == "TNT" || n == "TRUTV" || n == "HBO MAX" || n == "MAX" -> Service("HBO Max", "https://play.max.com/")
            n == "NHLN" -> Service("NHL Network", "https://www.nhl.com/info/nhl-network")
            else -> null
        }
    }

    fun of(
        game: Game,
        broadcasts: List<Broadcast>,
        radio: NhlRadio.Station?,
        hasClips: Boolean,
        home: String = "CA",
    ): List<Option> {
        val out = ArrayList<Option>()
        for (b in broadcasts) {
            val s = service(b.network)
            val where = when (b.market) { "N" -> "national"; "H" -> "${game.home.abbrev} region"; "A" -> "${game.away.abbrev} region"; else -> "" }
            val detail = listOf(b.network, where, if (b.country == "CA") "Canada" else if (b.country == "US") "US" else b.country)
                .filter { it.isNotBlank() }.joinToString(" · ")
            out += if (s != null) Option(s.name, detail, s.url, s.free, s.kind, b.country)
            else Option(b.network, "$detail · on TV or the team's app", null, false, Kind.TV, b.country)
        }
        // One entry per service per country (SN, SN1 and SNW are all Sportsnet+).
        val streams = out.distinctBy { Triple(it.name, it.country, it.url) }
        val extras = buildList {
            if (radio != null && radio.stream != null && !game.state.isOver) add(Option("${radio.name} radio", "The live call, in bitsports", radio.stream, true, Kind.RADIO))
            if (hasClips) add(Option("Goal clips", "Every goal so far, with the bitsports overlays", null, true, Kind.CLIPS))
            add(Option("NHL GameCenter", "Highlights and condensed game, free on NHL.com", "https://www.nhl.com/gamecenter/${game.id}", true, Kind.WEB))
        }
        // Free first; then your country's services; then the rest.
        return (streams + extras).sortedWith(compareBy<Option>({ !it.free }, { it.country.isNotEmpty() && it.country != home }, { it.url == null }))
    }
}
