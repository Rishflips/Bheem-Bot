package app.bheem.core.assistant

import app.bheem.core.proposal.ApprovalResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class AssistantFlowTest {
    @Test
    fun aRequestWithTwoJobsIsSplitAtTheJoiningWord() {
        assertEquals(
            listOf("remind me tomorrow at 9 to call Arun", "note that the invoice is due Friday"),
            CompoundSplitter.split("remind me tomorrow at 9 to call Arun and note that the invoice is due Friday"),
        )
        assertEquals(
            listOf("add buy milk to my tasks", "remind me at 5 pm to call Meena", "tell Ravi I am late"),
            CompoundSplitter.split("add buy milk to my tasks, then remind me at 5 pm to call Meena and also tell Ravi I am late"),
        )
    }

    @Test
    fun aSingleJobOrANonJobAfterAndIsNotSplit() {
        assertNull(CompoundSplitter.split("remind me to buy salt and pepper"))
        assertNull(CompoundSplitter.split("tell Ravi that Arun and Meena are late"))
        assertNull(CompoundSplitter.split("remind me to call Arun"))
    }

    @Test
    fun atMostFiveJobsAreAcceptedAndMoreIsNotSplit() {
        val six = (1..6).joinToString(" and ") { "note item $it" }
        assertNull(CompoundSplitter.split(six))
        val five = (1..5).joinToString(" and ") { "note item $it" }
        assertEquals(5, CompoundSplitter.split(five)?.size)
    }

    @Test
    fun aShortTimeAnswerCompletesTheCommandThatWasWaitingForOne() {
        assertEquals("remind me to call Arun 5 pm", Clarifier.combine("remind me to call Arun", "5 pm"))
        assertEquals("remind me to call Arun tomorrow evening", Clarifier.combine("remind me to call Arun", "tomorrow evening"))
    }

    @Test
    fun aReplyThatIsAnotherCommandOrTooLongIsNotAnAnswer() {
        assertNull(Clarifier.combine("remind me to call Arun", "note that the gas bill is due"))
        assertNull(Clarifier.combine("remind me to call Arun", "remind me tomorrow at 9 to call Ravi about the long pending invoice"))
        assertNull(Clarifier.combine("remind me to call Arun", "what is on tomorrow"))
    }

    @Test
    fun yesAndItsPlainRelativesAreConfirmationsAndNothingElseIs() {
        for (text in listOf("yes", "Yes.", "ok", "okay", "do it", "go ahead", "confirm", "yes please", "do all", "sure")) assertTrue(FollowUp.isConfirmation(text), text)
        for (text in listOf("yes remind me tomorrow", "no", "maybe", "pay Arun", "")) assertFalse(FollowUp.isConfirmation(text), text)
    }

    @Test
    fun doAllApprovesTheStepsInOrderAndStopsAfterTheFirstHandOff() = runBlocking<Unit> {
        val seen = mutableListOf<String>()
        val runner = PlanRunner(approve = { id -> seen += id; ApprovalResult.Executed }, isHandOff = { it == "b" })
        val result = runner.approveAll(listOf("a", "b", "c"))
        assertEquals(listOf("a", "b"), seen)
        assertEquals(PlanResult(done = listOf("a", "b"), stoppedAt = "c", reason = "waiting for you after a hand-off"), result)
    }

    @Test
    fun doAllStopsAtTheFirstFailureAndSaysWhy() = runBlocking<Unit> {
        val runner = PlanRunner(approve = { id -> if (id == "b") ApprovalResult.Failed("no calendar app") else ApprovalResult.Executed }, isHandOff = { false })
        val result = runner.approveAll(listOf("a", "b", "c"))
        assertEquals(listOf("a"), result.done)
        assertEquals("b", result.stoppedAt)
        assertEquals("no calendar app", result.reason)
    }

    @Test
    fun doAllCompletesWhenNothingStopsIt() = runBlocking<Unit> {
        val result = PlanRunner(approve = { ApprovalResult.Executed }, isHandOff = { false }).approveAll(listOf("a", "b"))
        assertEquals(listOf("a", "b"), result.done)
        assertNull(result.stoppedAt)
    }
}
