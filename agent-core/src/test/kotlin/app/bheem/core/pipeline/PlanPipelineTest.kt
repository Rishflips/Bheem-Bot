package app.bheem.core.pipeline

import app.bheem.core.gate.Gate
import app.bheem.core.gate.GatePolicy
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PlanPipelineTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)
    private var counter = 0
    private val asked = mutableListOf<String>()

    /** A planner that answers each sub-command with the line a model would give. */
    private fun pipeline() = CommandPipeline(
        Planner { text, _ ->
            asked += text
            when {
                text.startsWith("remind me tomorrow at 9") -> "CreateReminder|call Arun|2026-10-24T09:00"
                text.startsWith("note") -> "SaveNote|the invoice is due Friday"
                text.startsWith("add buy milk") -> "CreateTask|buy milk"
                text.startsWith("remind me to call Ravi") -> "CreateReminder|call Ravi|soon"
                else -> "NoAction"
            }
        },
        Gate(GatePolicy()),
        Ledger(InMemoryLedgerStore()),
        newId = { "p${counter++}" },
    )

    @Test
    fun aRequestWithTwoJobsBecomesAPlanOfTwoCardsEachNeedingItsOwnTap() = runBlocking<Unit> {
        val plan = assertIs<Outcome.Plan>(pipeline().handle("remind me tomorrow at 9 to call Arun and note that the invoice is due Friday", now))
        assertEquals(listOf("CreateReminder", "SaveNote"), plan.cards.map { it.action.action })
        assertTrue(plan.cards.all { it.requiresTap })
        assertEquals(2, asked.size)
        assertEquals(plan.cards.map { it.proposalId }, plan.cards.map { it.proposalId }.distinct())
    }

    @Test
    fun aStepTheGateRefusesIsLeftOutAndNamedAndTheOthersStillWaitForTaps() = runBlocking<Unit> {
        val plan = assertIs<Outcome.Plan>(pipeline().handle("note that the invoice is due Friday and remind me to call Ravi", now))
        assertEquals(listOf("SaveNote"), plan.cards.map { it.action.action })
        assertEquals(1, plan.skipped.size)
        assertTrue(plan.skipped.single().contains("call Ravi") && plan.skipped.single().contains("time"), plan.skipped.single())
    }

    @Test
    fun ifEveryStepFailsTheOutcomeIsTheFirstFailureNotAnEmptyPlan() = runBlocking<Unit> {
        val outcome = pipeline().handle("pay Arun 500 rupees and pay Meena 200 rupees", now)
        assertTrue(outcome !is Outcome.Plan, outcome.toString())
    }

    @Test
    fun aCommandMissingItsTimeBecomesAQuestionNotARefusal() = runBlocking<Unit> {
        val outcome = assertIs<Outcome.Clarify>(pipeline().handle("remind me to call Ravi", now))
        assertEquals("What time should I use for that?", outcome.question)
        assertEquals("remind me to call Ravi", outcome.original)
    }
}
