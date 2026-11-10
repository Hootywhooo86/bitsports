package com.bit128.bitsports.nhl

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Each team's lines as last seen, and the game-day before, so tonight's can be marked NEW where
 * a line wasn't together last time.
 */
class LinesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bitsports.lines", Context.MODE_PRIVATE)

    /** Saves [lines] as today's and returns the units that are new since the previous day's. */
    fun update(lines: StartingLines, today: LocalDate = LocalDate.now()): Set<String> {
        val t = lines.team
        val day = prefs.getString("day.$t", null)
        val e = prefs.edit()
        if (day != null && day != today.toString()) prefs.getString("cur.$t", null)?.let { e.putString("prev.$t", it) }
        e.putString("cur.$t", encode(lines.units())).putString("day.$t", today.toString()).apply()
        val prev = decode(prefs.getString("prev.$t", null))
        if (prev.isEmpty()) return emptySet()
        val old = prev.values.toSet()
        return lines.units().filter { (_, names) -> names !in old }.keys
    }

    private fun encode(m: Map<String, Set<String>>) = JSONObject().apply { m.forEach { (k, v) -> put(k, JSONArray(v.toList())) } }.toString()
    private fun decode(s: String?): Map<String, Set<String>> = runCatching {
        val o = JSONObject(s ?: return emptyMap())
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } }
    }.getOrDefault(emptyMap())
}
