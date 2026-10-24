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
) {
    suspend fun approve(id: String, now: LocalDateTime): ApprovalResult {
        val proposal = proposals.get(id) ?: return ApprovalResult.Refused("unknown proposal")
        val state = ledger.stateOf(id)
        if (state != LedgerEvent.PROPOSED && state != LedgerEvent.FAILED) return ApprovalResult.Refused("proposal is already $state")

        val recheck = gate.decide(GateRequest(proposal.action, Provenance.USER, proposal.userText, "recheck", now))
        if (recheck is GateDecision.Reject) {
            ledger.record(id, LedgerEvent.DISMISSED, now, "no longer valid: ${recheck.reason}")
            return ApprovalResult.Refused(recheck.reason)
        }
        ledger.record(id, LedgerEvent.APPROVED, now)

        val executor = executors.firstOrNull { it.handles == proposal.action.action }
            ?: return failed(id, now, "no executor for ${proposal.action.action}")
        val result = try {
            executor.execute(proposal.action)
        } catch (e: Exception) {
            ExecutionResult.Failed("executor failed: ${e.message}")
        }
        return when (result) {
            is ExecutionResult.Failed -> failed(id, now, result.reason)
            is ExecutionResult.Done -> {
                proposals.put(proposal.copy(undoToken = result.undoToken))
                ledger.record(id, LedgerEvent.EXECUTED, now)
                ApprovalResult.Executed
            }
        }
    }

    fun dismiss(id: String, now: LocalDateTime) {
        ledger.record(id, LedgerEvent.DISMISSED, now)
    }

    suspend fun undo(id: String, now: LocalDateTime): ApprovalResult {
        val proposal = proposals.get(id) ?: return ApprovalResult.Refused("unknown proposal")
        if (!ledger.canUndo(id, now, undoWindowSeconds)) return ApprovalResult.Refused("the undo window is over")
        val executor = executors.firstOrNull { it.handles == proposal.action.action } ?: return ApprovalResult.Failed("no executor for ${proposal.action.action}")
        val worked = try {
            executor.undo(proposal.action, proposal.undoToken)
        } catch (e: Exception) {
            false
        }
        if (!worked) return ApprovalResult.Failed("could not undo")
        return when (val r = ledger.record(id, LedgerEvent.UNDONE, now)) {
            is LedgerResult.Recorded -> ApprovalResult.Undone
            is LedgerResult.Refused -> ApprovalResult.Refused(r.reason)
        }
    }

    private fun failed(id: String, now: LocalDateTime, reason: String): ApprovalResult {
        ledger.record(id, LedgerEvent.FAILED, now, reason)
        return ApprovalResult.Failed(reason)
    }
}
