package com.bit128.bitsports

data class FollowedTeam(val team: String, val sport: String) {
    val key: String get() = "${slugify(sport)}:${slugify(team)}"
}

data class NewsItem(
    val title: String,
    val link: String,
    val source: String,
    val publishedAt: String?,
    val team: String,
)

fun slugify(s: String): String =
    s.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
