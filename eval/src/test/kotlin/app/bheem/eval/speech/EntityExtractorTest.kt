package app.bheem.eval.speech

import app.bheem.eval.data.ExpectedAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityExtractorTest {
    private fun entities(text: String, action: String, vararg args: Pair<String, String>) =
        EntityExtractor.fromCommand(text, ExpectedAction(action, args.toMap()))

    private fun amount(vararg tokens: String) = Entity(EntityKind.AMOUNT, tokens.toList())

    private fun name(vararg tokens: String) = Entity(EntityKind.NAME, tokens.toList())

    private fun time(vararg tokens: String) = Entity(EntityKind.TIME, tokens.toList())

    @Test
    fun anExpenseHasAnAmountAndAMerchant() {
        assertEquals(
            listOf(amount("840"), name("swiggy")),
            entities("I spent 840 rupees at Swiggy", "LogExpense", "amount" to "840.00", "merchant" to "Swiggy", "direction" to "debit"),
        )
    }

    @Test
    fun aPaymentHasAnAmountAndAPayee() {
        assertEquals(
            listOf(amount("2300"), name("priya")),
            entities("pay Priya 2300 rupees", "OpenUpiPayment", "payee" to "Priya", "amount" to "2300"),
        )
    }

    @Test
    fun aMerchantOfTwoWordsIsOneEntityInNormalizedForm() {
        assertEquals(name("bigbasket"), entities("I spent 120 rupees at Big Basket", "LogExpense", "amount" to "120.00", "merchant" to "Big Basket", "direction" to "debit")[1])
        assertEquals(name("murugan", "idli"), entities("I spent 450 rupees at Murugan Idli", "LogExpense", "amount" to "450.00", "merchant" to "Murugan Idli", "direction" to "debit")[1])
    }

    @Test
    fun aReminderHasTheDayTheHourAndTheNameInsideTheText() {
        assertEquals(
            listOf(time("tomorrow"), time("9", "pm"), name("meena")),
            entities("remind me tomorrow at 9 PM to call Meena", "CreateReminder", "text" to "call Meena", "time" to "2026-10-24T21:00"),
        )
    }

    @Test
    fun relativeDaysCoverTodayAndTheDayAfterTomorrow() {
        val today = entities("remind me today at 6 PM to call Ravi", "CreateReminder", "text" to "call Ravi", "time" to "2026-10-23T18:00")
        assertEquals(time("today"), today[0])
        val later = entities("remind me the day after tomorrow at 9 AM to call Vivek", "CreateReminder", "text" to "call Vivek", "time" to "2026-10-25T09:00")
        assertEquals(time("day", "after", "tomorrow"), later[0])
        assertEquals(time("9", "am"), later[1])
    }

    @Test
    fun minutesAndMidnightAndNoonAreSpokenForms() {
        assertEquals(time("9:30", "pm"), entities("remind me at 9:30 PM to call Ravi", "CreateReminder", "text" to "call Ravi", "time" to "2026-10-23T21:30")[0])
        assertEquals(time("12", "am"), entities("remind me at 12 AM to call Ravi", "CreateReminder", "text" to "call Ravi", "time" to "2026-10-24T00:00")[0])
        assertEquals(time("12", "pm"), entities("remind me at 12 PM to call Ravi", "CreateReminder", "text" to "call Ravi", "time" to "2026-10-23T12:00")[0])
    }

    @Test
    fun aTimeThatIsNotSpokenAsTheReferenceSaysItIsNotAnEntity() {
        val found = entities("remind me to call Ravi", "CreateReminder", "text" to "call Ravi", "time" to "2026-10-24T21:00")
        assertEquals(listOf(name("ravi")), found)
    }

    @Test
    fun aMeetingHasAPersonADayAndAnHour() {
        assertEquals(
            listOf(time("tomorrow"), time("9", "pm"), name("karthik")),
            entities("schedule a meeting with Karthik tomorrow at 9 PM", "CreateCalendarEvent", "title" to "meeting with Karthik", "start" to "2026-10-24T21:00"),
        )
    }

    @Test
    fun theChannelNameIsNotAPersonName() {
        assertEquals(
            listOf(name("arun")),
            entities("message Arun on WhatsApp that do not cook dinner tonight", "DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "do not cook dinner tonight"),
        )
    }

    @Test
    fun capitalizedWordsInsideTextAreNamesAndMonthsAreTimes() {
        val found = entities("save a note: Ravi's birthday is on 12 December", "SaveNote", "text" to "Ravi's birthday is on 12 December")
        assertEquals(listOf(time("december"), name("ravis")).toSet(), found.toSet())
        assertEquals(2, found.size)
    }

    @Test
    fun theFirstWordAndAmPmAndTheWordIAreNeverNames() {
        assertTrue(entities("Remind me to buy milk", "CreateReminder", "text" to "buy milk", "time" to "2026-10-24T09:00").isEmpty())
        assertTrue(entities("I will pay at 9 AM", "CreateTask", "text" to "pay").none { it.kind == EntityKind.NAME })
    }

    @Test
    fun commandsWithoutDetailsAndSmallTalkHaveNoEntities() {
        assertTrue(entities("add a task to get the car serviced", "CreateTask", "text" to "get the car serviced").isEmpty())
        assertTrue(entities("what is the capital of France", "NoAction").isEmpty())
    }

    @Test
    fun aQueryKeepsTheNamesItAsksAbout() {
        assertEquals(
            listOf(name("swiggy")),
            entities("how much went to Swiggy this month", "AnswerFromMemory", "query" to "*"),
        )
    }
}
