package com.bit128.bitsports

import androidx.compose.ui.graphics.Color

// A few real team colours; anything else (any team, any sport) falls back to a
// stable two-colour crest hashed from its name, so every team has a consistent
// identity mark without needing a real logo. Mirrors index.html's KNOWN_COLORS.
private val KNOWN_COLORS: Map<String, Pair<Color, Color>> = mapOf(
    "edmonton oilers" to (Color(0xFF041E42) to Color(0xFFFC4C02)),
    "calgary flames" to (Color(0xFFC8102E) to Color(0xFFF1BE48)),
    "toronto maple leafs" to (Color(0xFF00205B) to Color(0xFFFFFFFF)),
    "boston bruins" to (Color(0xFFFFB81C) to Color(0xFF000000)),
    "winnipeg jets" to (Color(0xFF041E42) to Color(0xFF004C97)),
    "colorado avalanche" to (Color(0xFF6F263D) to Color(0xFF236192)),
    "new york rangers" to (Color(0xFF0038A8) to Color(0xFFCE1126)),
    "montreal canadiens" to (Color(0xFFAF1E2D) to Color(0xFF192168)),
    "dallas cowboys" to (Color(0xFF002244) to Color(0xFFB0B7BC)),
    "kansas city chiefs" to (Color(0xFFE31837) to Color(0xFFFFB81C)),
    "los angeles lakers" to (Color(0xFF552583) to Color(0xFFFDB927)),
    "boston celtics" to (Color(0xFF007A33) to Color(0xFFFFFFFF)),
    "new york yankees" to (Color(0xFF0C2340) to Color(0xFFFFFFFF)),
    "los angeles dodgers" to (Color(0xFF005A9C) to Color(0xFFFFFFFF)),
    "manchester united" to (Color(0xFFDA291C) to Color(0xFFFBE122)),
    "real madrid" to (Color(0xFFFFFFFF) to Color(0xFFFEBE10)),
)

fun teamColors(name: String): Pair<Color, Color> {
    val key = name.trim().lowercase()
    KNOWN_COLORS[key]?.let { return it }
    var h = 0
    for (c in key) h = (h * 31 + c.code) and 0x7fffffff
    val hue = (h % 360).toFloat()
    return hslColor(hue, 0.55f, 0.42f) to hslColor((hue + 40f) % 360f, 0.55f, 0.58f)
}

private fun hslColor(h: Float, s: Float, l: Float): Color {
    val c = (1 - kotlin.math.abs(2 * l - 1)) * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = l - c / 2
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}
