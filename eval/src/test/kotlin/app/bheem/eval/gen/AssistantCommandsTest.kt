package app.bheem.eval.gen

import app.bheem.core.action.ActionCatalog
import app.bheem.eval.data.Dataset
import app.bheem.eval.data.DatasetValidator
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AssistantCommandsTest {
    private val cases = AssistantCommands.cases()

    @Test
    fun idsAreUniqueAndKeepClearOfTheTemplatedSet() {
        assertEquals(cases.size, cases.map { it.id }.toSet().size)
        assertTrue(cases.all { Regex("""ast-\d{3}""").matches(it.id) })
    }

    @Test
    fun allCasesAreEnglishAndValidAgainstTheCatalog() {
        assertTrue(cases.all { it.lang == "en" })
        assertEquals(emptyList(), DatasetValidator.validate(Dataset(cases, emptyList(), emptyList())))
    }

    @Test
    fun textsAreUniqueAndNotCopiesOfTheTemplatedCommands() {
        val own = cases.map { it.text.lowercase() }
        assertEquals(own.size, own.toSet().size)
        val templated = CommandGenerator.generate().map { it.text.lowercase() }.toSet()
        assertTrue(own.none { it in templated })
    }

    @Test
    fun everyActionAppearsAndAskHasRealWeight() {
        val byAction = cases.groupingBy { it.expected.action }.eachCount()
        assertEquals(ActionCatalog.offeredToModel, byAction.keys)
        assertTrue((byAction["AnswerFromMemory"] ?: 0) >= 8, "ask cases: ${byAction["AnswerFromMemory"]}")
        assertTrue((byAction["NoAction"] ?: 0) >= 5, "out of scope cases: ${byAction["NoAction"]}")
    }

    @Test
    fun moneyIsAtMostOneInSixOfTheCases() {
        val money = cases.count { it.expected.action == "LogExpense" }
        assertTrue(money * 6 <= cases.size, "$money money cases of ${cases.size}")
    }

    @Test
    fun scheduledTimesAreMinuteIsoAndAfterTheReferenceNow() {
        val timed = cases.filter { it.expected.action in setOf("CreateReminder", "CreateCalendarEvent") }
        assertTrue(timed.isNotEmpty())
        for (c in timed) {
            val iso = c.expected.args.getValue(if (c.expected.action == "CreateReminder") "time" else "start")
            val at = LocalDateTime.parse(iso, ReferenceClock.isoMinute)
            assertTrue(at.isAfter(ReferenceClock.NOW), "${c.id} $iso")
        }
    }

    @Test
    fun theSetIsFixedNotRandom() {
        assertEquals(cases, AssistantCommands.cases())
    }

    @Test
    fun theAssistantSetHasAtLeastAHundredCases() {
        assertTrue(cases.size >= 100, "assistant cases: ${cases.size}")
    }

    @Test
    fun theHeldOutSetIsSeparateValidAndNotACopyOfTheDevelopmentSet() {
        val here = java.nio.file.Paths.get("").toAbsolutePath().let { if (it.fileName.toString() == "eval") it else it.resolve("eval") }
        val held = app.bheem.eval.data.Jsonl.readText<app.bheem.eval.data.CommandCase>(java.nio.file.Files.readString(here.resolve("data/assistant-heldout.jsonl")))
        assertTrue(held.size >= 30, "held-out cases: ${held.size}")
        assertTrue(held.all { Regex("""ast-h\d{2}""").matches(it.id) }, "held-out ids are not all ast-hNN")
        assertEquals(emptyList(), DatasetValidator.validate(Dataset(held, emptyList(), emptyList())))
        val dev = cases.map { it.text.lowercase().trim() }.toSet()
        assertEquals(0, held.count { it.text.lowercase().trim() in dev }, "held-out texts that also appear in the development set")
        assertEquals(held.size, held.map { it.text.lowercase().trim() }.toSet().size, "duplicate held-out texts")
        assertTrue(held.none { it.expected.action == "OpenUpiPayment" })
    }
}
