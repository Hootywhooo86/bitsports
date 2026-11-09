package com.bit128.bitsports.nhl

import org.json.JSONObject

/** ESPN's league-wide injury list: who's out and why. */
data class Injury(val name: String, val team: String, val status: String, val comment: String?)

object Injuries {
    fun parse(json: String): List<Injury> {
        val teams = JSONObject(json).optJSONArray("injuries") ?: return emptyList()
        val out = ArrayList<Injury>()
        for (i in 0 until teams.length()) {
            val list = teams.getJSONObject(i).optJSONArray("injuries") ?: continue
            for (k in 0 until list.length()) {
                val o = list.getJSONObject(k)
                val a = o.optJSONObject("athlete") ?: continue
                val team = a.optJSONObject("team")?.optString("abbreviation").orEmpty()
                out += Injury(a.optString("displayName"), EspnOdds.nhlAbbrev(team), o.optString("status"), o.optString("shortComment").ifBlank { null })
            }
        }
        return out
    }

    private fun norm(s: String) = java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replace(Regex("[^A-Za-z]"), "").lowercase()

    /** The injury for [p], matched on name and team. */
    fun find(all: List<Injury>, p: PlayerRef): Injury? = all.firstOrNull { it.team == p.team && norm(it.name) == norm(p.name) }

    /** A scratch for [p] in a game's scratch list (full names). */
    fun scratched(scratches: List<String>, p: PlayerRef): Boolean = scratches.any { norm(it) == norm(p.name) }
}
