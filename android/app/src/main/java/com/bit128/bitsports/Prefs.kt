package com.bit128.bitsports

import android.content.Context
import com.bit128.bitsports.alerts.AlertPrefs

// Every look choice is the viewer's, saved on this device. Mirrors the
// Settings tab here and the Customize sheet in index.html (localStorage "bitsports.prefs").
data class UiPrefs(
    val accent: Long = 0xFFFFFFFF,
    val compactScores: Boolean = false,
    val largeText: Boolean = false,
    val plainHeadings: Boolean = false,
    val showNews: Boolean = true,
    /** League keys shown on the Sports tab (sports.Leagues). */
    val watched: Set<String> = com.bit128.bitsports.sports.Leagues.defaultWatched,
    val blinkLive: Boolean = true,
    // GOAL moment: "lamp", "burst", "puck" or "text"; and the 8-bit horn.
    val goalStyle: String = "lamp",
    val goalSound: Boolean = true,
    // Which game alerts to send.
    val alertPregame: Boolean = true,
    val alertGoals: Boolean = true,
    val alertReplays: Boolean = true,
    val alertPeriods: Boolean = true,
    val alertRecap: Boolean = true,
    val alertFinal: Boolean = true,
    /** A listen-live link for your team's radio call, if you'd rather use your own. */
    val radioUrl: String = "",
)

fun UiPrefs.alertPrefs() = AlertPrefs(
    pregame = alertPregame, goals = alertGoals, replays = alertReplays,
    periods = alertPeriods, recap = alertRecap, final = alertFinal,
)

val GOAL_STYLES: List<Pair<String, String>> = listOf("Lamp" to "lamp", "Burst" to "burst", "Puck" to "puck", "Text only" to "text")

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
            showNews = prefs.getBoolean("showNews", d.showNews),
            watched = prefs.getStringSet("watched", null)?.toSet() ?: d.watched,
            blinkLive = prefs.getBoolean("blinkLive", d.blinkLive),
            goalStyle = prefs.getString("goalStyle", d.goalStyle) ?: d.goalStyle,
            goalSound = prefs.getBoolean("goalSound", d.goalSound),
            alertPregame = prefs.getBoolean("alertPregame", d.alertPregame),
            alertGoals = prefs.getBoolean("alertGoals", d.alertGoals),
            alertReplays = prefs.getBoolean("alertReplays", d.alertReplays),
            alertPeriods = prefs.getBoolean("alertPeriods", d.alertPeriods),
            alertRecap = prefs.getBoolean("alertRecap", d.alertRecap),
            alertFinal = prefs.getBoolean("alertFinal", d.alertFinal),
            radioUrl = prefs.getString("radioUrl", d.radioUrl) ?: d.radioUrl,
        )
    }

    fun save(p: UiPrefs) {
        prefs.edit()
            .putLong("accent", p.accent)
            .putBoolean("compactScores", p.compactScores)
            .putBoolean("largeText", p.largeText)
            .putBoolean("plainHeadings", p.plainHeadings)
            .putBoolean("showNews", p.showNews)
            .putStringSet("watched", p.watched)
            .putBoolean("blinkLive", p.blinkLive)
            .putString("goalStyle", p.goalStyle)
            .putBoolean("goalSound", p.goalSound)
            .putBoolean("alertPregame", p.alertPregame)
            .putBoolean("alertGoals", p.alertGoals)
            .putBoolean("alertReplays", p.alertReplays)
            .putBoolean("alertPeriods", p.alertPeriods)
            .putBoolean("alertRecap", p.alertRecap)
            .putBoolean("alertFinal", p.alertFinal)
            .putString("radioUrl", p.radioUrl)
            .apply()
    }
}
