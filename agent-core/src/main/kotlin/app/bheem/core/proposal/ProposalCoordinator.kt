package app.bheem.core.proposal

import app.bheem.core.gate.Gate
import app.bheem.core.gate.GateDecision
import app.bheem.core.gate.GateRequest
import app.bheem.core.gate.Provenance
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.ledger.LedgerResult
import java.time.LocalDateTime

class ProposalCoordinator(
    private val proposals: ProposalStore,
    private val ledger: Ledger,
    private val gate: Gate,
    private val executors: List<ActionExecutor>,
    private val undoWindowSeconds: Long,
    private val tx: TransactionRunner = TransactionRunner.None,
) {
    suspend fun approve(id: String, now: LocalDateTime): ApprovalResult {
        val proposal = proposals.get(id) ?: return ApprovalResult.Refused("unknown proposal")
        val state = ledger.stateOf(id)
        if (state != LedgerEvent.PROPOSED && state != LedgerEvent.FAILED) return ApprovalResult.Refused("proposal is already $state")

        val recheck = gate.decide(GateRequest(proposal.action, proposal.provenance, proposal.userText, proposal.source, now), rateLimited = false)
        if (recheck is GateDecision.Reject) {
            ledger.record(id, LedgerEvent.DISMISSED, now, "no longer valid: ${recheck.reason}")
            if (state == LedgerEvent.FAILED) cleanUp(id, proposal)
            return ApprovalResult.Refused(recheck.reason)
        }
        when (val approved = ledger.record(id, LedgerEvent.APPROVED, now)) {
            is LedgerResult.Refused -> return ApprovalResult.Refused(approved.reason)
            is LedgerResult.Recorded -> Unit
        }

        val executor = executors.firstOrNull { it.handles == proposal.action.action }
            ?: return failed(id, now, "no executor for ${proposal.action.action}")
        val result = try {
            executor.execute(proposal.action, id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            ExecutionResult.Failed("executor failed: ${e.message}")
        }
        return when (result) {
            is ExecutionResult.Failed -> failed(id, now, result.reason)
            is ExecutionResult.Done -> {
                tx.run {
                    proposals.put(proposal.copy(undoToken = result.undoToken))
                    check(ledger.record(id, LedgerEvent.EXECUTED, now, result.note.orEmpty()) is LedgerResult.Recorded) { "the ledger refused EXECUTED for $id" }
                }
                ApprovalResult.Executed
            }
        }
    }

    fun sweepInterrupted(now: LocalDateTime): List<String> =
        ledger.approvedProposals().filter { id ->
            ledger.record(id, LedgerEvent.FAILED, now, "interrupted, check before retrying") is LedgerResult.Recorded
        }

    suspend fun dismiss(id: String, now: LocalDateTime) {
        val wasFailed = ledger.stateOf(id) == LedgerEvent.FAILED
        tx.run { ledger.record(id, LedgerEvent.DISMISSED, now) }
        if (!wasFailed) return
        proposals.get(id)?.let { cleanUp(id, it) }
    }

    private suspend fun cleanUp(id: String, proposal: StoredProposal) {
        val executor = executors.firstOrNull { it.handles == proposal.action.action } ?: return
        try {
            executor.discard(proposal.action, id)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Unit
        }
    }

    suspend fun undo(id: String, now: LocalDateTime): ApprovalResult {
        val proposal = proposals.get(id) ?: return ApprovalResult.Refused("unknown proposal")
        if (!ledger.canUndo(id, now, undoWindowSeconds)) return ApprovalResult.Refused("the undo window is over")
        val executor = executors.firstOrNull { it.handles == proposal.action.action } ?: return ApprovalResult.Failed("no executor for ${proposal.action.action}")
        val worked = try {
            executor.undo(proposal.action, proposal.undoToken)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
        if (!worked) return ApprovalResult.Failed("could not undo")
        return when (val r = tx.run { ledger.record(id, LedgerEvent.UNDONE, now) }) {
            is LedgerResult.Recorded -> ApprovalResult.Undone
            is LedgerResult.Refused -> ApprovalResult.Refused(r.reason)
        }
    }

    private fun failed(id: String, now: LocalDateTime, reason: String): ApprovalResult {
        tx.run { ledger.record(id, LedgerEvent.FAILED, now, reason) }
        return ApprovalResult.Failed(reason)
    }
}
