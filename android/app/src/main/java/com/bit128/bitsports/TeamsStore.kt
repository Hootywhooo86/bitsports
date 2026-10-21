package com.bit128.bitsports

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// Local-first, same as the web prototype: followed teams live only on this
// device, nothing is synced.
class TeamsStore(context: Context) {
    private val prefs = context.getSharedPreferences("bitsports", Context.MODE_PRIVATE)

    fun load(): List<FollowedTeam> {
        val raw = prefs.getString("teams", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FollowedTeam(o.getString("team"), o.getString("sport"))
        }
    }

    fun save(teams: List<FollowedTeam>) {
        val arr = JSONArray()
        teams.forEach {
            arr.put(JSONObject().put("team", it.team).put("sport", it.sport))
        }
        prefs.edit().putString("teams", arr.toString()).apply()
    }
}
