package app.bheem.core.feed

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.proposal.StoredProposal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedTest {
    private val t0 = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val ledger = Ledger(InMemoryLedgerStore())

    private fun describe(name: String, vararg args: Pair<String, String>) = ActionDescriber.describe(ProposedAction(name, args.toMap()))

    private fun stored(id: String, at: LocalDateTime, action: ProposedAction = ProposedAction("SaveNote", mapOf("text" to "note $id"))) =
        StoredProposal(id, action, RiskLevel.L0, true, at, "text", null).also {
            ledger.record(id, LedgerEvent.PROPOSED, at, action = action.action, source = "bar")
        }

    @Test
    fun eachActionIsDescribedInPlainWords() {
        assertEquals(Described("Reminder", "call Ravi, Sat 24 Oct, 9:00 AM"), describe("CreateReminder", "text" to "call Ravi", "time" to "2026-10-24T09:00"))
        assertEquals(Described("Calendar event", "dentist, Sat 24 Oct, 4:00 PM"), describe("CreateCalendarEvent", "title" to "dentist", "start" to "2026-10-24T16:00"))
        assertEquals(Described("Task", "renew the passport"), describe("CreateTask", "text" to "renew the passport"))
        assertEquals(Described("Note", "locker number 27"), describe("SaveNote", "text" to "locker number 27"))
        assertEquals(Described("Expense", "\u20B9380.00 at Uber"), describe("LogExpense", "amount" to "380.00", "merchant" to "Uber", "direction" to "debit"))
        assertEquals(Described("Message to Arun on WhatsApp", "I am late"), describe("DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "I am late"))
        assertEquals(Described("Pay Ravi", "\u20B9840.00"), describe("OpenUpiPayment", "payee" to "Ravi", "amount" to "840.00"))
    }

    @Test
    fun pendingCardsComeFirstNewestFirstThenRecentResults() {
        val a = stored("a", t0)
        val b = stored("b", t0.plusMinutes(1))
        val c = stored("c", t0.plusMinutes(2))
        ledger.record("a", LedgerEvent.APPROVED, t0.plusMinutes(3))
        ledger.record("a", LedgerEvent.EXECUTED, t0.plusMinutes(3))
        val items = Feed.items(listOf(a, b, c), ledger, t0.plusMinutes(10), undoWindowSeconds = 30)
        assertEquals(listOf("c", "b", "a"), items.map { it.id })
        assertEquals(listOf(FeedStatus.PENDING, FeedStatus.PENDING, FeedStatus.DONE), items.map { it.status })
    }

    @Test
    fun dismissedAndUndoneProposalsLeaveTheFeed() {
        val a = stored("a", t0)
        val b = stored("b", t0)
        ledger.record("a", LedgerEvent.DISMISSED, t0)
        ledger.record("b", LedgerEvent.APPROVED, t0)
        ledger.record("b", LedgerEvent.EXECUTED, t0)
        ledger.record("b", LedgerEvent.UNDONE, t0)
        assertEquals(emptyList(), Feed.items(listOf(a, b), ledger, t0, 30))
    }

    @Test
    fun aDoneItemOffersUndoOnlyInsideTheWindowAndAFailedOneShowsItsReason() {
        val a = stored("a", t0)
        ledger.record("a", LedgerEvent.APPROVED, t0)
        ledger.record("a", LedgerEvent.EXECUTED, t0)
        assertEquals(true, Feed.items(listOf(a), ledger, t0.plusSeconds(10), 30).single().canUndo)
        assertEquals(false, Feed.items(listOf(a), ledger, t0.plusSeconds(40), 30).single().canUndo)
        val f = stored("f", t0)
        ledger.record("f", LedgerEvent.APPROVED, t0)
        ledger.record("f", LedgerEvent.FAILED, t0, "no calendar app")
        val failed = Feed.items(listOf(f), ledger, t0, 30).single()
        assertEquals(FeedStatus.FAILED, failed.status)
        assertEquals("no calendar app", failed.failure)
    }

    @Test
    fun theFeedIsCappedAtTwentyItems() {
        val many = (1..25).map { stored("p$it", t0.plusMinutes(it.toLong())) }
        assertEquals(20, Feed.items(many, ledger, t0.plusHours(1), 30).size)
    }
}
