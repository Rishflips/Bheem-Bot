package app.bheem.core.memory

import app.bheem.core.reminder.InMemoryReminderStore
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

/** Twelve questions over one invented set of records at a fixed clock (Friday 23 Oct 2026, 08:00). The answers are pinned word for word. */
class RecallScenarioTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)

    private val recall = run {
        val memory = InMemoryMemoryStore()
        val reminders = InMemoryReminderStore()
        fun remind(id: String, text: String, at: LocalDateTime) = reminders.add(id, text, at.atZone(zone).toInstant().toEpochMilli(), zone.id)
        memory.add(MemoryRecord("n1", RecordKind.NOTE, "the electrician is Suresh, comes at 9 AM", LocalDateTime.of(2026, 10, 20, 18, 0)))
        memory.add(MemoryRecord("n2", RecordKind.NOTE, "scooter key is in the blue bowl", LocalDateTime.of(2026, 10, 21, 9, 0)))
        memory.add(MemoryRecord("t1", RecordKind.TASK, "buy milk", LocalDateTime.of(2026, 10, 22, 9, 0)))
        memory.add(MemoryRecord("t2", RecordKind.TASK, "renew insurance", LocalDateTime.of(2026, 10, 22, 10, 0)))
        memory.add(MemoryRecord("e1", RecordKind.EXPENSE, "Chemist 450.00", LocalDateTime.of(2026, 10, 20, 12, 0), "Chemist", "450.00", "debit"))
        remind("r1", "call Arun", LocalDateTime.of(2026, 10, 24, 17, 0))
        remind("r2", "send the invoice", LocalDateTime.of(2026, 10, 24, 9, 30))
        remind("r3", "dentist", LocalDateTime.of(2026, 10, 30, 11, 0))
        remind("r4", "pay rent", LocalDateTime.of(2026, 11, 1, 10, 0))
        Recall(memory, reminders, zone)
    }

    private fun ask(question: String) = recall.answer(question, now).text

    @Test
    fun tomorrow() = assertEquals("Tomorrow, Sat 24 Oct:\n- 9:30 AM send the invoice\n- 5:00 PM call Arun", ask("what is on tomorrow"))

    @Test
    fun todayIsEmpty() = assertEquals("You have nothing on Fri 23 Oct.", ask("what's on today"))

    @Test
    fun aWeekday() = assertEquals("Fri 30 Oct:\n- 11:00 AM dentist", ask("what do I have on friday"))

    @Test
    fun upcomingReminders() = assertEquals(
        "Your upcoming reminders:\n- Sat 24 Oct 9:30 AM send the invoice\n- Sat 24 Oct 5:00 PM call Arun\n- Fri 30 Oct 11:00 AM dentist\n- Sun 1 Nov 10:00 AM pay rent",
        ask("what reminders do I have"),
    )

    @Test
    fun aNoteByItsTopic() = assertEquals("Here is what I found:\n- the electrician is Suresh, comes at 9 AM (note, 20 Oct)", ask("what did I note about the electrician"))

    @Test
    fun whereIKeptSomething() = assertEquals("Here is what I found:\n- scooter key is in the blue bowl (note, 21 Oct)", ask("where did I keep the scooter key"))

    @Test
    fun myTasks() = assertEquals("You have 2 tasks:\n- renew insurance\n- buy milk", ask("show me my tasks"))

    @Test
    fun spendingIsListedNeverTotalled() = assertEquals("Here is what I found:\n- Chemist 450.00 (expense, 20 Oct)", ask("what did I spend at the chemist"))

    @Test
    fun nextWeek() = assertEquals("Next week:\n- Fri 30 Oct 11:00 AM dentist\n- Sun 1 Nov 10:00 AM pay rent", ask("what is on next week"))

    @Test
    fun nothingAboutThePlumber() = assertEquals("I found nothing about that in your notes, tasks or reminders.", ask("what did I note about the plumber"))

    @Test
    fun aReminderBySearch() = assertEquals("Here is what I found:\n- pay rent (reminder, Sun 1 Nov 10:00 AM)", ask("when is the rent due"))

    @Test
    fun thisWeek() = assertEquals(
        "This week:\n- Sat 24 Oct 9:30 AM send the invoice\n- Sat 24 Oct 5:00 PM call Arun",
        ask("what is on this week"),
    )
}
