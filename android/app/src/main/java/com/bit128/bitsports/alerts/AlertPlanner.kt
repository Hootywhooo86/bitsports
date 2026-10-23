package com.bit128.bitsports.alerts

import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.GameDetail
import com.bit128.bitsports.nhl.GoalEvent
import com.bit128.bitsports.nhl.PeriodStats
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

sealed interface Alert {
    data class Pregame(val game: Game) : Alert
    data class Goal(val goal: GoalEvent) : Alert
    data class Replay(val goal: GoalEvent) : Alert
    data class PeriodEnd(val stats: PeriodStats, val label: String) : Alert
    data class Final(val game: Game, val shootout: Boolean, val threeStars: List<String>) : Alert
    /** Watching started mid-game: one "here's where it stands" instead of a burst of old goals. */
    data class CatchUp(val game: Game, val goals: List<GoalEvent>) : Alert
}

data class AlertPrefs(
    val pregame: Boolean,
    val goals: Boolean,
    val replays: Boolean,
    val periods: Boolean,
    val recap: Boolean,
    val final: Boolean,
)

/** What has already been announced for one game. Persisted so nothing alerts twice, even after a restart. */
data class WatchState(
    val gameId: Long,
    val alertedGoals: Set<Int>,
    val replayed: Set<Int>,
    val endedPeriods: Set<Int>,
    val finalSent: Boolean,
    val pregameSent: Boolean,
    /** The game has been checked at least once; until then, goals already on the board are a catch-up. */
    val seen: Boolean = false,
) {
    fun toJson(): String = JSONObject()
        .put("gameId", gameId)
        .put("alertedGoals", JSONArray(alertedGoals.sorted()))
        .put("replayed", JSONArray(replayed.sorted()))
        .put("endedPeriods", JSONArray(endedPeriods.sorted()))
        .put("finalSent", finalSent)
        .put("pregameSent", pregameSent)
        .put("seen", seen)
        .toString()

    companion object {
        fun fresh(gameId: Long) = WatchState(gameId, emptySet(), emptySet(), emptySet(), finalSent = false, pregameSent = false)

        fun fromJson(s: String): WatchState {
            val o = JSONObject(s)
            fun ints(k: String) = o.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() }.orEmpty()
            return WatchState(
                gameId = o.getLong("gameId"),
                alertedGoals = ints("alertedGoals"),
                replayed = ints("replayed"),
                endedPeriods = ints("endedPeriods"),
                finalSent = o.optBoolean("finalSent"),
                pregameSent = o.optBoolean("pregameSent"),
                // States saved before this field existed had been watched if anything was recorded.
                seen = o.optBoolean("seen", ints("alertedGoals").isNotEmpty() || ints("endedPeriods").isNotEmpty()),
            )
        }
    }
}

/**
 * Decides what to announce from one poll of a game. Every event is recorded in the state
 * whether or not its alert is switched on, so turning an alert on mid-game never replays old news.
 */
object AlertPlanner {
    private val PREGAME_LEAD: Duration = Duration.ofMinutes(30)
    private val GIVE_UP_AFTER: Duration = Duration.ofMinutes(90)

    fun step(prev: WatchState, d: GameDetail, prefs: AlertPrefs): Pair<List<Alert>, WatchState> {
        val alerts = mutableListOf<Alert>()
        val goals = GameAnalysis.goals(d)
        val alerted = prev.alertedGoals.toMutableSet()
        val replayed = prev.replayed.toMutableSet()
        // First look at a game already under way with goals on the board: catch up in one alert.
        val firstSight = !prev.seen
        val catchUp = firstSight && goals.size >= 2
        if (catchUp && prefs.goals && !d.game.state.isOver) alerts += Alert.CatchUp(d.game, goals)
        for (g in goals) {
            if (g.eventId !in alerted) {
                alerted += g.eventId
                if (prefs.goals && !catchUp) alerts += Alert.Goal(g)
                // A clip that's already there rides on the goal alert; no second alert for it.
                if (g.clipUrl != null) replayed += g.eventId
            } else if (g.clipUrl != null && g.eventId !in replayed) {
                replayed += g.eventId
                if (prefs.replays) alerts += Alert.Replay(g)
            }
        }

        val ended = prev.endedPeriods.toMutableSet()
        d.plays.filter { it.type == "period-end" && it.periodType != "SO" && it.period !in ended }
            .map { it.period to it.periodType }
            .distinct()
            .forEach { (period, type) ->
                ended += period
                if (prefs.periods && !catchUp) alerts += Alert.PeriodEnd(GameAnalysis.periodStats(d, period), periodLabel(period, type))
            }

        var finalSent = prev.finalSent
        if (d.game.state.isOver && !finalSent) {
            finalSent = true
            val shootout = d.plays.any { it.periodType == "SO" } || d.game.clock?.periodType == "SO"
            if (prefs.final) alerts += Alert.Final(d.game, shootout, d.threeStars)
        }

        return alerts to prev.copy(alertedGoals = alerted, replayed = replayed, endedPeriods = ended, finalSent = finalSent, seen = true)
    }

    fun pregame(prev: WatchState, game: Game, now: Instant, prefs: AlertPrefs): Pair<Alert.Pregame?, WatchState> {
        val due = !now.isBefore(game.startUtc.minus(PREGAME_LEAD)) && now.isBefore(game.startUtc)
        if (prev.pregameSent || !due || !game.scheduleOk || game.state.isLive || game.state.isOver) return null to prev
        return (if (prefs.pregame) Alert.Pregame(game) else null) to prev.copy(pregameSent = true)
    }

    fun shouldStopWatching(game: Game, now: Instant): Boolean = when {
        game.state.isOver -> true
        game.state.isLive -> false
        else -> now.isAfter(game.startUtc.plus(GIVE_UP_AFTER))
    }

    fun staleLabel(fetchedAt: Instant, now: Instant): String? {
        val secs = Duration.between(fetchedAt, now).seconds
        if (secs < 45) return null
        if (secs < 60) return "updated ${secs}s ago"
        return "updated ${secs / 60}m ${"%02d".format(secs % 60)}s ago"
    }

    fun periodLabel(period: Int, periodType: String): String = when {
        periodType == "SO" -> "SO"
        periodType == "OT" || period > 3 -> if (period <= 4) "OT" else "${period - 3}OT"
        period == 1 -> "1st"
        period == 2 -> "2nd"
        period == 3 -> "3rd"
        else -> "${period}th"
    }
}

