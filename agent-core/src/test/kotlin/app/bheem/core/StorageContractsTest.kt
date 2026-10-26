package app.bheem.core

import app.bheem.core.action.ProposedAction
import app.bheem.core.feed.Feed
import app.bheem.core.feed.FeedRow
import app.bheem.core.feed.FeedStatus
import app.bheem.core.feed.InMemoryFeedSource
import app.bheem.core.gate.Gate
import app.bheem.core.gate.Provenance
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.ledger.NewLedgerEntry
import app.bheem.core.memory.Ask
import app.bheem.core.memory.InMemoryMemoryStore
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.RecordKind
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.Outcome
import app.bheem.core.proposal.ActionExecutor
import app.bheem.core.proposal.ExecutionResult
import app.bheem.core.proposal.InMemoryProposalStore
import app.bheem.core.proposal.ProposalCoordinator
import app.bheem.core.proposal.ProposalMeta
import app.bheem.core.proposal.StoredProposal
import app.bheem.core.proposal.TransactionRunner
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StorageContractsTest {
    private val t0 = LocalDateTime.of(2026, 10, 23, 8, 0)

    private fun entry(id: String, event: LedgerEvent, at: LocalDateTime = t0) = NewLedgerEntry(at, id, event, "CreateReminder", "bar", "")

    @Test
    fun theStoreAssignsTheLedgerIdsAndReturnsTheStoredEntry() {
        val store = InMemoryLedgerStore(firstId = 100)
        assertEquals(100L, store.append(entry("a", LedgerEvent.PROPOSED)).id)
        assertEquals(101L, store.append(entry("b", LedgerEvent.PROPOSED)).id)
        assertEquals(102L, Ledger(store).record("a", LedgerEvent.APPROVED, t0).let { assertIs<app.bheem.core.ledger.LedgerResult.Recorded>(it).entry.id })
    }

    @Test
    fun theStoreAnswersHistoryLastEventAndOpenProposalsWithoutListingEverything() {
        val store = InMemoryLedgerStore()
        store.append(entry("a", LedgerEvent.PROPOSED))
        store.append(entry("b", LedgerEvent.PROPOSED))
        store.append(entry("a", LedgerEvent.APPROVED))
        store.append(entry("c", LedgerEvent.PROPOSED))
        store.append(entry("c", LedgerEvent.DISMISSED))
        assertEquals(listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED), store.history("a").map { it.event })
        assertEquals(LedgerEvent.APPROVED, store.lastEvent("a")?.event)
        assertNull(store.lastEvent("missing"))
        assertEquals(listOf("b"), store.openProposalIds())
    }

    private class RecordingTx : TransactionRunner {
        var depth = 0
        var committed = 0

        override fun <T> run(block: () -> T): T {
            depth++
            try {
                return block().also { committed++ }
            } finally {
                depth--
            }
        }
    }

    private class GuardedProposals(private val tx: RecordingTx, private val inner: InMemoryProposalStore) : app.bheem.core.proposal.ProposalStore by inner {
        val writesOutsideTx = mutableListOf<String>()

        override fun put(proposal: StoredProposal) {
            if (tx.depth == 0) writesOutsideTx += proposal.id
            inner.put(proposal)
        }
    }

    private class GuardedLedger(private val tx: RecordingTx, private val inner: InMemoryLedgerStore) : app.bheem.core.ledger.LedgerStore by inner {
        val writesOutsideTx = mutableListOf<LedgerEvent>()

        override fun append(entry: NewLedgerEntry): app.bheem.core.ledger.LedgerEntry {
            if (tx.depth == 0) writesOutsideTx += entry.event
            return inner.append(entry)
        }
    }

    @Test
    fun aProposalAndItsLedgerEntryAreWrittenInOneTransaction() {
        val tx = RecordingTx()
        val proposals = GuardedProposals(tx, InMemoryProposalStore())
        val ledgerStore = GuardedLedger(tx, InMemoryLedgerStore())
        val meta = ProposalMeta("proposer-v3.7-line", "gemma-4-e2b", "GPU")
        val pipeline = CommandPipeline({ _, _ -> "CreateReminder|the recital|2026-10-24T19:00" }, Gate(), Ledger(ledgerStore), proposals, tx, { meta }) { "p1" }
        val card = assertIs<Outcome.Card>(runBlocking { pipeline.handle("remind me tomorrow at 7 PM about the recital", t0) })
        assertEquals(emptyList(), proposals.writesOutsideTx)
        assertEquals(emptyList(), ledgerStore.writesOutsideTx)
        assertTrue(tx.committed >= 1, "the pipeline never opened a transaction")
        val stored = proposals.get(card.proposalId)
        assertEquals(Provenance.USER, stored?.provenance)
        assertEquals("bar", stored?.source)
        assertEquals("proposer-v3.7-line", stored?.promptVersion)
        assertEquals("gemma-4-e2b", stored?.modelId)
    }

    @Test
    fun aRefusalIsLedgeredInsideATransactionToo() {
        val tx = RecordingTx()
        val ledgerStore = GuardedLedger(tx, InMemoryLedgerStore())
        val pipeline = CommandPipeline({ _, _ -> "gibberish" }, Gate(), Ledger(ledgerStore), InMemoryProposalStore(), tx) { "p1" }
        assertIs<Outcome.Refused>(runBlocking { pipeline.handle("remind me", t0) })
        assertEquals(emptyList(), ledgerStore.writesOutsideTx)
        assertTrue(tx.committed >= 1)
    }

    @Test
    fun approvingKeepsTheUndoTokenAndTheExecutedEntryInOneTransaction() {
        val tx = RecordingTx()
        val proposals = GuardedProposals(tx, InMemoryProposalStore())
        val ledgerStore = GuardedLedger(tx, InMemoryLedgerStore())
        val ledger = Ledger(ledgerStore, tx)
        val action = ProposedAction("CreateReminder", mapOf("text" to "call Ravi", "time" to "2026-10-24T09:00"))
        tx.run {
            proposals.put(StoredProposal("p1", action, RiskLevel.L0, true, t0, "remind me tomorrow at 9 to call Ravi", null))
            ledger.record("p1", LedgerEvent.PROPOSED, t0, action = action.action, source = "bar")
        }
        val executor = object : ActionExecutor {
            override val handles = "CreateReminder"
            override suspend fun execute(action: ProposedAction) = ExecutionResult.Done("tok")
            override suspend fun undo(action: ProposedAction, undoToken: String?) = true
        }
        val coordinator = ProposalCoordinator(proposals, ledger, Gate(), listOf(executor), undoWindowSeconds = 30, tx = tx)
        runBlocking { coordinator.approve("p1", t0.plusSeconds(5)) }
        assertEquals(emptyList(), proposals.writesOutsideTx)
        assertEquals(emptyList(), ledgerStore.writesOutsideTx)
        assertEquals("tok", proposals.get("p1")?.undoToken)
    }

    @Test
    fun aFailingLedgerWriteInsideTheTransactionDoesNotLeaveAHalfDoneProposal() {
        val failing = object : app.bheem.core.ledger.LedgerStore by InMemoryLedgerStore() {
            override fun append(entry: NewLedgerEntry): app.bheem.core.ledger.LedgerEntry = error("disk full")
        }
        var proposalCommitted = false
        val rollbackTx = object : TransactionRunner {
            override fun <T> run(block: () -> T): T {
                val result = block()
                proposalCommitted = true
                return result
            }
        }
        val pipeline = CommandPipeline({ _, _ -> "CreateReminder|the recital|2026-10-24T19:00" }, Gate(), Ledger(failing), InMemoryProposalStore(), rollbackTx) { "p1" }
        assertFailsWith<IllegalStateException> { runBlocking { pipeline.handle("remind me tomorrow at 7 PM about the recital", t0) } }
        assertEquals(false, proposalCommitted)
    }

    @Test
    fun theFeedIsBuiltFromJoinedRowsWithoutTouchingTheLedger() {
        val proposals = InMemoryProposalStore()
        val ledgerStore = InMemoryLedgerStore()
        val ledger = Ledger(ledgerStore)
        val a = ProposedAction("SaveNote", mapOf("text" to "note a"))
        for (id in listOf("a", "b")) {
            proposals.put(StoredProposal(id, a, RiskLevel.L0, true, t0.plusMinutes(if (id == "a") 0 else 1), "note", "token-$id"))
            ledger.record(id, LedgerEvent.PROPOSED, t0, action = "SaveNote", source = "bar")
        }
        ledger.record("a", LedgerEvent.APPROVED, t0)
        ledger.record("a", LedgerEvent.EXECUTED, t0)
        val rows = InMemoryFeedSource(proposals, ledgerStore).rows(limit = 50)
        assertEquals(2, rows.size)
        val items = Feed.items(rows, t0.plusSeconds(5), undoWindowSeconds = 30)
        assertEquals(listOf("b", "a"), items.map { it.id })
        assertEquals(listOf(FeedStatus.PENDING, FeedStatus.DONE), items.map { it.status })
        assertEquals(true, items.last().canUndo)
    }

    @Test
    fun theFeedSourceHonoursItsLimitAndSkipsProposalsWithNoLedgerEntry() {
        val proposals = InMemoryProposalStore()
        val ledgerStore = InMemoryLedgerStore()
        val a = ProposedAction("SaveNote", mapOf("text" to "n"))
        for (i in 1..5) {
            proposals.put(StoredProposal("p$i", a, RiskLevel.L0, true, t0.plusMinutes(i.toLong()), "n", null))
            if (i != 3) ledgerStore.append(entry("p$i", LedgerEvent.PROPOSED))
        }
        assertEquals(listOf("p5", "p4"), InMemoryFeedSource(proposals, ledgerStore).rows(limit = 2).map { it.proposal.id })
        assertEquals(listOf("p5", "p4", "p2", "p1"), InMemoryFeedSource(proposals, ledgerStore).rows(limit = 10).map { it.proposal.id })
    }

    @Test
    fun memorySearchAndKindQueriesWorkThroughTheStore() {
        val store = InMemoryMemoryStore()
        store.add(MemoryRecord("r1", RecordKind.NOTE, "the scooter key is kept in the cupboard", t0))
        store.add(MemoryRecord("r2", RecordKind.NOTE, "Anita prefers tea without sugar", t0))
        store.add(MemoryRecord("r3", RecordKind.EXPENSE, "Swiggy 380.00", t0, "Swiggy", "380.00", "debit"))
        assertEquals(listOf("r1"), store.search("where did I keep the scooter key", limit = 5).map { it.id })
        assertEquals(listOf("r3"), store.ofKind(RecordKind.EXPENSE).map { it.id })
        assertTrue("cupboard" in Ask.answer("where did I keep the scooter key", store, t0).text)
        assertTrue("380.00" in Ask.answer("what did I spend at Swiggy", store, t0).text)
    }

    @Test
    fun theLedgerChecksAndAppendsInsideItsTransactionWithoutAMonitorOfItsOwn() {
        val tx = RecordingTx()
        val guarded = GuardedLedger(tx, InMemoryLedgerStore())
        val ledger = Ledger(guarded, tx)
        ledger.record("a", LedgerEvent.PROPOSED, t0, action = "CreateReminder", source = "bar")
        assertEquals(emptyList(), guarded.writesOutsideTx)
        assertTrue(tx.committed >= 1)
        assertTrue(java.lang.reflect.Modifier.isSynchronized(Ledger::class.java.getMethod("record", String::class.java, LedgerEvent::class.java, LocalDateTime::class.java, String::class.java, String::class.java, String::class.java).modifiers).not())
    }

    @Test
    fun theStoreListsProposalsWhoseLastEventIsApproved() {
        val store = InMemoryLedgerStore()
        store.append(entry("a", LedgerEvent.PROPOSED))
        store.append(entry("a", LedgerEvent.APPROVED))
        store.append(entry("b", LedgerEvent.PROPOSED))
        store.append(entry("b", LedgerEvent.APPROVED))
        store.append(entry("b", LedgerEvent.EXECUTED))
        store.append(entry("c", LedgerEvent.PROPOSED))
        assertEquals(listOf("a"), store.approvedProposalIds())
    }
}
