package com.bit128.bitsports.alerts

import android.content.Context
import java.time.Instant

/**
 * What the alert machinery last did, on this device, so Settings → Alert check can say exactly
 * where alerts stop instead of leaving you guessing: when the game was last checked, the last alert
 * that went out, the last thing that went wrong, and when watching is scheduled to start.
 */
class AlertLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bitsports.alertlog", Context.MODE_PRIVATE)

    data class Entry(val text: String, val at: Instant)

    private fun put(key: String, text: String) =
        prefs.edit().putString("$key.text", text.take(200)).putLong("$key.at", System.currentTimeMillis()).apply()

    private fun get(key: String): Entry? = prefs.getString("$key.text", null)?.let { Entry(it, Instant.ofEpochMilli(prefs.getLong("$key.at", 0))) }

    fun checked(game: String) = put("check", game)
    fun sent(title: String) = put("sent", title)
    fun failed(what: String, e: Throwable?) = put("error", what + (e?.let { ": ${it::class.simpleName} ${it.message.orEmpty()}" } ?: ""))
    fun scheduled(text: String) = put("scheduled", text)

    val lastCheck get() = get("check")
    val lastSent get() = get("sent")
    val lastError get() = get("error")
    val schedule get() = get("scheduled")
}
