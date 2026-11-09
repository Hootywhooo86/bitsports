package com.bit128.bitsports.fantasy

import com.bit128.bitsports.fixture
import com.bit128.bitsports.nhl.Game
import com.bit128.bitsports.nhl.GameState
import com.bit128.bitsports.nhl.Injuries
import com.bit128.bitsports.nhl.NhlFantasy
import com.bit128.bitsports.nhl.PlayerRef
import com.bit128.bitsports.nhl.Shift
import com.bit128.bitsports.nhl.TeamLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Real NHL and ESPN answers: EDM @ NJD on 3 November 2026, McDavid's game log, the injury list. */
class FantasyTest {
    private val box = NhlFantasy.boxScore(fixture("box-2026020239.json"), 2026020239L)
    private val game = Game(2026020239L, GameState.OFF, true, Instant.now(), TeamLine(22, "EDM", 2, 34), TeamLine(1, "NJD", 3, 21), null)
    private val hughes = PlayerRef(8481559, "Jack Hughes", "NJD", "C")
    private val draisaitl = PlayerRef(8477934, "Leon Draisaitl", "EDM", "C")
    private val hellebuyck = PlayerRef(8476945, "Connor Hellebuyck", "NJD", "G")

    @Test fun searchFindsMcDavid() {
        val p = NhlFantasy.search(fixture("search-mcdavid.json")).single()
        assertEquals(PlayerRef(8478402, "Connor McDavid", "EDM", "C", 97), p)
    }

    @Test fun boxScoreLines() {
        val h = box.getValue(8481559)
        assertEquals(2, h.goals); assertEquals(1, h.assists); assertEquals(5, h.shots); assertEquals(3, h.blocks); assertEquals(1, h.plusMinus)
        val g = box.getValue(8476945)
        assertEquals(18, g.saves); assertEquals(3, g.goalsAgainst); assertEquals("W", g.decision)
    }

    @Test fun scoringPresets() {
        val h = box.getValue(8481559)
        // Standard: 2 G x3, 1 A x2, +1, 1 PPG, 5 SOG x.5, 3 BLK x.5 = 6+2+1+1+2.5+1.5
        assertEquals(14.0, Scoring.STANDARD.points(h, goalie = false), 0.001)
        assertEquals(3.0, Scoring.POINTS.points(h, goalie = false), 0.001)
        // Goalie: W 4 + 18 x .2 - 3 = 4.6
        assertEquals(4.6, Scoring.STANDARD.points(box.getValue(8476945), goalie = true), 0.001)
        assertEquals("4.6", Scoring.fmt(4.6)); assertEquals("13", Scoring.fmt(13.0))
        assertEquals(Scoring.STANDARD, Scoring.fromJson(Scoring.STANDARD.toJson()))
    }

    @Test fun aNightRanksPlayersAndPicksTheMvp() {
        val list = FantasyList("a", "My pool", listOf(draisaitl, hughes, hellebuyck, PlayerRef(1, "Nobody Playing", "VAN", "D")))
        val n = FantasyNight.night(list, listOf(game), box, Scoring.STANDARD)
        assertEquals("Jack Hughes", n.mvp!!.player.name)
        // Draisaitl: 1 G, 1 PPG, 4 SOG = 6; Hellebuyck's win = 4.6.
        assertEquals(listOf("Jack Hughes", "Leon Draisaitl", "Connor Hellebuyck"), n.rows.take(3).map { it.player.name })
        assertTrue(n.rows.last().game == null)
        assertEquals("2G 1A · +1 · 5 SOG · 3 BLK", FantasyNight.statText(box[8481559], false))
        assertEquals("W · 18 SV · 3 GA", FantasyNight.statText(box[8476945], true))
    }

    @Test fun pointStreakFromTheGameLog() {
        assertEquals(8, NhlFantasy.pointStreak(fixture("gamelog-8478402.json")).coerceAtMost(8))
    }

    @Test fun injuriesMatchOnNameAndTeam() {
        val all = Injuries.parse(fixture("espn-injuries.json"))
        assertEquals("Injured Reserve", Injuries.find(all, PlayerRef(1, "Troy Terry", "ANA", "RW"))!!.status)
        assertNull(Injuries.find(all, PlayerRef(1, "Troy Terry", "EDM", "RW")))
        assertTrue(Injuries.scratched(listOf("Zach Hyman", "Connor Brown"), PlayerRef(2, "Zach Hyman", "EDM", "LW")))
    }

    @Test fun linematesAreTheLongestOverlaps() {
        val shifts = listOf(
            Shift(1, "EDM", "McDavid", 1, 0, 50), Shift(1, "EDM", "McDavid", 1, 100, 160),
            Shift(2, "EDM", "Hyman", 1, 0, 45), Shift(2, "EDM", "Hyman", 1, 100, 150),
            Shift(3, "EDM", "Bouchard", 1, 20, 60),
            Shift(4, "EDM", "Skinner", 1, 0, 1200), // goalie: left out
            Shift(5, "CGY", "Huberdeau", 1, 0, 200), // other team
        )
        assertEquals(listOf("Hyman" to 95, "Bouchard" to 30), FantasyNight.linemates(shifts, 1, setOf(4)))
    }

    @Test fun listsRoundTrip() {
        val l = listOf(FantasyList("x", "Family pool", listOf(hughes, hellebuyck.copy(number = 41))))
        assertEquals(l, FantasyStore.decode(FantasyStore.encode(l)))
    }
}
