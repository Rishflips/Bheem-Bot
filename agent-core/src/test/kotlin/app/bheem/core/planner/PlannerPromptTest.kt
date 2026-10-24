package app.bheem.core.planner

import app.bheem.core.action.ActionCatalog
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlannerPromptTest {
    @Test
    fun nowIsWrittenWithTheWeekdayDateAndTime() {
        assertEquals("Friday 2026-10-23 08:00 (IST)", PlannerPrompt.nowText(LocalDateTime.of(2026, 10, 23, 8, 0)))
        assertEquals("Monday 2026-10-26 21:05 (IST)", PlannerPrompt.nowText(LocalDateTime.of(2026, 10, 26, 21, 5)))
    }

    @Test
    fun theShippedPromptHasAVersionTheBenchmarkAlsoReads() {
        assertEquals("proposer-v3.7-line", PlannerPrompt.VERSION)
    }

    @Test
    fun changingThePromptTextRequiresANewVersionAndFreshGoldens() {
        val hash = java.security.MessageDigest.getInstance("SHA-256").digest(PlannerPrompt.command("X").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        assertEquals(
            PINNED_HASH,
            hash,
            "the command prompt text changed (hash $hash): bump PlannerPrompt.VERSION, re-run scripts/bench-release.ps1, replace eval/golden, then update PINNED_HASH",
        )
    }

    @Test
    fun theCommandPromptCarriesNowAndEveryActionSignatureInCatalogOrder() {
        val prompt = PlannerPrompt.command("Friday 2026-10-23 08:00 (IST)")
        assertContains(prompt, "Now: Friday 2026-10-23 08:00 (IST).")
        for ((name, keys) in ActionCatalog.required) {
            val line = name + keys.joinToString("") { "|<$it>" }
            assertTrue(line in prompt.lines(), "missing $line")
        }
    }

    @Test
    fun theCommandPromptRoutesQuestionsAndOtherRequests() {
        val prompt = PlannerPrompt.command("x")
        assertContains(prompt, "AnswerFromMemory for a question about my spending, tasks or notes")
        assertContains(prompt, "NoAction for anything else")
        assertContains(prompt, "digits and cents")
    }

    private companion object {
        const val PINNED_HASH = "8993d5838eb4b9189b343eab1e1b6f37029ad0a1a6c9e1f9792b96b446ffac8e"
    }
}
