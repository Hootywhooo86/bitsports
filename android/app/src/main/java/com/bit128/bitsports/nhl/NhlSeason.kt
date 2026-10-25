package com.bit128.bitsports.nhl

import org.json.JSONArray
import org.json.JSONObject

/** One game from a team's season schedule (club-schedule-season), with what a calendar entry needs. */
data class ScheduleGame(
    val game: Game,
    /** 1 preseason, 2 regular season, 3 playoffs. */
    val gameType: Int,
    val venue: String?,
    val tv: List<String>,
    /** "REG", "OT" or "SO" once it's over. */
    val lastPeriodType: String?,
)

object NhlSeasonParser {
    fun season(json: String): List<ScheduleGame> {
        val arr = JSONObject(json).optJSONArray("games") ?: JSONArray()
        val games = NhlParser.scoreboard(JSONObject().put("games", arr).toString())
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ScheduleGame(
                game = games[i],
                gameType = o.optInt("gameType", 2),
                venue = o.optJSONObject("venue")?.optString("default")?.ifBlank { null },
                tv = o.optJSONArray("tvBroadcasts")?.let { t -> (0 until t.length()).mapNotNull { t.optJSONObject(it)?.optString("network")?.ifBlank { null } } }
                    .orEmpty().distinct(),
                lastPeriodType = o.optJSONObject("gameOutcome")?.optString("lastPeriodType")?.ifBlank { null },
            )
        }
    }
}
