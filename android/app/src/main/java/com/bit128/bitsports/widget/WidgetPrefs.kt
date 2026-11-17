package com.bit128.bitsports.widget

import android.content.Context
import com.bit128.bitsports.alerts.AlertScheduler

/** Each widget's own choices, set in WidgetSetupActivity. Saved on this device only. */
data class WidgetConfig(
    val team: String?,          // NHL abbrev; null = your first followed NHL team
    val heat: String = "both",  // rink: "both" teams, or "mine" only
    val board: String = "smooth",  // rink: "smooth" fine pixels, or "dots" (round LEDs with gaps)
)

class WidgetPrefs(private val context: Context) {
    private val prefs = context.getSharedPreferences("bitsports.widgets", Context.MODE_PRIVATE)

    fun load(id: Int) = WidgetConfig(
        team = prefs.getString("$id.team", null),
        heat = prefs.getString("$id.heat", "both") ?: "both",
        board = if (prefs.getString("$id.board", null) == "dots") "dots" else "smooth",
    )

    fun save(id: Int, c: WidgetConfig) {
        prefs.edit().putString("$id.team", c.team).putString("$id.heat", c.heat).putString("$id.board", c.board).apply()
    }

    fun delete(id: Int) {
        prefs.edit().remove("$id.team").remove("$id.heat").remove("$id.board").apply()
    }

    /** The team this widget shows: its own pick, else the first NHL team you follow. */
    fun teamFor(id: Int): String? = load(id).team ?: AlertScheduler.followedAbbrevs(context).firstOrNull()
}
