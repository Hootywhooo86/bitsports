package com.bit128.bitsports.nhl

import java.text.Normalizer

// The 32 NHL teams, their two main colours (for the flat pixel crest) and the
// loose ways fans type them. A match must be unique: "New York" fits two teams, so it's null.
object NhlTeams {
    val all: List<NhlTeam> = listOf(
        NhlTeam("ANA", "Anaheim", "Ducks", 0xFFF47A38 to 0xFFB9975B),
        NhlTeam("BOS", "Boston", "Bruins", 0xFFFFB81C to 0xFF000000, listOf("b's")),
        NhlTeam("BUF", "Buffalo", "Sabres", 0xFF003087 to 0xFFFFB81C),
        NhlTeam("CGY", "Calgary", "Flames", 0xFFC8102E to 0xFFF1BE48),
        NhlTeam("CAR", "Carolina", "Hurricanes", 0xFFCC0000 to 0xFF000000, listOf("canes")),
        NhlTeam("CHI", "Chicago", "Blackhawks", 0xFFCF0A2C to 0xFF000000, listOf("hawks")),
        NhlTeam("COL", "Colorado", "Avalanche", 0xFF6F263D to 0xFF236192, listOf("avs")),
        NhlTeam("CBJ", "Columbus", "Blue Jackets", 0xFF002654 to 0xFFCE1126, listOf("jackets", "cbj")),
        NhlTeam("DAL", "Dallas", "Stars", 0xFF006847 to 0xFF8F8F8C),
        NhlTeam("DET", "Detroit", "Red Wings", 0xFFCE1126 to 0xFFFFFFFF, listOf("wings")),
        NhlTeam("EDM", "Edmonton", "Oilers", 0xFF041E42 to 0xFFFC4C02, listOf("oil")),
        NhlTeam("FLA", "Florida", "Panthers", 0xFFC8102E to 0xFF041E42, listOf("cats")),
        NhlTeam("LAK", "Los Angeles", "Kings", 0xFF111111 to 0xFFA2AAAD, listOf("la kings", "la")),
        NhlTeam("MIN", "Minnesota", "Wild", 0xFF154734 to 0xFFA6192E),
        NhlTeam("MTL", "Montréal", "Canadiens", 0xFFAF1E2D to 0xFF192168, listOf("habs", "montreal canadians", "canadians")),
        NhlTeam("NSH", "Nashville", "Predators", 0xFFFFB81C to 0xFF041E42, listOf("preds")),
        NhlTeam("NJD", "New Jersey", "Devils", 0xFFCE1126 to 0xFF000000, listOf("nj devils", "jersey")),
        NhlTeam("NYI", "New York", "Islanders", 0xFF00539B to 0xFFF47D30, listOf("isles", "ny islanders")),
        NhlTeam("NYR", "New York", "Rangers", 0xFF0038A8 to 0xFFCE1126, listOf("ny rangers", "blueshirts")),
        NhlTeam("OTT", "Ottawa", "Senators", 0xFFC52032 to 0xFFC2912C, listOf("sens")),
        NhlTeam("PHI", "Philadelphia", "Flyers", 0xFFF74902 to 0xFF000000, listOf("philly")),
        NhlTeam("PIT", "Pittsburgh", "Penguins", 0xFF000000 to 0xFFFCB514, listOf("pens")),
        NhlTeam("SJS", "San Jose", "Sharks", 0xFF006D75 to 0xFFEA7200),
        NhlTeam("SEA", "Seattle", "Kraken", 0xFF001628 to 0xFF99D9D9),
        NhlTeam("STL", "St. Louis", "Blues", 0xFF002F87 to 0xFFFCB514, listOf("saint louis", "st louis")),
        NhlTeam("TBL", "Tampa Bay", "Lightning", 0xFF002868 to 0xFFFFFFFF, listOf("bolts", "tampa")),
        NhlTeam("TOR", "Toronto", "Maple Leafs", 0xFF00205B to 0xFFFFFFFF, listOf("leafs")),
        NhlTeam("UTA", "Utah", "Mammoth", 0xFF71AFE5 to 0xFF090909),
        NhlTeam("VAN", "Vancouver", "Canucks", 0xFF00205B to 0xFF00843D, listOf("nucks")),
        NhlTeam("VGK", "Vegas", "Golden Knights", 0xFFB4975A to 0xFF333F42, listOf("knights", "las vegas")),
        NhlTeam("WSH", "Washington", "Capitals", 0xFFC8102E to 0xFF041E42, listOf("caps")),
        NhlTeam("WPG", "Winnipeg", "Jets", 0xFF041E42 to 0xFF004C97),
    )

    private val byAbbrevMap = all.associateBy { it.abbrev }
    fun byAbbrev(a: String): NhlTeam? = byAbbrevMap[a.uppercase()]

    fun match(text: String): NhlTeam? {
        val q = norm(text)
        if (q.isEmpty()) return null
        val hits = all.filter { t ->
            (listOf(t.abbrev, t.city, t.nickname, t.fullName) + t.aliases).any { norm(it) == q }
        }
        return hits.singleOrNull()
    }

    private fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9' ]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}
