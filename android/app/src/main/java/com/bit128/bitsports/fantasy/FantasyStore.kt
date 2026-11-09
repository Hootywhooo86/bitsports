package com.bit128.bitsports.fantasy

import android.content.Context
import com.bit128.bitsports.nhl.PlayerRef
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** A named list of players ("My pool", "Family pool", "Waiver watch"). */
data class FantasyList(val id: String, val name: String, val players: List<PlayerRef>)

/** Your lists and the scoring, kept on the phone. */
class FantasyStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("bitsports.fantasy", Context.MODE_PRIVATE)

    fun lists(): List<FantasyList> = decode(prefs.getString("lists", null))
    fun save(lists: List<FantasyList>) = prefs.edit().putString("lists", encode(lists)).apply()

    fun scoring(): Scoring = Scoring.fromJson(prefs.getString("scoring", null)) ?: Scoring.STANDARD
    fun setScoring(s: Scoring) = prefs.edit().putString("scoring", s.toJson()).apply()

    /** Every player on any list, once. */
    fun allPlayers(): List<PlayerRef> = lists().flatMap { it.players }.distinctBy { it.id }

    fun add(listId: String, p: PlayerRef) = save(lists().map { if (it.id == listId && it.players.none { q -> q.id == p.id }) it.copy(players = it.players + p) else it })
    fun remove(listId: String, playerId: Int) = save(lists().map { if (it.id == listId) it.copy(players = it.players.filter { q -> q.id != playerId }) else it })
    fun create(name: String): FantasyList = FantasyList(UUID.randomUUID().toString(), name.trim().ifBlank { "My list" }, emptyList()).also { save(lists() + it) }
    fun rename(listId: String, name: String) = save(lists().map { if (it.id == listId) it.copy(name = name.trim().ifBlank { it.name }) else it })
    fun delete(listId: String) = save(lists().filter { it.id != listId })

    companion object {
        fun encode(lists: List<FantasyList>): String = JSONArray().apply {
            lists.forEach { l ->
                put(JSONObject().put("id", l.id).put("name", l.name).put("players", JSONArray().apply {
                    l.players.forEach { p -> put(JSONObject().put("id", p.id).put("name", p.name).put("team", p.team).put("pos", p.pos).put("num", p.number ?: 0)) }
                }))
            }
        }.toString()

        fun decode(s: String?): List<FantasyList> = runCatching {
            val a = JSONArray(s ?: return emptyList())
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val ps = o.getJSONArray("players")
                FantasyList(o.getString("id"), o.getString("name"), (0 until ps.length()).map { k ->
                    val p = ps.getJSONObject(k)
                    PlayerRef(p.getInt("id"), p.getString("name"), p.getString("team"), p.getString("pos"), p.optInt("num").takeIf { it > 0 })
                })
            }
        }.getOrDefault(emptyList())
    }
}
