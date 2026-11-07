package com.bit128.bitsports.nhl

import org.json.JSONObject

/** Bits of a player's NHL page (player/{id}/landing). */
object NhlPlayer {
    /**
     * Career regular-season goals with this season counted as [seasonGoals] (the live total from
     * the play-by-play). The page's own totals can lag a game, so this season is swapped out.
     */
    fun careerGoals(json: String, seasonGoals: Int): Int? {
        val o = JSONObject(json)
        val career = o.optJSONObject("careerTotals")?.optJSONObject("regularSeason")?.optInt("goals", -1)?.takeIf { it >= 0 } ?: return null
        val seasons = o.optJSONArray("seasonTotals") ?: return career
        var latest = 0; var latestGoals = 0
        for (i in 0 until seasons.length()) {
            val s = seasons.getJSONObject(i)
            if (s.optString("leagueAbbrev") != "NHL" || s.optInt("gameTypeId") != 2) continue
            val season = s.optInt("season")
            if (season > latest) { latest = season; latestGoals = 0 }
            if (season == latest) latestGoals += s.optInt("goals")
        }
        return career - latestGoals + seasonGoals
    }
}
