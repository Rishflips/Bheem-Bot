package app.bheem.core.assistant

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.RiskLevel
import app.bheem.core.pipeline.Outcome
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnswerRoutingTest {
    private fun card() = Outcome.Card(
        "p1",
        ProposedAction("CreateReminder", mapOf("text" to "call Amma", "time" to "2026-11-05T17:00")),
        RiskLevel.L0,
        requiresTap = true,
        timeCorrected = false,
    )

    @Test
    fun aCommandIsOnlySomethingThePipelineWouldPutInFrontOfTheOwner() {
        assertTrue(AnswerRouting.isCommand(card()))
        assertTrue(AnswerRouting.isCommand(Outcome.Plan("plan", listOf(card()), emptyList())))
        assertTrue(AnswerRouting.isCommand(Outcome.Clarify("What time?", "remind me to call")))
        assertFalse(AnswerRouting.isCommand(Outcome.Nothing))
        assertFalse(AnswerRouting.isCommand(Outcome.Refused("no")))
        assertFalse(AnswerRouting.isCommand(Outcome.Question("q", "my notes")))
        assertFalse(AnswerRouting.isCommand(Outcome.ModelUnavailable("not yet")))
    }

    @Test
    fun aPendingGapQuestionOwnsOnlyMessagesNothingElseAnswers() {
        assertTrue(AnswerRouting.isGapAnswer(null, Outcome.Nothing))
        assertFalse(AnswerRouting.isGapAnswer("It is 9:00 AM.", Outcome.Nothing))
        assertFalse(AnswerRouting.isGapAnswer(null, card()))
        assertFalse(AnswerRouting.isGapAnswer(null, Outcome.Clarify("What time?", "remind me")))
        assertFalse(AnswerRouting.isGapAnswer(null, Outcome.Refused("no")))
    }
}
