package app.bheem.core.ledger

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LedgerTest {
    private val t0 = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val store = InMemoryLedgerStore()
    private val ledger = Ledger(store)

    private fun propose(id: String, at: LocalDateTime = t0) =
        ledger.record(id, LedgerEvent.PROPOSED, at, action = "CreateReminder", source = "bar")

    private fun record(id: String, event: LedgerEvent, at: LocalDateTime = t0, detail: String = "") = ledger.record(id, event, at, detail)

    private fun refused(r: LedgerResult): String = assertIs<LedgerResult.Refused>(r).reason

    @Test
    fun entriesGetRisingIdsAndKeepTheirOrder() {
        propose("a")
        propose("b")
        record("a", LedgerEvent.APPROVED)
        assertEquals(listOf(1L, 2L, 3L), store.all().map { it.id })
        assertEquals(listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED), ledger.history("a").map { it.event })
    }

    @Test
    fun theActionAndSourceAreFixedByTheFirstEntry() {
        propose("a")
        val approved = assertIs<LedgerResult.Recorded>(record("a", LedgerEvent.APPROVED)).entry
        assertEquals("CreateReminder", approved.action)
        assertEquals("bar", approved.source)
    }

    @Test
    fun theFullHappyPathAndUndoAreAllowed() {
        propose("a")
        for (e in listOf(LedgerEvent.APPROVED, LedgerEvent.EXECUTED, LedgerEvent.UNDONE)) assertIs<LedgerResult.Recorded>(record("a", e), e.name)
        assertEquals(LedgerEvent.UNDONE, ledger.stateOf("a"))
    }

    @Test
    fun nothingRunsBeforeItIsApproved() {
        propose("a")
        assertTrue("PROPOSED" in refused(record("a", LedgerEvent.EXECUTED)))
        assertEquals(1, store.all().size)
    }

    @Test
    fun aProposalCanBeDismissedAndAFailedOneRetried() {
        propose("a")
        assertIs<LedgerResult.Recorded>(record("a", LedgerEvent.DISMISSED))
        propose("b")
        record("b", LedgerEvent.APPROVED)
        record("b", LedgerEvent.FAILED, detail = "no calendar app")
        assertIs<LedgerResult.Recorded>(record("b", LedgerEvent.APPROVED))
        assertIs<LedgerResult.Recorded>(record("b", LedgerEvent.EXECUTED))
    }

    @Test
    fun finishedProposalsCannotChangeAgain() {
        propose("a")
        record("a", LedgerEvent.DISMISSED)
        assertTrue("DISMISSED" in refused(record("a", LedgerEvent.APPROVED)))
        propose("b")
        record("b", LedgerEvent.APPROVED)
        record("b", LedgerEvent.EXECUTED)
        record("b", LedgerEvent.UNDONE)
        assertTrue("UNDONE" in refused(record("b", LedgerEvent.EXECUTED)))
        propose("c")
        record("c", LedgerEvent.APPROVED)
        record("c", LedgerEvent.EXECUTED)
        assertTrue("EXECUTED" in refused(record("c", LedgerEvent.APPROVED)))
    }

    @Test
    fun aRejectionAndAReadAreSingleEntryRecords() {
        assertIs<LedgerResult.Recorded>(ledger.record("r", LedgerEvent.REJECTED, t0, "amount is out of range", action = "LogExpense", source = "bar"))
        assertTrue("REJECTED" in refused(record("r", LedgerEvent.APPROVED)))
        assertIs<LedgerResult.Recorded>(ledger.record("q", LedgerEvent.READ, t0, action = "AnswerFromMemory", source = "bar"))
        assertTrue("READ" in refused(record("q", LedgerEvent.EXECUTED)))
    }

    @Test
    fun aFirstEntryMustBeProposedRejectedOrReadAndNameItsActionAndSource() {
        assertTrue("first" in refused(ledger.record("x", LedgerEvent.APPROVED, t0, action = "CreateReminder", source = "bar")))
        assertTrue("action and source" in refused(ledger.record("y", LedgerEvent.PROPOSED, t0)))
        assertEquals(0, store.all().size)
    }

    @Test
    fun openProposalsAreTheOnesStillWaitingForTheUser() {
        propose("a")
        propose("b")
        propose("c")
        record("b", LedgerEvent.APPROVED)
        record("c", LedgerEvent.DISMISSED)
        assertEquals(listOf("a"), ledger.openProposals())
    }

    @Test
    fun undoIsOfferedOnlyForARecentlyExecutedAction() {
        propose("a")
        record("a", LedgerEvent.APPROVED)
        assertEquals(false, ledger.canUndo("a", t0.plusSeconds(1), 30))
        record("a", LedgerEvent.EXECUTED, at = t0.plusSeconds(5))
        assertEquals(true, ledger.canUndo("a", t0.plusSeconds(30), 30))
        assertEquals(false, ledger.canUndo("a", t0.plusSeconds(36), 30))
        assertEquals(false, ledger.canUndo("missing", t0, 30))
        assertNull(ledger.stateOf("missing"))
    }
}
