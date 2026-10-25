package com.bit128.bitsports.ui

import android.content.Context
import android.content.SharedPreferences

/**
 * The colour a team lights up in on the LED boards, rinks, heat maps and widgets. A team's real
 * colours are often navy, black or white, which vanish on a black board or wash out against
 * white goal sparks (the Leafs, Lightning and Red Wings all came out white). Each team gets a
 * main and a spare LED colour that read on black, the two teams in a game never share one, and
 * you can pick your own for any team in Settings → Look → Team colours.
 */
object TeamInk {
    /** Main and spare LED colour per team: bright enough for a black board, never pure white. */
    val DEFAULTS: Map<String, Pair<Int, Int>> = mapOf(
        "ANA" to (0xFFF47A38 to 0xFFB9975B),
        "BOS" to (0xFFFFB81C to 0xFFB0B7BA),
        "BUF" to (0xFF3A72E8 to 0xFFFFB81C),
        "CGY" to (0xFFE8283C to 0xFFF1BE48),
        "CAR" to (0xFFE8202A to 0xFFB0B7BA),
        "CHI" to (0xFFE8283C to 0xFFF0A020),
        "COL" to (0xFF4A90D9 to 0xFFB0405E),
        "CBJ" to (0xFF3A6FD8 to 0xFFE8283C),
        "DAL" to (0xFF00A86B to 0xFFB0B7BA),
        "DET" to (0xFFE8283C to 0xFFB0B7BA),
        "EDM" to (0xFFFC4C02 to 0xFF3A6FD8),
        "FLA" to (0xFFE8283C to 0xFFC8A23C),
        "LAK" to (0xFFB0B7BA to 0xFF8A6CD8),
        "MIN" to (0xFF2E9E5E to 0xFFE8283C),
        "MTL" to (0xFFD8283C to 0xFF4A5CE0),
        "NSH" to (0xFFFFB81C to 0xFF3A6FD8),
        "NJD" to (0xFFE8283C to 0xFFB0B7BA),
        "NYI" to (0xFF2F7FD8 to 0xFFF47D30),
        "NYR" to (0xFF3A6FE8 to 0xFFE8283C),
        "OTT" to (0xFFE8283C to 0xFFC2912C),
        "PHI" to (0xFFF74902 to 0xFFB0B7BA),
        "PIT" to (0xFFFCB514 to 0xFFB0B7BA),
        "SJS" to (0xFF00A3AE to 0xFFEA7200),
        "SEA" to (0xFF99D9D9 to 0xFFE8283C),
        "STL" to (0xFF3A6FE8 to 0xFFFCB514),
        "TBL" to (0xFF3A7BF0 to 0xFFB0B7BA),
        "TOR" to (0xFF3A6FE8 to 0xFFB0B7BA),
        "UTA" to (0xFF71AFE5 to 0xFFB0B7BA),
        "VAN" to (0xFF3A6FD8 to 0xFF00A85A),
        "VGK" to (0xFFC8A86A to 0xFFE8283C),
        "WSH" to (0xFFE8283C to 0xFF3A6FD8),
        "WPG" to (0xFF3A86E0 to 0xFFE8283C),
    ).mapValues { (_, v) -> v.first.toInt() to v.second.toInt() }

    /** What you can pick from in Settings, besides each team's own two. */
    val PALETTE: List<Int> = listOf(
        0xFFE8283C, 0xFFFC4C02, 0xFFFF8A2A, 0xFFFFB81C, 0xFFFFE066, 0xFF7ED957,
        0xFF00A86B, 0xFF00C2C7, 0xFF4AB8FF, 0xFF3A6FE8, 0xFF8A6CD8, 0xFFE05CC0,
        0xFFB0B7BA, 0xFFB0B7BA,
    ).map { it.toInt() }

    private const val FALLBACK = 0xFF8C8C8C.toInt()
    private const val PREFS = "bitsports.teamink"
    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var picked: Map<String, Int> = emptyMap()

    /** Called once per process (BitsportsApp), so widgets and alerts see your picks too. */
    fun init(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        picked = p.all.mapNotNull { (k, v) -> (v as? Int)?.let { k to it } }.toMap()
    }

    fun picked(abbrev: String): Int? = picked[abbrev.uppercase()]

    /** [color] null goes back to the team's own. */
    fun pick(abbrev: String, color: Int?) {
        val a = abbrev.uppercase()
        picked = if (color == null) picked - a else picked + (a to color)
        prefs?.edit()?.apply { if (color == null) remove(a) else putInt(a, color) }?.apply()
    }

    /** Only for tests: forget picks without a Context. */
    internal fun reset(with: Map<String, Int> = emptyMap()) { picked = with }

    fun of(abbrev: String): Int = picked(abbrev) ?: DEFAULTS[abbrev.uppercase()]?.first ?: FALLBACK

    /**
     * A game's two colours. When they'd look alike (Leafs vs Lightning, both blue), the home team
     * switches to its spare. Your own picks always win, for either side.
     */
    fun pair(away: String, home: String): Pair<Int, Int> {
        val a = of(away)
        val h = of(home)
        if (!alike(a, h)) return a to h
        // Try the home spare first, then the away spare, then both; a colour you picked never changes.
        val awayOptions = listOfNotNull(a, DEFAULTS[away.uppercase()]?.second.takeIf { picked(away) == null })
        val homeOptions = listOfNotNull(h, DEFAULTS[home.uppercase()]?.second.takeIf { picked(home) == null })
        val tries = listOf(0 to 1, 1 to 0, 1 to 1)
        for ((i, j) in tries) {
            val x = awayOptions.getOrNull(i) ?: continue
            val y = homeOptions.getOrNull(j) ?: continue
            if (!alike(x, y)) return x to y
        }
        return a to h
    }

    /** Close enough that two teams would be hard to tell apart on the board. */
    fun alike(x: Int, y: Int): Boolean {
        fun ch(v: Int, s: Int) = (v shr s and 0xFF).toDouble()
        val dr = ch(x, 16) - ch(y, 16)
        val dg = ch(x, 8) - ch(y, 8)
        val db = ch(x, 0) - ch(y, 0)
        val rm = (ch(x, 16) + ch(y, 16)) / 2
        // "Redmean" distance: a cheap fit to how far apart colours look.
        val d = Math.sqrt((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db)
        return d < 150
    }
}
