package com.bit128.bitsports.nhl

import android.content.Context
import org.json.JSONArray

/**
 * How the odds moved through a game, for the WIN-O-METER's momentum chart. Each sample is the
 * away team's chance to win (as the moneyline prices it) and the score at that moment, so a goal
 * shows where the line jumped. Recorded by the app and by the widgets (which keep polling with the
 * app closed), kept for the last couple of games.
 */
data class OddsSample(val atMs: Long, val awayShare: Float, val awayScore: Int, val homeScore: Int)

object OddsHistory {
    /** Closer than this and a new sample replaces the last one (several things poll at once). */
    private const val MIN_GAP_MS = 15_000L
    const val MAX = 400
    private const val KEEP_GAMES = 3

    /** The away team's share of the win chance, the book's margin taken out. */
    fun share(o: GameOdds): Float? {
        val a = o.away.moneyLine?.let(NhlOddsParser::implied) ?: return null
        val h = o.home.moneyLine?.let(NhlOddsParser::implied) ?: return null
        return a / (a + h)
    }

    /** Pure: [list] with [s] added. */
    fun append(list: List<OddsSample>, s: OddsSample): List<OddsSample> {
        val last = list.lastOrNull()
        val base = if (last != null && s.atMs - last.atMs < MIN_GAP_MS) list.dropLast(1) else list
        return (base + s).takeLast(MAX)
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("bitsports.odds", Context.MODE_PRIVATE)

    @Synchronized
    fun record(context: Context, g: Game, o: GameOdds, now: Long = System.currentTimeMillis()): List<OddsSample> {
        val sh = share(o) ?: return get(context, g.id)
        val list = append(get(context, g.id), OddsSample(now, sh, g.away.score ?: 0, g.home.score ?: 0))
        val p = prefs(context)
        val e = p.edit().putString(g.id.toString(), encode(list))
        // Old games go; only the last few are kept.
        p.all.keys.mapNotNull { it.toLongOrNull() }.filter { it != g.id }.sortedDescending().drop(KEEP_GAMES - 1).forEach { e.remove(it.toString()) }
        e.apply()
        return list
    }

    fun get(context: Context, gameId: Long): List<OddsSample> = decode(prefs(context).getString(gameId.toString(), null))

    fun encode(list: List<OddsSample>): String = JSONArray().apply {
        list.forEach { put(JSONArray().put(it.atMs).put((it.awayShare * 1000).toInt()).put(it.awayScore).put(it.homeScore)) }
    }.toString()

    fun decode(s: String?): List<OddsSample> = runCatching {
        val a = JSONArray(s ?: return emptyList())
        List(a.length()) { i -> a.getJSONArray(i).let { OddsSample(it.getLong(0), it.getInt(1) / 1000f, it.getInt(2), it.getInt(3)) } }
    }.getOrDefault(emptyList())
}
