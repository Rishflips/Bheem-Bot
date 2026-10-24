package app.bheem.core.proposal

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.Gate
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ProposalCoordinatorTest {
    private val t0 = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val ledgerStore = InMemoryLedgerStore()
    private val ledger = Ledger(ledgerStore)
    private val proposals = InMemoryProposalStore()
    private val reminder = ProposedAction("CreateReminder", mapOf("text" to "call Ravi", "time" to "2026-10-24T09:00"))

    private class FakeExecutor(override val handles: String, private val result: ExecutionResult = ExecutionResult.Done("tok-1")) : ActionExecutor {
        val executed = mutableListOf<ProposedAction>()
        val undone = mutableListOf<String?>()
        var undoWorks = true

        override suspend fun execute(action: ProposedAction): ExecutionResult {
            executed += action
            return result
        }

        override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean {
            undone += undoToken
            return undoWorks
        }
    }

    private fun coordinator(vararg executors: ActionExecutor) = ProposalCoordinator(proposals, ledger, Gate(), executors.toList(), undoWindowSeconds = 30)

    private fun open(id: String = "p1", action: ProposedAction = reminder) {
        proposals.put(StoredProposal(id, action, RiskLevel.L0, true, t0, "remind me tomorrow at 9 to call Ravi", null))
        ledger.record(id, LedgerEvent.PROPOSED, t0, action = action.action, source = "bar")
    }

    private fun run(block: suspend () -> ApprovalResult) = runBlocking { block() }

    @Test
    fun approvingRunsTheExecutorRecordsItAndKeepsTheUndoToken() {
        val exec = FakeExecutor("CreateReminder")
        open()
        val result = run { coordinator(exec).approve("p1", t0.plusSeconds(5)) }
        assertEquals(ApprovalResult.Executed, result)
        assertEquals(listOf(reminder), exec.executed)
        assertEquals(listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED, LedgerEvent.EXECUTED), ledger.history("p1").map { it.event })
        assertEquals("tok-1", proposals.get("p1")?.undoToken)
    }

    @Test
    fun anExecutorFailureIsLedgeredAndTheProposalCanBeRetried() {
        val broken = FakeExecutor("CreateReminder", ExecutionResult.Failed("no alarm permission"))
        open()
        val result = run { coordinator(broken).approve("p1", t0) }
        assertEquals(ApprovalResult.Failed("no alarm permission"), result)
        assertEquals(LedgerEvent.FAILED, ledger.stateOf("p1"))
        val fixed = FakeExecutor("CreateReminder")
        assertEquals(ApprovalResult.Executed, run { coordinator(fixed).approve("p1", t0.plusSeconds(10)) })
        assertEquals(LedgerEvent.EXECUTED, ledger.stateOf("p1"))
    }

    @Test
    fun anExecutorThatThrowsIsTreatedAsAFailure() {
        val throwing = object : ActionExecutor {
            override val handles = "CreateReminder"
            override suspend fun execute(action: ProposedAction): ExecutionResult = error("boom")
            override suspend fun undo(action: ProposedAction, undoToken: String?) = false
        }
        open()
        val result = assertIs<ApprovalResult.Failed>(run { coordinator(throwing).approve("p1", t0) })
        assertTrue("boom" in result.reason)
        assertEquals(LedgerEvent.FAILED, ledger.stateOf("p1"))
    }

    @Test
    fun anActionWithNoExecutorFailsCleanly() {
        open()
        val result = assertIs<ApprovalResult.Failed>(run { coordinator(FakeExecutor("SaveNote")).approve("p1", t0) })
        assertTrue("no executor" in result.reason)
    }

    @Test
    fun anUnknownOrFinishedProposalIsRefusedAndRunsNothing() {
        val exec = FakeExecutor("CreateReminder")
        assertIs<ApprovalResult.Refused>(run { coordinator(exec).approve("nope", t0) })
        open()
        run { coordinator(exec).approve("p1", t0) }
        assertIs<ApprovalResult.Refused>(run { coordinator(exec).approve("p1", t0) })
        assertEquals(1, exec.executed.size)
    }

    @Test
    fun aReminderThatHasGoneStaleWhileWaitingIsNotRunAndIsDismissed() {
        val exec = FakeExecutor("CreateReminder")
        open()
        val result = assertIs<ApprovalResult.Refused>(run { coordinator(exec).approve("p1", LocalDateTime.of(2026, 10, 24, 9, 30)) })
        assertTrue("past" in result.reason)
        assertEquals(emptyList(), exec.executed)
        assertEquals(LedgerEvent.DISMISSED, ledger.stateOf("p1"))
    }

    @Test
    fun dismissingEndsTheProposalWithoutRunningIt() {
        val exec = FakeExecutor("CreateReminder")
        open()
        coordinator(exec).dismiss("p1", t0)
        assertEquals(LedgerEvent.DISMISSED, ledger.stateOf("p1"))
        assertEquals(emptyList(), exec.executed)
    }

    @Test
    fun undoWithinTheWindowCallsTheExecutorWithItsToken() {
        val exec = FakeExecutor("CreateReminder")
        open()
        run { coordinator(exec).approve("p1", t0) }
        assertEquals(ApprovalResult.Undone, run { coordinator(exec).undo("p1", t0.plusSeconds(20)) })
        assertEquals(listOf<String?>("tok-1"), exec.undone)
        assertEquals(LedgerEvent.UNDONE, ledger.stateOf("p1"))
    }

    @Test
    fun undoAfterTheWindowIsRefusedAndAFailedUndoKeepsTheExecutedState() {
        val exec = FakeExecutor("CreateReminder")
        open()
        run { coordinator(exec).approve("p1", t0) }
        assertTrue("window" in assertIs<ApprovalResult.Refused>(run { coordinator(exec).undo("p1", t0.plusSeconds(31)) }).reason)
        exec.undoWorks = false
        assertIs<ApprovalResult.Failed>(run { coordinator(exec).undo("p1", t0.plusSeconds(10)) })
        assertEquals(LedgerEvent.EXECUTED, ledger.stateOf("p1"))
        assertNull(proposals.get("missing"))
    }

    private class BarrierProposals(private val inner: InMemoryProposalStore) : ProposalStore by inner {
        private val barrier = java.util.concurrent.CyclicBarrier(2)

        override fun get(id: String): StoredProposal? {
            val found = inner.get(id)
            runCatching { barrier.await(2, java.util.concurrent.TimeUnit.SECONDS) }
            return found
        }
    }

    @Test
    fun twoApprovesThatBothPassTheStateCheckStillRunTheExecutorOnce() {
        val slow = object : ActionExecutor {
            val runs = java.util.concurrent.atomic.AtomicInteger()
            override val handles = "CreateReminder"
            override suspend fun execute(action: ProposedAction): ExecutionResult {
                runs.incrementAndGet()
                delay(100)
                return ExecutionResult.Done("tok")
            }
            override suspend fun undo(action: ProposedAction, undoToken: String?) = true
        }
        open()
        val results = runBlocking {
            val c = ProposalCoordinator(BarrierProposals(proposals), ledger, Gate(), listOf(slow), undoWindowSeconds = 30)
            val a = async(Dispatchers.Default) { c.approve("p1", t0) }
            val b = async(Dispatchers.Default) { c.approve("p1", t0) }
            listOf(a.await(), b.await())
        }
        assertEquals(1, slow.runs.get())
        assertEquals(1, results.count { it == ApprovalResult.Executed })
        assertEquals(1, results.count { it is ApprovalResult.Refused })
        assertEquals(listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED, LedgerEvent.EXECUTED), ledger.history("p1").map { it.event })
    }

    @Test
    fun aRateLimitedGateNeverCostsTheUserAnApproval() {
        val limiter = app.bheem.core.gate.SourceRateLimiter(maxPerWindow = 1, windowSeconds = 60)
        val gate = Gate(limiter = limiter)
        limiter.tryAcquire("bar", t0)
        val exec = FakeExecutor("CreateReminder")
        open()
        val result = run { ProposalCoordinator(proposals, ledger, gate, listOf(exec), undoWindowSeconds = 30).approve("p1", t0) }
        assertEquals(ApprovalResult.Executed, result)
        assertEquals(1, exec.executed.size)
    }

    @Test
    fun anApprovalInterruptedByAProcessDeathIsMarkedFailedOnTheNextStart() {
        open("p1")
        open("p2")
        ledger.record("p1", LedgerEvent.APPROVED, t0)
        val swept = coordinator().sweepInterrupted(t0.plusMinutes(5))
        assertEquals(listOf("p1"), swept)
        assertEquals(LedgerEvent.FAILED, ledger.stateOf("p1"))
        assertEquals(LedgerEvent.PROPOSED, ledger.stateOf("p2"))
        assertTrue("interrupted" in ledger.history("p1").last().detail)
    }

    @Test
    fun aStoredUntrustedProposalIsCheckedAsUntrustedWhenApproved() {
        val exec = FakeExecutor("DraftMessage")
        val draft = ProposedAction("DraftMessage", mapOf("channel" to "whatsapp", "to" to "Arun", "text" to "hello"))
        proposals.put(StoredProposal("t1", draft, RiskLevel.L1, true, t0, "hello Arun", null, app.bheem.core.gate.Provenance.TAINTED, "notification"))
        ledger.record("t1", LedgerEvent.PROPOSED, t0, action = "DraftMessage", source = "notification")
        val result = assertIs<ApprovalResult.Refused>(run { coordinator(exec).approve("t1", t0) })
        assertTrue("not allowed" in result.reason, result.reason)
        assertEquals(emptyList(), exec.executed)
        assertEquals(LedgerEvent.DISMISSED, ledger.stateOf("t1"))
    }
}
