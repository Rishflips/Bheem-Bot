package app.bheem.core.gate

import app.bheem.core.action.ActionCatalog
import app.bheem.core.action.ProposedAction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Text that did not come from the person (a notification, a shared message) may only ever propose a small set of actions, and only behind a tap. */
class TaintedGateTest {
    private val now = LocalDateTime.of(2026, 10, 27, 9, 0)
    private val gate = Gate()

    private val valid = mapOf(
        "LogExpense" to mapOf("amount" to "90", "merchant" to "Chai Point", "direction" to "debit"),
        "CreateReminder" to mapOf("text" to "call Ravi", "time" to "2026-10-28T09:00"),
        "CreateTask" to mapOf("text" to "renew the passport"),
        "CreateCalendarEvent" to mapOf("title" to "dentist", "start" to "2026-10-28T16:00"),
        "DraftMessage" to mapOf("channel" to "whatsapp", "to" to "Arun", "text" to "I will be late"),
        "OpenUpiPayment" to mapOf("payee" to "attacker@upi", "amount" to "5000"),
        "SaveNote" to mapOf("text" to "locker number 27"),
        "AnswerFromMemory" to mapOf("query" to "where is the scooter key"),
    )

    private fun decide(action: String, provenance: Provenance) =
        gate.decide(GateRequest(ProposedAction(action, valid.getValue(action)), provenance, "message Arun I will be late", "notification", now))

    @Test
    fun everyActionTypeHasAValidExampleSoNoneIsSkipped() {
        assertEquals(ActionCatalog.names - "NoAction", valid.keys)
    }

    @Test
    fun fromUntrustedTextOnlyAnExpenseLogAndAReminderCanBeProposed() {
        val allowed = valid.keys.filter { decide(it, Provenance.TAINTED) is GateDecision.Propose }.toSet()
        assertEquals(setOf("LogExpense", "CreateReminder"), allowed)
    }

    @Test
    fun everyOtherActionIsRejectedFromUntrustedTextAndSaysWhy() {
        for (action in valid.keys - setOf("LogExpense", "CreateReminder")) {
            val decision = decide(action, Provenance.TAINTED)
            assertIs<GateDecision.Reject>(decision, action)
            assertTrue(decision.reason.isNotBlank(), action)
        }
    }

    @Test
    fun anAllowedActionFromUntrustedTextIsCappedAtL1AndAlwaysWaitsForATap() {
        for (action in listOf("LogExpense", "CreateReminder")) {
            val proposed = assertIs<GateDecision.Propose>(decide(action, Provenance.TAINTED), action)
            assertEquals(RiskLevel.L1, proposed.level, action)
            assertTrue(proposed.requiresTap, action)
        }
    }

    @Test
    fun theSameActionsFromTheUserStayAtL0AndStillWaitForATapWhileTheDefaultPolicyHolds() {
        for (action in listOf("LogExpense", "CreateReminder")) {
            val proposed = assertIs<GateDecision.Propose>(decide(action, Provenance.USER), action)
            assertEquals(RiskLevel.L0, proposed.level, action)
            assertTrue(proposed.requiresTap, action)
        }
    }

    @Test
    fun aPaymentIsRefusedWhoeverAsks() {
        for (provenance in Provenance.entries) assertIs<GateDecision.Reject>(decide("OpenUpiPayment", provenance), provenance.name)
    }

    @Test
    fun untrustedTextCannotBypassTheRateLimitByBeingTainted() {
        val limited = Gate(GatePolicy(), SourceRateLimiter(maxPerWindow = 1, windowSeconds = 60))
        val request = GateRequest(ProposedAction("CreateReminder", valid.getValue("CreateReminder")), Provenance.TAINTED, "x", "notification", now)
        assertIs<GateDecision.Propose>(limited.decide(request))
        assertIs<GateDecision.Reject>(limited.decide(request))
    }
}
