package app.bheem.core.pipeline

import app.bheem.core.gate.Gate
import app.bheem.core.gate.GatePolicy
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.proposal.ActionExecutor
import app.bheem.core.proposal.ApprovalResult
import app.bheem.core.proposal.ExecutionResult
import app.bheem.core.proposal.InMemoryProposalStore
import app.bheem.core.proposal.ProposalCoordinator
import app.bheem.core.action.ProposedAction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CallApprovalTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)
    private var saved: String? = "+919876543210"
    private val opened = mutableListOf<ProposedAction>()
    private val proposals = InMemoryProposalStore()
    private val ledger = Ledger(InMemoryLedgerStore())
    private var counter = 0

    private val pipeline = CommandPipeline(Planner { _, _ -> "NoAction" }, Gate(GatePolicy()), ledger, proposals, phones = PhoneLookup { saved }, newId = { "p${counter++}" })

    private val dialer = object : ActionExecutor {
        override val handles = "PlaceCall"

        override suspend fun execute(action: ProposedAction): ExecutionResult = ExecutionResult.Failed("needs an id")

        override suspend fun execute(action: ProposedAction, proposalId: String): ExecutionResult {
            opened += action
            return ExecutionResult.Done(null)
        }

        override suspend fun undo(action: ProposedAction, undoToken: String?) = false
    }

    private fun coordinator() = ProposalCoordinator(
        proposals, ledger, Gate(GatePolicy()), listOf(dialer), 30,
        verify = { a -> if (a.action == "PlaceCall" && a.args["number"] != saved) "the saved number for ${a.args["name"]} has changed or is gone" else null },
    )

    @Test
    fun aCallCardIsApprovedWhenTheSavedNumberIsStillTheOneOnTheCard() = runBlocking<Unit> {
        val card = assertIs<Outcome.Card>(pipeline.handle("call Amma", now))
        assertEquals(ApprovalResult.Executed, coordinator().approve(card.proposalId, now))
        assertEquals(1, opened.size)
    }

    @Test
    fun ifTheNoteChangesBetweenTheCardAndTheTapNothingIsOpened() = runBlocking<Unit> {
        val card = assertIs<Outcome.Card>(pipeline.handle("call Amma", now))
        saved = "+911111111111"
        val refused = assertIs<ApprovalResult.Refused>(coordinator().approve(card.proposalId, now))
        assertTrue(refused.reason.contains("changed or is gone"), refused.reason)
        assertEquals(0, opened.size)
    }

    @Test
    fun ifTheNoteIsDeletedBetweenTheCardAndTheTapNothingIsOpened() = runBlocking<Unit> {
        val card = assertIs<Outcome.Card>(pipeline.handle("call Amma", now))
        saved = null
        assertIs<ApprovalResult.Refused>(coordinator().approve(card.proposalId, now))
        assertEquals(0, opened.size)
    }
}
