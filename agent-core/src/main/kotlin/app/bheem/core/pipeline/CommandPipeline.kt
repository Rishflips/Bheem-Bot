package app.bheem.core.pipeline

import app.bheem.core.action.ActionCatalog
import app.bheem.core.action.ParseResult
import app.bheem.core.action.ProposedAction
import app.bheem.core.assistant.CompoundSplitter
import app.bheem.core.gate.Gate
import app.bheem.core.gate.GateDecision
import app.bheem.core.gate.GateRequest
import app.bheem.core.gate.Provenance
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.ledger.LedgerResult
import app.bheem.core.parse.AnswerFormats
import app.bheem.core.parse.AnswerParser
import app.bheem.core.planner.PreRouter
import app.bheem.core.privacy.Redact
import app.bheem.core.proposal.InMemoryProposalStore
import app.bheem.core.proposal.ProposalMeta
import app.bheem.core.proposal.ProposalStore
import app.bheem.core.proposal.StoredProposal
import app.bheem.core.proposal.TransactionRunner
import app.bheem.core.time.TimeReconciler
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

fun interface Planner {
    suspend fun plan(userText: String, now: LocalDateTime): String
}

/** Finds the number the owner saved for a name. Resolved by code from the owner's own notes; the model never supplies one. */
fun interface PhoneLookup {
    fun find(name: String): String?
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

    data class ModelUnavailable(val reason: String) : Outcome

    /** Several jobs in one sentence: one card per job, each with its own tap. [skipped] names the jobs that could not become a card and why. */
    data class Plan(val planId: String, val cards: List<Card>, val skipped: List<String>) : Outcome

    /** The command is clear except for one thing the owner has to say; [original] is the sentence to complete with the answer. */
    data class Clarify(val question: String, val original: String) : Outcome
}

class CommandPipeline(
    private val planner: Planner,
    private val gate: Gate,
    private val ledger: Ledger,
    private val proposals: ProposalStore = InMemoryProposalStore(),
    private val tx: TransactionRunner = TransactionRunner.None,
    private val meta: () -> ProposalMeta = { ProposalMeta("", "", "") },
    private val parser: AnswerParser = AnswerFormats.LINE,
    private val phones: PhoneLookup? = null,
    private val newId: () -> String,
) {
    suspend fun handle(userText: String, now: LocalDateTime, source: String = "bar"): Outcome {
        if (phones != null) {
            PreRouter.callTarget(userText)?.let { name ->
                val number = phones.find(name) ?: return refused("I have no number saved for $name. Save one with: note $name's number is ...", "PlaceCall", source, now)
                return decide(ProposedAction("PlaceCall", mapOf("name" to name, "number" to number)), userText, now, source)
            }
            PreRouter.routeTarget(userText)?.let { return decide(ProposedAction("OpenRoute", mapOf("destination" to it)), userText, now, source) }
        }
        when (PreRouter.route(userText)) {
            PreRouter.Route.NoAction -> return Outcome.Nothing
            PreRouter.Route.Memory -> {
                val id = newId()
                tx.run { ledger.record(id, LedgerEvent.READ, now, "route=memory", "AnswerFromMemory", source) }
                return Outcome.Question(id, userText)
            }
            PreRouter.Route.Payment -> return refused("payments come later", "OpenUpiPayment", source, now)
            PreRouter.Route.Model -> Unit
        }
        CompoundSplitter.split(userText)?.let { parts -> return plan(parts, now, source) }
        val raw = try {
            planner.plan(userText, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: app.bheem.models.host.ModelUnavailable) {
            return Outcome.ModelUnavailable(e.message.orEmpty())
        } catch (e: Exception) {
            return refused("planner failed (${e::class.java.simpleName.ifEmpty { "error" }})", "Unreadable", source, now)
        }
        val proposal = when (val parsed = parser.parse(raw)) {
            is ParseResult.Invalid -> return refused(parsed.reason, "Unreadable", source, now)
            is ParseResult.Valid -> parsed.action
        }
        if (proposal.action == "NoAction") return Outcome.Nothing
        if (proposal.action in ActionCatalog.codeOnly) return refused("${proposal.action} is not offered to the model", proposal.action, source, now)
        return decide(proposal, userText, now, source)
    }

    private fun decide(proposal: ProposedAction, userText: String, now: LocalDateTime, source: String): Outcome {
        val (repaired, correction) = repairedTime(proposal, userText, now) ?: return missingTime(userText, proposal.action, source, now)
        return when (val decision = gate.decide(GateRequest(repaired, Provenance.USER, userText, source, now))) {
            is GateDecision.Reject -> refused(decision.reason, proposal.action, source, now)
            GateDecision.Nothing -> Outcome.Nothing
            is GateDecision.Propose -> {
                val id = newId()
                val detail = listOfNotNull(correction, "args=${decision.action.args}").joinToString("; ")
                if (decision.level == RiskLevel.READ) {
                    tx.run { ledger.record(id, LedgerEvent.READ, now, detail, decision.action.action, source) }
                    Outcome.Question(id, decision.action.args.getValue("query"))
                } else {
                    val info = meta()
                    tx.run {
                        check(ledger.record(id, LedgerEvent.PROPOSED, now, detail, decision.action.action, source) is LedgerResult.Recorded) { "the ledger refused PROPOSED for $id" }
                        proposals.put(
                            StoredProposal(
                                id, decision.action, decision.level, decision.requiresTap, now, userText, null,
                                Provenance.USER, source, info.promptVersion, info.modelId, info.backend,
                            ),
                        )
                    }
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

    private suspend fun plan(parts: List<String>, now: LocalDateTime, source: String): Outcome {
        val cards = mutableListOf<Outcome.Card>()
        val skipped = mutableListOf<String>()
        var firstFailure: Outcome? = null
        for (part in parts) {
            when (val outcome = handle(part, now, source)) {
                is Outcome.Card -> cards += outcome
                is Outcome.ModelUnavailable -> return outcome
                is Outcome.Refused -> { skipped += "$part: ${outcome.reason}"; firstFailure = firstFailure ?: outcome }
                is Outcome.Clarify -> { skipped += "$part: ${outcome.question}"; firstFailure = firstFailure ?: outcome }
                is Outcome.Question -> { skipped += "$part: ask that on its own"; firstFailure = firstFailure ?: outcome }
                Outcome.Nothing -> { skipped += "$part: I can't help with that yet"; firstFailure = firstFailure ?: outcome }
                is Outcome.Plan -> Unit
            }
        }
        if (cards.isEmpty()) return firstFailure ?: Outcome.Nothing
        if (cards.size == 1 && skipped.isEmpty()) return cards.single()
        return Outcome.Plan(newId(), cards, skipped)
    }

    private fun missingTime(userText: String, action: String, source: String, now: LocalDateTime): Outcome {
        tx.run { ledger.record(newId(), LedgerEvent.REJECTED, now, "the time is missing or unclear", action, source) }
        return Outcome.Clarify("What time should I use for that?", userText)
    }

    private fun refused(reason: String, action: String, source: String, now: LocalDateTime): Outcome {
        val safe = Redact.reason(reason)
        tx.run { ledger.record(newId(), LedgerEvent.REJECTED, now, safe, action, source) }
        return Outcome.Refused(safe)
    }

    private companion object {
        val timeKeys = mapOf("CreateReminder" to "time", "CreateCalendarEvent" to "start")
    }
}
