package com.bit128.bitsports.calendar

import android.content.Context
import com.bit128.bitsports.PrefsStore
import com.bit128.bitsports.account.Account128
import com.bit128.bitsports.alerts.AlertLog
import com.bit128.bitsports.alerts.AlertScheduler
import com.bit128.bitsports.nhl.DirectNhlSource
import com.bit128.bitsports.nhl.ScheduleGame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant

/**
 * Your teams' games in Bitcal. bitsports keeps one calendar, "bitsports · NHL", in your 128bit
 * account and mirrors every game into it through Bitcal's own bitcal_mirror (the way Google is
 * copied in): one row per game, keyed by NHL game id, updated in place when a time, status or
 * score changes, and left alone once you edit it in Bitcal. Games of teams you stop following are
 * cancelled. Runs when you sign in, when your teams change, each morning, on opening the app and
 * after a game.
 */
object BitcalSync {
    private const val THROTTLE_MS = 6 * 3600_000L
    private const val CALENDAR_NAME = "bitsports · NHL"
    private const val CALENDAR_COLOR = "#fc4c02"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences("bitsports.bitcal", Context.MODE_PRIVATE)

    fun enabled(context: Context) = Account128(context).signedIn && prefs(context).getBoolean("on", true)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("on", on).apply()
    fun lastSync(context: Context) = prefs(context).getLong("last", 0L).takeIf { it > 0 }
    fun count(context: Context) = prefs(context).getStringSet("games", emptySet())!!.size
    fun lastError(context: Context) = prefs(context).getString("error", null)

    /** Throttled to every few hours unless [force]d; quietly does nothing when signed out or off. */
    fun sync(context: Context, force: Boolean = false, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try {
                lock.withLock {
                    if (!enabled(app)) return@withLock
                    if (!force && System.currentTimeMillis() - (lastSync(app) ?: 0) < THROTTLE_MS) return@withLock
                    try {
                        syncNow(app)
                        prefs(app).edit().remove("error").apply()
                    } catch (e: Exception) {
                        prefs(app).edit().putString("error", e.message ?: "Couldn't reach Bitcal").apply()
                        AlertLog(app).failed("Bitcal update failed", e)
                    }
                }
            } finally {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    /** Signing out can take the games with it; otherwise they stay in Bitcal as they are. */
    fun removeAll(context: Context, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        scope.launch {
            try {
                lock.withLock {
                    val token = Account128(app).token()
                    val cal = findCalendar(token) ?: return@withLock
                    val gone = prefs(app).getStringSet("games", emptySet())!!.mapNotNull { it.toLongOrNull() }
                    mirror(token, cal, BitcalRows.cancelled(gone))
                    prefs(app).edit().putStringSet("games", emptySet()).apply()
                }
            } catch (e: Exception) {
                AlertLog(app).failed("Couldn't remove games from Bitcal", e)
            } finally {
                withContext(Dispatchers.Main) { onDone() }
            }
        }
    }

    private suspend fun syncNow(context: Context) {
        val account = Account128(context)
        val token = account.token()
        val source = DirectNhlSource()
        val seasons = LinkedHashMap<String, List<ScheduleGame>>()
        for (abbrev in AlertScheduler.followedAbbrevs(context)) seasons[abbrev] = source.season(abbrev)
        val entries = GameCalendar.plan(seasons, PrefsStore(context).load().radioUrl.ifBlank { null })
        val cal = findCalendar(token) ?: createCalendar(token)
        val before = prefs(context).getStringSet("games", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        val now = entries.map { it.gameId }.toSet()
        revive(token, cal, now - before)
        val rows = BitcalRows.rows(entries) + BitcalRows.cancelled(before - now)
        rows.chunked(100).forEach { mirror(token, cal, it) }
        prefs(context).edit()
            .putStringSet("games", now.map { it.toString() }.toSet())
            .putLong("last", System.currentTimeMillis())
            .apply()
    }

    private suspend fun findCalendar(token: String): String? {
        val q = "select=id&kind=eq.events&ext->>provider=eq.bitsports&ext->>id=eq.nhl"
        val body = Account128.request("GET", "${Account128.URL_BASE}/rest/v1/bitcal_calendars?" + q.split('&').joinToString("&") { p ->
            p.substringBefore('=') + "=" + URLEncoder.encode(p.substringAfter('='), "UTF-8")
        }, null, token)
        return JSONArray(body).optJSONObject(0)?.optString("id")?.ifBlank { null }
    }

    private suspend fun createCalendar(token: String): String {
        val cal = JSONObject()
            .put("name", CALENDAR_NAME).put("color", CALENDAR_COLOR).put("kind", "events")
            .put("ext", JSONObject().put("provider", "bitsports").put("email", "").put("id", "nhl"))
        val body = Account128.request("POST", "${Account128.URL_BASE}/rest/v1/bitcal_calendars", cal.toString(), token,
            mapOf("Prefer" to "return=representation"))
        return JSONArray(body).getJSONObject(0).getString("id")
    }

    /**
     * Following a team again: its games were cancelled (soft-deleted) when you stopped, and the
     * mirror never touches deleted rows, so bring back the ones you didn't edit first.
     */
    private suspend fun revive(token: String, cal: String, gameIds: Set<Long>) {
        gameIds.chunked(100).forEach { ids ->
            val list = ids.joinToString(",", "(", ")") { "\"${BitcalRows.extId(it)}\"" }
            val url = "${Account128.URL_BASE}/rest/v1/bitcal_events?calendar_id=eq.$cal&edited=is.false&deleted_at=not.is.null&ext_id=in." +
                URLEncoder.encode(list, "UTF-8")
            Account128.request("PATCH", url, JSONObject().put("deleted_at", JSONObject.NULL).toString(), token)
        }
    }

    private suspend fun mirror(token: String, cal: String, rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        val body = JSONObject().put("cal", cal).put("rows", JSONArray(rows))
        Account128.request("POST", "${Account128.URL_BASE}/rest/v1/rpc/bitcal_mirror", body.toString(), token)
    }
}

/** Games as rows for bitcal_mirror. Pure, so the shape Bitcal receives is tested. */
object BitcalRows {
    fun extId(gameId: Long) = "nhl:$gameId"

    fun rows(entries: List<GameCalendar.Entry>): List<JSONObject> = entries.map { e ->
        JSONObject()
            .put("ext_id", extId(e.gameId))
            .put("title", e.title)
            .put("starts_at", Instant.ofEpochMilli(e.startMs).toString())
            .put("ends_at", Instant.ofEpochMilli(e.endMs).toString())
            .put("all_day", false)
            .put("tz", "UTC")
            .put("location", e.location.orEmpty())
            .put("notes", e.description)
            .put("ext", JSONObject().put("provider", "bitsports").put("gameId", e.gameId))
    }

    fun cancelled(gameIds: Collection<Long>): List<JSONObject> =
        gameIds.map { JSONObject().put("ext_id", extId(it)).put("cancelled", true) }
}
