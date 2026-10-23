package app.bheem.eval.speech

import app.bheem.eval.gen.CommandGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandSetEntityCoverageTest {
    private val english = CommandGenerator.generate().filter { it.lang == "en" }

    @Test
    fun theEnglishCommandSetIsTheOneHundredAndOneCommandsTheBenchmarkUses() {
        assertEquals(101, english.size)
    }

    @Test
    fun aPerfectTranscriptScoresNoErrorsOnEveryCommand() {
        for (c in english) {
            val score = SpeechScorer.score(c, c.text)
            assertEquals(0, score.wer.errors, c.text)
            assertEquals(score.entities.totalCount, score.entities.correctCount, c.text)
        }
    }

    @Test
    fun everyAmountNameAndTimeInTheCommandSetBecomesAnEntity() {
        for (c in english) {
            val found = EntityExtractor.fromCommand(c.text, c.expected)
            val args = c.expected.args
            if ("amount" in args) assertEquals(1, found.count { it.kind == EntityKind.AMOUNT }, c.text)
            if (listOf("payee", "merchant", "to").any { it in args }) assertTrue(found.any { it.kind == EntityKind.NAME }, c.text)
            if (listOf("time", "start").any { it in args }) assertEquals(2, found.count { it.kind == EntityKind.TIME }, c.text)
        }
    }

    @Test
    fun theNamesTheCommandSetUsesAreFoundInRemindersAndMeetings() {
        val reminders = english.filter { it.expected.action in setOf("CreateReminder", "CreateCalendarEvent") }
        assertTrue(reminders.isNotEmpty())
        for (c in reminders) assertEquals(1, EntityExtractor.fromCommand(c.text, c.expected).count { it.kind == EntityKind.NAME }, c.text)
    }

    @Test
    fun aWrongAmountInAnyPaymentCommandIsCaught() {
        for (c in english.filter { it.expected.action == "OpenUpiPayment" }) {
            val wrong = c.text.replace(Regex("""\d+"""), "7")
            val score = SpeechScorer.score(c, wrong)
            assertEquals(1.0, score.entities.errorRate(EntityKind.AMOUNT), c.text)
            assertEquals(0.0, score.entities.errorRate(EntityKind.NAME), c.text)
        }
    }
}
