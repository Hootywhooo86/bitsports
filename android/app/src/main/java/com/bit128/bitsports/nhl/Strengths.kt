package com.bit128.bitsports.nhl

/** The on-ice strength in words: power plays, 5-on-3s, 4-on-4 and pulled goalies. */
object Strengths {
    enum class Kind { PP, EMPTY_NET, EVEN_SHORT }

    /** [team] is the side with the edge (the power play, or the one that pulled its goalie). */
    data class Banner(val kind: Kind, val team: String?, val text: String)

    fun banner(g: Game): Banner? {
        if (!g.state.isLive || g.clock?.inIntermission == true) return null
        val s = g.situation ?: return null
        val c = s.code.takeIf { it.length == 4 && it.all(Char::isDigit) } ?: return null
        val awayG = c[0].digitToInt()
        val away = c[1].digitToInt()
        val home = c[2].digitToInt()
        val homeG = c[3].digitToInt()
        val time = s.timeRemaining?.trimStart('0')?.let { if (it.startsWith(":")) "0$it" else it }
        // A pulled goalie shows up as an extra skater; that's not a power play.
        if (awayG == 0 || homeG == 0) {
            val who = if (awayG == 0) g.away.abbrev else g.home.abbrev
            return Banner(Kind.EMPTY_NET, who, "$who NET EMPTY")
        }
        if (away != home) {
            val who = if (away > home) g.away.abbrev else g.home.abbrev
            val n = "${maxOf(away, home)} ON ${minOf(away, home)}"
            return Banner(Kind.PP, who, listOfNotNull("$who PP", n.takeIf { maxOf(away, home) - minOf(away, home) > 1 }, time).joinToString(" "))
        }
        if (away < 5) return Banner(Kind.EVEN_SHORT, null, "$away ON $home")
        return null
    }
}
