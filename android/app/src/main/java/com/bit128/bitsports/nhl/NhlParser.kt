package com.bit128.bitsports.nhl

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object NhlParser {
    fun scoreboard(json: String): List<Game> {
        val games = JSONObject(json).optJSONArray("games") ?: JSONArray()
        return (0 until games.length()).map { game(games.getJSONObject(it)) }
    }

    fun playByPlay(json: String, fetchedAt: Instant): GameDetail {
        val o = JSONObject(json)
        val roster = HashMap<Int, String>()
        o.optJSONArray("rosterSpots")?.let { spots ->
            for (i in 0 until spots.length()) {
                val s = spots.getJSONObject(i)
                roster[s.getInt("playerId")] = "${name(s, "firstName")} ${name(s, "lastName")}".trim()
            }
        }
        val raw = o.optJSONArray("plays") ?: JSONArray()
        val plays = (0 until raw.length()).map { play(raw.getJSONObject(it)) }
        val stars = o.optJSONObject("summary")?.optJSONArray("threeStars")?.let { a ->
            (0 until a.length()).mapNotNull { i ->
                val s = a.getJSONObject(i)
                s.optJSONObject("name")?.optString("default")?.takeIf { it.isNotBlank() }
                    ?: roster[s.optInt("playerId", -1)]
            }
        }.orEmpty()
        return GameDetail(
            game = game(o),
            plays = plays,
            roster = roster,
            threeStars = stars,
            fetchedAt = fetchedAt,
            homeDefendingSide = raw.lastDefendingSide(),
        )
    }

    private fun JSONArray.lastDefendingSide(): String? {
        for (i in length() - 1 downTo 0) {
            optJSONObject(i)?.optString("homeTeamDefendingSide")?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return null
    }

    private fun name(o: JSONObject, key: String) = o.optJSONObject(key)?.optString("default").orEmpty()

    private fun game(o: JSONObject): Game {
        val pd = o.optJSONObject("periodDescriptor")
        val clock = o.optJSONObject("clock")?.let { c ->
            Clock(
                period = pd?.optInt("number", 0) ?: o.optInt("period", 0),
                periodType = pd?.optString("periodType", "REG") ?: "REG",
                timeRemaining = c.optString("timeRemaining", ""),
                inIntermission = c.optBoolean("inIntermission", false),
                running = c.optBoolean("running", false),
            )
        }
        return Game(
            id = o.getLong("id"),
            state = runCatching { GameState.valueOf(o.optString("gameState")) }.getOrDefault(GameState.FUT),
            scheduleOk = o.optString("gameScheduleState", "OK") == "OK",
            startUtc = runCatching { Instant.parse(o.optString("startTimeUTC")) }.getOrDefault(Instant.EPOCH),
            away = team(o.getJSONObject("awayTeam")),
            home = team(o.getJSONObject("homeTeam")),
            clock = clock,
        )
    }

    private fun team(t: JSONObject) = TeamLine(
        id = t.optInt("id"),
        abbrev = t.optString("abbrev"),
        score = if (t.has("score")) t.optInt("score") else null,
        sog = if (t.has("sog")) t.optInt("sog") else null,
    )

    private fun play(p: JSONObject): Play {
        val d = p.optJSONObject("details") ?: JSONObject()
        val pd = p.optJSONObject("periodDescriptor")
        fun int(k: String) = if (d.has(k)) d.optInt(k) else null
        return Play(
            eventId = p.optInt("eventId"),
            type = p.optString("typeDescKey"),
            period = pd?.optInt("number", 0) ?: 0,
            periodType = pd?.optString("periodType", "REG") ?: "REG",
            timeInPeriod = p.optString("timeInPeriod"),
            teamId = int("eventOwnerTeamId"),
            x = int("xCoord"),
            y = int("yCoord"),
            shotType = d.optString("shotType").ifBlank { null },
            situationCode = p.optString("situationCode").ifBlank { null },
            details = d,
        )
    }
}
