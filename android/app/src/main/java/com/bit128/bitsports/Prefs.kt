package com.bit128.bitsports

import android.content.Context

// Every look choice is the viewer's, saved on this device. Mirrors the
// Customize sheet in index.html (localStorage "bitsports.prefs").
data class UiPrefs(
    val accent: Long = 0xFFFFFFFF,
    val compactScores: Boolean = false,
    val largeText: Boolean = false,
    val plainHeadings: Boolean = false,
    val showScores: Boolean = true,
    val showNews: Boolean = true,
    val blinkLive: Boolean = true,
)

// Light accents only, so black text on a filled button always reads. No red (that's LIVE).
val ACCENTS: List<Pair<String, Long>> = listOf(
    "White" to 0xFFFFFFFF,
    "Ice" to 0xFF7FD4FF,
    "Lime" to 0xFFC6F24E,
    "Gold" to 0xFFFFD95E,
    "Mint" to 0xFF5EF2B0,
    "Peach" to 0xFFFFB38A,
)

class PrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("bitsports.prefs", Context.MODE_PRIVATE)

    fun load(): UiPrefs {
        val d = UiPrefs()
        return UiPrefs(
            accent = prefs.getLong("accent", d.accent),
            compactScores = prefs.getBoolean("compactScores", d.compactScores),
            largeText = prefs.getBoolean("largeText", d.largeText),
            plainHeadings = prefs.getBoolean("plainHeadings", d.plainHeadings),
            showScores = prefs.getBoolean("showScores", d.showScores),
            showNews = prefs.getBoolean("showNews", d.showNews),
            blinkLive = prefs.getBoolean("blinkLive", d.blinkLive),
        )
    }

    fun save(p: UiPrefs) {
        prefs.edit()
            .putLong("accent", p.accent)
            .putBoolean("compactScores", p.compactScores)
            .putBoolean("largeText", p.largeText)
            .putBoolean("plainHeadings", p.plainHeadings)
            .putBoolean("showScores", p.showScores)
            .putBoolean("showNews", p.showNews)
            .putBoolean("blinkLive", p.blinkLive)
            .apply()
    }
}
