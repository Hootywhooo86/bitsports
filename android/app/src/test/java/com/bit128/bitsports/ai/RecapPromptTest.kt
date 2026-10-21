package com.bit128.bitsports.ai

import com.bit128.bitsports.fixture
import com.bit128.bitsports.nhl.GameAnalysis
import com.bit128.bitsports.nhl.NhlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RecapPromptTest {
    private val d = NhlParser.playByPlay(fixture("pbp-2026020141.json"), Instant.EPOCH)
    private val prompt = RecapPrompt.build(d, 1)
    private val system = prompt.first
    private val user = prompt.second

    @Test fun systemPromptSetsVoiceAndGuardrails() {
        assertTrue(system.contains("3-4 sentences"))
        assertTrue(system.contains("Only use facts listed"))
    }

    @Test fun userPromptCarriesThePeriodFacts() {
        val stats = GameAnalysis.periodStats(d, 1)
        assertTrue(user, user.contains("San Jose Sharks"))
        assertTrue(user, user.contains("Montréal Canadiens"))
        assertTrue(user, user.contains("Score after the 1st: San Jose Sharks 1, Montréal Canadiens 1"))
        assertTrue(user, user.contains("Shots on goal: San Jose Sharks ${stats.awayShots}, Montréal Canadiens ${stats.homeShots}"))
        GameAnalysis.goals(d).filter { it.period == 1 }.forEach { g ->
            assertTrue(user, user.contains(g.scorer))
            assertTrue(user, user.contains(g.timeInPeriod))
        }
    }

    @Test fun mentionsNoOneOutsideTheGame() {
        val allowed = d.roster.values.toSet() + setOf("San Jose", "Jose Sharks", "San Jose Sharks", "Montréal Canadiens", "Shots on", "Power play", "Score after")
        val names = Regex("\\b\\p{Lu}[\\p{L}'-]+ \\p{Lu}[\\p{L}'-]+").findAll(user).map { it.value }.toList()
        val strangers = names.filter { n -> allowed.none { it.contains(n) || n.contains(it) } }
        assertEquals(emptyList<String>(), strangers)
    }

    @Test fun staysSmall() {
        assertTrue(user.length < 3000)
    }

    @Test fun reasonTextIsOneHonestSentence() {
        assertEquals("Turn on AI in bitcall to get recaps.", FamilyAi.reasonText("ai_off"))
        assertEquals("Download a model in bitcall to get recaps.", FamilyAi.reasonText("no_model"))
        assertEquals("Install bitcall to get AI recaps.", FamilyAi.reasonText("not_installed"))
    }
}
