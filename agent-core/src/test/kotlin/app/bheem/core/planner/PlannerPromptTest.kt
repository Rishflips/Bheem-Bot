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
}
