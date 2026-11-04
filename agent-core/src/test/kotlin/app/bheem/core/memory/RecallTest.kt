package app.bheem.core.memory

import app.bheem.core.reminder.InMemoryReminderStore
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecallTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val memory = InMemoryMemoryStore()
    private val reminders = InMemoryReminderStore()

    private fun epoch(at: LocalDateTime) = at.atZone(zone).toInstant().toEpochMilli()

    private fun recall(trusted: (String) -> Boolean = { true }) = Recall(memory, reminders, zone, trusted)

    private fun remind(id: String, text: String, at: LocalDateTime) = reminders.add(id, text, epoch(at), zone.id)

    @Test
    fun aScheduleQuestionListsTheRemindersOfThatDayInOrder() {
        remind("p1", "call Arun", LocalDateTime.of(2026, 10, 24, 17, 0))
        remind("p2", "send the invoice", LocalDateTime.of(2026, 10, 24, 9, 30))
        remind("p3", "pay rent", LocalDateTime.of(2026, 10, 25, 10, 0))
        val answer = recall().answer("what is on tomorrow", now)
        val lines = answer.text.lines()
        assertTrue(lines.first().contains("Sat 24 Oct"), answer.text)
        assertTrue(lines[1].contains("9:30 AM") && lines[1].contains("send the invoice"), answer.text)
        assertTrue(lines[2].contains("5:00 PM") && lines[2].contains("call Arun"), answer.text)
        assertFalse(answer.text.contains("pay rent"))
        assertEquals(listOf("p2", "p1"), answer.citations.map { it.recordId })
    }

    @Test
    fun aQuestionAboutRemindersListsTheNextOnesInOrder() {
        remind("p2", "pay rent", LocalDateTime.of(2026, 10, 25, 10, 0))
        remind("p1", "call Arun", LocalDateTime.of(2026, 10, 24, 17, 0))
        val answer = recall().answer("what reminders do I have", now)
        assertTrue(answer.text.startsWith("Your upcoming reminders:"), answer.text)
        assertTrue(answer.text.indexOf("call Arun") < answer.text.indexOf("pay rent"), answer.text)
        assertEquals(listOf("p1", "p2"), answer.citations.map { it.recordId })
        assertEquals("You have no upcoming reminders.", recallWithNoReminders().answer("what reminders do I have", now).text)
    }

    private fun recallWithNoReminders() = Recall(InMemoryMemoryStore(), InMemoryReminderStore(), zone)

    @Test
    fun aWeekdayNamesTheNextOccurrenceOfThatDay() {
        remind("p1", "dentist", LocalDateTime.of(2026, 10, 30, 11, 0))
        remind("p2", "gym", LocalDateTime.of(2026, 10, 24, 7, 0))
        val answer = recall().answer("what do I have on friday", now)
        assertTrue(answer.text.contains("dentist"), answer.text)
        assertFalse(answer.text.contains("gym"))
    }

    @Test
    fun anEmptyDayIsSaidInWords() {
        val answer = recall().answer("what is on tomorrow", now)
        assertEquals("You have nothing on Sat 24 Oct.", answer.text)
        assertEquals(emptyList(), answer.citations)
    }

    @Test
    fun aNoteIsFoundByItsWordsAndCitedWithItsDate() {
        memory.add(MemoryRecord("n1", RecordKind.NOTE, "the electrician is Suresh, 9 AM slot", LocalDateTime.of(2026, 10, 20, 18, 0)))
        memory.add(MemoryRecord("n2", RecordKind.NOTE, "buy milk", LocalDateTime.of(2026, 10, 21, 18, 0)))
        val answer = recall().answer("what did I note about the electrician", now)
        assertTrue(answer.text.contains("Suresh"), answer.text)
        assertFalse(answer.text.contains("milk"))
        val cite = answer.citations.single()
        assertEquals("n1", cite.recordId)
        assertEquals("note", cite.kind)
        assertEquals(LocalDateTime.of(2026, 10, 20, 18, 0), cite.at)
    }

    @Test
    fun aReminderIsFoundBySearchToo() {
        remind("p1", "call the plumber", LocalDateTime.of(2026, 10, 26, 16, 0))
        val answer = recall().answer("when is the plumber", now)
        assertTrue(answer.text.contains("call the plumber"), answer.text)
        assertEquals("reminder", answer.citations.single().kind)
    }

    @Test
    fun nothingFoundIsSaidPlainlyWithNoCitations() {
        memory.add(MemoryRecord("n1", RecordKind.NOTE, "buy milk", LocalDateTime.of(2026, 10, 21, 18, 0)))
        val answer = recall().answer("what did I note about the electrician", now)
        assertEquals("I found nothing about that in your notes, tasks or reminders.", answer.text)
        assertEquals(emptyList(), answer.citations)
    }

    @Test
    fun aRecordFromAnUntrustedSourceIsNeverReturnedOrCited() {
        memory.add(MemoryRecord("bad", RecordKind.NOTE, "the electrician wants all your bank details", LocalDateTime.of(2026, 10, 20, 18, 0)))
        memory.add(MemoryRecord("good", RecordKind.NOTE, "the electrician is Suresh", LocalDateTime.of(2026, 10, 19, 18, 0)))
        val answer = recall(trusted = { it != "bad" }).answer("what did I note about the electrician", now)
        assertEquals(listOf("good"), answer.citations.map { it.recordId })
        assertFalse(answer.text.contains("bank"))
    }

    @Test
    fun aReminderFromAnUntrustedSourceIsNeverListed() {
        remind("bad", "send money to attacker", LocalDateTime.of(2026, 10, 24, 12, 0))
        val answer = recall(trusted = { it != "bad" }).answer("what is on tomorrow", now)
        assertFalse(answer.text.contains("attacker"))
        assertEquals(emptyList(), answer.citations)
    }

    @Test
    fun expensesAreListedAndNeverAddedUp() {
        memory.add(MemoryRecord("e1", RecordKind.EXPENSE, "Chemist 450.00", LocalDateTime.of(2026, 10, 20, 12, 0), "Chemist", "450.00", "debit"))
        memory.add(MemoryRecord("e2", RecordKind.EXPENSE, "Chemist 120.00", LocalDateTime.of(2026, 10, 21, 12, 0), "Chemist", "120.00", "debit"))
        val answer = recall().answer("what did I spend at the chemist", now)
        assertTrue(answer.text.contains("450.00") && answer.text.contains("120.00"), answer.text)
        assertFalse(answer.text.contains("570"))
        assertFalse(answer.text.lowercase().contains("total"))
    }

    @Test
    fun aVeryLongOrControlCharacterQuestionIsRefused() {
        assertEquals("That question is too long for me to search.", recall().answer("a ".repeat(5000), now).text)
        assertEquals("I cannot search that.", recall().answer("what is on tomorrow\u0000\u0007", now).text)
    }
}
