package app.bheem.core.pipeline

import app.bheem.core.gate.Gate
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.proposal.InMemoryProposalStore
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CommandPipelineTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val store = InMemoryLedgerStore()
    private var counter = 0
    private val proposals = InMemoryProposalStore()

    private fun run(userText: String, modelSays: String): Outcome {
        val pipeline = CommandPipeline({ _, _ -> modelSays }, Gate(), Ledger(store), proposals) { "p${++counter}" }
        return runBlocking { pipeline.handle(userText, now) }
    }

    private fun events() = store.all().map { it.event }

    @Test
    fun aGoodLineBecomesACardAndIsLedgered() {
        val card = assertIs<Outcome.Card>(run("remind me tomorrow at 7 PM about the recital", "CreateReminder|the recital|2026-10-24T19:00"))
        assertEquals("p1", card.proposalId)
        assertEquals("CreateReminder", card.action.action)
        assertEquals(RiskLevel.L0, card.level)
        assertTrue(card.requiresTap)
        assertEquals(listOf(LedgerEvent.PROPOSED), events())
    }

    @Test
    fun aCardIsStoredWithTheUsersTextSoItCanBeApprovedLater() {
        val card = assertIs<Outcome.Card>(run("remind me tomorrow at 7 PM about the recital", "CreateReminder|the recital|2026-10-24T19:00"))
        val stored = proposals.get(card.proposalId)
        assertEquals(card.action, stored?.action)
        assertEquals("remind me tomorrow at 7 PM about the recital", stored?.userText)
        assertEquals(null, stored?.undoToken)
    }

    @Test
    fun refusalsAndQuestionsStoreNothing() {
        run("call my mother", "DraftMessage|whatsapp|mother|I miss you")
        run("where did I keep the key", "AnswerFromMemory|key")
        assertEquals(emptyList(), proposals.all())
    }

    @Test
    fun theUsersWordsFixAWrongModelDateAndTheCardSaysSo() {
        val card = assertIs<Outcome.Card>(run("set a reminder for next Monday at 8 AM to submit the timesheet", "CreateReminder|submit the timesheet|2026-10-28T08:00"))
        assertEquals("2026-10-26T08:00", card.action.args["time"])
        assertTrue(card.timeCorrected)
        assertTrue("time" in store.all().single().detail)
    }

    @Test
    fun aCalendarEventGetsTheSameTimeRepair() {
        val card = assertIs<Outcome.Card>(run("book a call with Suresh next Monday at 11 AM", "CreateCalendarEvent|call with Suresh|2026-10-28T11:00:00"))
        assertEquals("2026-10-26T11:00", card.action.args["start"])
    }

    @Test
    fun unreadableModelOutputIsRefusedAndLedgered() {
        val refused = assertIs<Outcome.Refused>(run("remind me to call", "I don't have access to your task list"))
        assertTrue("unknown action" in refused.reason)
        assertEquals(listOf(LedgerEvent.REJECTED), events())
    }

    @Test
    fun anInventedMessageIsRefusedBeforeItReachesTheUser() {
        val refused = assertIs<Outcome.Refused>(run("call my mother", "DraftMessage|whatsapp|mother|I miss you, thinking of you."))
        assertTrue("not from the command" in refused.reason)
        assertEquals(listOf(LedgerEvent.REJECTED), events())
    }

    @Test
    fun aReminderWithNoTimeAnywhereAsksInsteadOfGuessing() {
        val refused = assertIs<Outcome.Refused>(run("remind me to send the invoice", "CreateReminder|send the invoice|soon"))
        assertTrue("time" in refused.reason)
    }

    @Test
    fun aTimeInThePastIsRefusedByTheGate() {
        val refused = assertIs<Outcome.Refused>(run("remind me today at 6 AM to stretch", "CreateReminder|stretch|2026-10-23T06:00"))
        assertTrue("past" in refused.reason)
    }

    @Test
    fun noActionDoesNothingAndLeavesNoLedgerEntry() {
        assertEquals(Outcome.Nothing, run("tell me a joke", "NoAction"))
        assertEquals(emptyList(), events())
    }

    @Test
    fun aQuestionIsAReadAndNeedsNoTap() {
        val ask = assertIs<Outcome.Question>(run("where did I keep the scooter key", "AnswerFromMemory|scooter key"))
        assertEquals("scooter key", ask.query)
        assertEquals(listOf(LedgerEvent.READ), events())
    }

    @Test
    fun aPlannerThatThrowsGivesARefusalNotACrash() {
        val pipeline = CommandPipeline({ _, _ -> error("engine failed") }, Gate(), Ledger(store)) { "p1" }
        val refused = assertIs<Outcome.Refused>(runBlocking { pipeline.handle("remind me to call", now) })
        assertTrue("engine failed" in refused.reason)
    }

    @Test
    fun aCancelledPlannerIsNotTurnedIntoARefusal() {
        val pipeline = CommandPipeline({ _, _ -> throw kotlinx.coroutines.CancellationException("cancelled") }, Gate(), Ledger(store), proposals) { "p1" }
        assertFailsWith<kotlinx.coroutines.CancellationException> { runBlocking { pipeline.handle("remind me to call", now) } }
        assertEquals(emptyList(), events())
    }
}
