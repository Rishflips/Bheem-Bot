package app.bheem.core.feed

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.proposal.InMemoryProposalStore
import app.bheem.core.proposal.StoredProposal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedTest {
    private val t0 = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val ledgerStore = InMemoryLedgerStore()
    private val ledger = Ledger(ledgerStore)
    private val proposals = InMemoryProposalStore()

    private fun items(now: LocalDateTime, window: Long = 30L) = Feed.items(InMemoryFeedSource(proposals, ledgerStore).rows(100), now, window)

    private fun describe(name: String, vararg args: Pair<String, String>) = ActionDescriber.describe(ProposedAction(name, args.toMap()))

    private fun stored(id: String, at: LocalDateTime, action: ProposedAction = ProposedAction("SaveNote", mapOf("text" to "note $id"))) =
        StoredProposal(id, action, RiskLevel.L0, true, at, "text", null).also {
            proposals.put(it)
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
        val items = items(t0.plusMinutes(10))
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
        assertEquals(emptyList(), items(t0))
    }

    @Test
    fun aDoneItemOffersUndoOnlyInsideTheWindowAndAFailedOneShowsItsReason() {
        val a = stored("a", t0)
        ledger.record("a", LedgerEvent.APPROVED, t0)
        ledger.record("a", LedgerEvent.EXECUTED, t0)
        assertEquals(true, items(t0.plusSeconds(10)).single().canUndo)
        assertEquals(false, items(t0.plusSeconds(40)).single().canUndo)
        val f = stored("f", t0)
        ledger.record("f", LedgerEvent.APPROVED, t0)
        ledger.record("f", LedgerEvent.FAILED, t0, "no calendar app")
        val failed = items(t0).first { it.id == "f" }
        assertEquals(FeedStatus.FAILED, failed.status)
        assertEquals("no calendar app", failed.failure)
    }

    @Test
    fun aDoneItemShowsTheExecutorsNoteAndOtherItemsShowNone() {
        stored("a", t0)
        ledger.record("a", LedgerEvent.APPROVED, t0)
        ledger.record("a", LedgerEvent.EXECUTED, t0, "may arrive late")
        stored("b", t0.plusSeconds(1))
        ledger.record("b", LedgerEvent.APPROVED, t0)
        ledger.record("b", LedgerEvent.EXECUTED, t0)
        stored("c", t0.plusSeconds(2))
        val byId = items(t0).associateBy { it.id }
        assertEquals("may arrive late", byId.getValue("a").note)
        assertEquals(null, byId.getValue("b").note)
        assertEquals(null, byId.getValue("c").note)
    }

    @Test
    fun theFeedIsCappedAtTwentyItems() {
        val many = (1..25).map { stored("p$it", t0.plusMinutes(it.toLong())) }
        assertEquals(20, items(t0.plusHours(1)).size)
    }

    @Test
    fun anApprovedItemStillRunningShowsAsWorkingWithoutButtons() {
        val a = stored("a", t0)
        ledger.record("a", LedgerEvent.APPROVED, t0)
        val item = items(t0).single { it.id == "a" }
        assertEquals(FeedStatus.WORKING, item.status)
        assertEquals(false, item.canUndo)
    }
}
