package app.bheem.core.pipeline

import app.bheem.core.action.ParseResult
import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.Gate
import app.bheem.core.gate.GateDecision
import app.bheem.core.gate.GateRequest
import app.bheem.core.gate.Provenance
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.parse.LineParser
import app.bheem.core.proposal.InMemoryProposalStore
import app.bheem.core.proposal.ProposalStore
import app.bheem.core.proposal.StoredProposal
import app.bheem.core.time.TimeReconciler
import java.time.LocalDateTime

fun interface Planner {
    suspend fun plan(userText: String, now: LocalDateTime): String
}

sealed interface Outcome {
    data class Card(
        val proposalId: String,
        val action: ProposedAction,
        val level: RiskLevel,
        val requiresTap: Boolean,
        val timeCorrected: Boolean,
    ) : Outcome

    data class Question(val proposalId: String, val query: String) : Outcome

    data object Nothing : Outcome

    data class Refused(val reason: String) : Outcome
}

class CommandPipeline(
    private val planner: Planner,
    private val gate: Gate,
    private val ledger: Ledger,
    private val proposals: ProposalStore = InMemoryProposalStore(),
    private val newId: () -> String,
) {
    suspend fun handle(userText: String, now: LocalDateTime, source: String = "bar"): Outcome {
        val raw = try {
            planner.plan(userText, now)
        } catch (e: Exception) {
            return refused("planner failed: ${e.message}", "Unreadable", source, now)
        }
        val proposal = when (val parsed = LineParser.parse(raw)) {
            is ParseResult.Invalid -> return refused(parsed.reason, "Unreadable", source, now)
            is ParseResult.Valid -> parsed.action
        }
        if (proposal.action == "NoAction") return Outcome.Nothing

        val (repaired, correction) = repairedTime(proposal, userText, now) ?: return refused("the time is missing or unclear", proposal.action, source, now)
        return when (val decision = gate.decide(GateRequest(repaired, Provenance.USER, userText, source, now))) {
            is GateDecision.Reject -> refused(decision.reason, proposal.action, source, now)
            GateDecision.Nothing -> Outcome.Nothing
            is GateDecision.Propose -> {
                val id = newId()
                val detail = listOfNotNull(correction, "args=${decision.action.args}").joinToString("; ")
                if (decision.level == RiskLevel.READ) {
                    ledger.record(id, LedgerEvent.READ, now, detail, decision.action.action, source)
                    Outcome.Question(id, decision.action.args.getValue("query"))
                } else {
                    ledger.record(id, LedgerEvent.PROPOSED, now, detail, decision.action.action, source)
                    proposals.put(StoredProposal(id, decision.action, decision.level, decision.requiresTap, now, userText, null))
                    Outcome.Card(id, decision.action, decision.level, decision.requiresTap, correction != null)
                }
            }
        }
    }

    private fun repairedTime(proposal: ProposedAction, userText: String, now: LocalDateTime): Pair<ProposedAction, String?>? {
        val key = timeKeys[proposal.action] ?: return proposal to null
        val modelTime = proposal.args[key]
        val r = TimeReconciler.reconcile(userText, modelTime, now)
        val value = r.value ?: return null
        val correction = if (r.corrected) "time corrected from $modelTime to $value" else null
        return ProposedAction(proposal.action, proposal.args + (key to value.toString())) to correction
    }

    private fun refused(reason: String, action: String, source: String, now: LocalDateTime): Outcome {
        ledger.record(newId(), LedgerEvent.REJECTED, now, reason, action, source)
        return Outcome.Refused(reason)
    }

    private companion object {
        val timeKeys = mapOf("CreateReminder" to "time", "CreateCalendarEvent" to "start")
    }
}
