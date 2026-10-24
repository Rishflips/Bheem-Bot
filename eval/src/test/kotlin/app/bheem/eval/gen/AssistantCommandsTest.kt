package app.bheem.eval.gen

import app.bheem.eval.catalog.ActionCatalog
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
        assertEquals(ActionCatalog.names, byAction.keys)
        assertTrue((byAction["AnswerFromMemory"] ?: 0) >= 8, "ask cases: ${byAction["AnswerFromMemory"]}")
        assertTrue((byAction["NoAction"] ?: 0) >= 5, "out of scope cases: ${byAction["NoAction"]}")
    }

    @Test
    fun moneyIsAtMostOneInSixOfTheCases() {
        val money = cases.count { it.expected.action in setOf("LogExpense", "OpenUpiPayment") }
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
}
