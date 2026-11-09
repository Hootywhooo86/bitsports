package com.bit128.bitsports.fantasy

import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.PlayerRef
import com.bit128.bitsports.nhl.Shift
import com.bit128.bitsports.nhl.StatLine

/** A night of fantasy hockey: each player's line and points, each list's total. Pure. */
object FantasyNight {
    data class Row(val player: PlayerRef, val game: Game?, val line: StatLine?, val points: Double) {
        val playing get() = game != null
    }

    data class ListNight(val list: FantasyList, val rows: List<Row>) {
        val total get() = rows.sumOf { it.points }
        /** The night's best, if anyone scored. */
        val mvp get() = rows.filter { it.line != null && it.points > 0 }.maxByOrNull { it.points }
    }

    fun gameFor(p: PlayerRef, games: List<Game>): Game? = games.firstOrNull { it.away.abbrev == p.team || it.home.abbrev == p.team }

    fun night(list: FantasyList, games: List<Game>, lines: Map<Int, StatLine>, scoring: Scoring): ListNight =
        ListNight(list, list.players.map { p ->
            val g = gameFor(p, games)
            val l = lines[p.id]?.takeIf { g != null && it.gameId == g.id }
            Row(p, g, l, l?.let { scoring.points(it, p.isGoalie) } ?: 0.0)
        }.sortedWith(compareByDescending<Row> { it.points }.thenBy { it.game == null }))

    /** "1G 2A · 4 SOG · 3 HIT" or "W · 31 SV · 1 GA". */
    fun statText(l: StatLine?, goalie: Boolean): String {
        if (l == null) return ""
        return if (goalie) listOfNotNull(l.decision?.takeIf { it == "W" || it == "L" || it == "O" }?.let { if (it == "O") "OTL" else it },
            "${l.saves} SV", "${l.goalsAgainst} GA", "SO".takeIf { l.goalsAgainst == 0 && l.decision == "W" }).joinToString(" · ")
        else listOfNotNull(
            "${l.goals}G ${l.assists}A",
            if (l.plusMinus > 0) "+${l.plusMinus}" else if (l.plusMinus < 0) "${l.plusMinus}" else null,
            "${l.shots} SOG".takeIf { l.shots > 0 },
            "${l.hits} HIT".takeIf { l.hits > 0 },
            "${l.blocks} BLK".takeIf { l.blocks > 0 },
        ).joinToString(" · ")
    }

    /**
     * Who a player was on the ice with most at even strength-ish: the two teammates whose shifts
     * overlapped his the longest (goalies left out).
     */
    fun linemates(shifts: List<Shift>, playerId: Int, goalieIds: Set<Int>, n: Int = 2): List<Pair<String, Int>> {
        val mine = shifts.filter { it.playerId == playerId }
        if (mine.isEmpty()) return emptyList()
        val team = mine.first().teamAbbrev
        val overlap = HashMap<Int, Int>()
        val names = HashMap<Int, String>()
        for (s in shifts) {
            if (s.playerId == playerId || s.teamAbbrev != team || s.playerId in goalieIds) continue
            for (m in mine) if (m.period == s.period) {
                val o = minOf(m.end, s.end) - maxOf(m.start, s.start)
                if (o > 0) { overlap[s.playerId] = (overlap[s.playerId] ?: 0) + o; names[s.playerId] = s.name }
            }
        }
        return overlap.entries.sortedByDescending { it.value }.take(n).map { names[it.key]!! to it.value }
    }
}
