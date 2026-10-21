package com.bit128.bitsports.alerts

import android.content.Context
import com.bit128.bitsports.nhl.GoalEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.time.LocalDate

/** On-device memory for game watching: what's been announced, recaps, games skipped tonight. */
class WatchStore(context: Context) {
    private val prefs = context.getSharedPreferences("bitsports.watch", Context.MODE_PRIVATE)

    fun state(gameId: Long): WatchState =
        prefs.getString("watch.$gameId", null)?.let { runCatching { WatchState.fromJson(it) }.getOrNull() }
            ?: WatchState.fresh(gameId)

    fun save(s: WatchState) = prefs.edit().putString("watch.${s.gameId}", s.toJson()).apply()

    fun recap(gameId: Long, period: Int): String? = prefs.getString("recap.$gameId.$period", null)
    fun saveRecap(gameId: Long, period: Int, text: String) = prefs.edit().putString("recap.$gameId.$period", text).apply()

    /** Periods a recap was already asked for, so a slow model isn't asked twice. */
    fun recappedPeriods(gameId: Long): Set<Int> =
        prefs.getStringSet("recapped.$gameId", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet()
    fun markRecapped(gameId: Long, period: Int) =
        prefs.edit().putStringSet("recapped.$gameId", (recappedPeriods(gameId) + period).map { it.toString() }.toSet()).apply()

    /** "Stop for tonight" on the mini scoreboard: this game only, today only. */
    fun skipTonight(gameId: Long) = prefs.edit().putString("skip.$gameId", LocalDate.now().toString()).apply()
    fun skippedTonight(gameId: Long) = prefs.getString("skip.$gameId", null) == LocalDate.now().toString()
}

/** Goals the watcher just announced, so an open app can play the GOAL moment. */
object LiveHub {
    private val _goals = MutableSharedFlow<GoalEvent>(extraBufferCapacity = 8)
    val goals: SharedFlow<GoalEvent> = _goals
    fun emit(goal: GoalEvent) { _goals.tryEmit(goal) }
}
