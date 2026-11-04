package app.bheem.core.feed

import app.bheem.core.gate.RiskLevel
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextUpTest {
    private val now = LocalDateTime.of(2026, 11, 4, 9, 0)

    private fun item(id: String, due: LocalDateTime?, status: FeedStatus = FeedStatus.DONE) =
        FeedItem(id, "Reminder", "text $id", status, RiskLevel.L1, canUndo = false, failure = null, createdAt = now.minusDays(1), dueAt = due)

    @Test
    fun theEarliestFutureReminderIsNextAndTheRestAreCounted() {
        val items = listOf(item("late", now.plusHours(8)), item("soon", now.plusHours(1)), item("mid", now.plusHours(3)))
        val next = NextUp.of(items, now)!!
        assertEquals("soon", next.first.id)
        assertEquals(3, next.count)
        assertEquals(listOf("soon", "mid", "late"), next.all.map { it.id })
    }

    @Test
    fun pastReminderAndThingsStillWaitingAreNotNext() {
        val items = listOf(item("past", now.minusHours(1)), item("waiting", now.plusHours(1), FeedStatus.PENDING), item("note", null))
        assertNull(NextUp.of(items, now))
    }
}
