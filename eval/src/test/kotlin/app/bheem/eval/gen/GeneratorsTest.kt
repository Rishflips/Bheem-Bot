package app.bheem.eval.gen

import app.bheem.eval.data.Dataset
import app.bheem.eval.data.DatasetValidator
import app.bheem.eval.score.AmountNormalizer
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratorsTest {
    private val commands = CommandGenerator.generate()
    private val notifications = NotificationGenerator.generate()
    private val adversarial = AdversarialGenerator.generate()

    @Test
    fun countsAreExact() {
        assertEquals(300, commands.size)
        assertEquals(200, notifications.size)
        assertEquals(50, adversarial.size)
    }

    @Test
    fun generationIsDeterministic() {
        assertEquals(commands, CommandGenerator.generate())
        assertEquals(notifications, NotificationGenerator.generate())
    }

    @Test
    fun wholeDatasetValidates() {
        assertEquals(emptyList(), DatasetValidator.validate(Dataset(commands, notifications, adversarial)))
    }

    @Test
    fun commandTextsAreUniqueAndAllThreeScriptsAppear() {
        assertEquals(commands.size, commands.map { it.text }.toSet().size)
        assertEquals(setOf("hi-latn", "en", "hi-deva"), commands.map { it.lang }.toSet())
        assertTrue(commands.any { c -> c.text.any { it in 'ऀ'..'ॿ' } })
    }

    @Test
    fun expectedTimesAreNeverInThePast() {
        val times = commands.filter { it.expected.action in setOf("CreateReminder", "CreateCalendarEvent") }
            .map { LocalDateTime.parse(it.expected.args.getValue(if ("time" in it.expected.args) "time" else "start")) }
        assertTrue(times.isNotEmpty())
        assertTrue(times.all { it.isAfter(ReferenceClock.NOW) })
    }

    @Test
    fun billDueTimesRollOverMonthEndsCorrectly() {
        val bills = notifications.filter { it.expected.action == "CreateReminder" }
        assertEquals(40, bills.size)
        assertTrue(bills.all { LocalDateTime.parse(it.expected.args.getValue("time")).toLocalTime().hour == 9 })
        assertTrue(bills.any { it.expected.args.getValue("time").startsWith("2026-11") })
    }

    @Test
    fun indianGroupedAmountsAppearAndParseToTheExpectedValue() {
        val transactions = notifications.filter { it.expected.action == "LogExpense" }
        assertEquals(120, transactions.size)
        assertTrue(transactions.any { Regex("""\d,\d\d,\d{3}""").containsMatchIn(it.text) })
        for (n in transactions) {
            val shown = Regex("""(?:Rs\.|INR )([\d,]+)\.00""").find(n.text)!!.groupValues[1]
            assertTrue(AmountNormalizer.equal(shown, n.expected.args.getValue("amount")), n.text)
        }
    }

    @Test
    fun noActionNotificationsIncludeOneTimePasswords() {
        val none = notifications.filter { it.expected.action == "NoAction" }
        assertEquals(40, none.size)
        assertTrue(none.any { "OTP" in it.text })
    }

    @Test
    fun adversarialCasesAllCarryAnInjectionAndVaryWrappers() {
        assertEquals(5, adversarial.map { it.wrapper }.toSet().size)
        assertEquals(50, adversarial.map { it.text }.toSet().size)
    }
}
