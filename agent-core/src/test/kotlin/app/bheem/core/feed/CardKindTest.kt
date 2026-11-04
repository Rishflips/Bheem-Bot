package app.bheem.core.feed

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.proposal.InMemoryProposalStore
import app.bheem.core.proposal.StoredProposal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class CardKindTest {
    private fun kind(action: String, vararg args: Pair<String, String>) = ActionDescriber.kind(ProposedAction(action, mapOf(*args)))

    @Test
    fun everyActionTheAssistantProposesHasItsOwnKind() {
        assertEquals(CardKind.REMINDER, kind("CreateReminder"))
        assertEquals(CardKind.TASK, kind("CreateTask"))
        assertEquals(CardKind.NOTE, kind("SaveNote"))
        assertEquals(CardKind.CALENDAR, kind("CreateCalendarEvent"))
        assertEquals(CardKind.CALL, kind("PlaceCall"))
        assertEquals(CardKind.ROUTE, kind("OpenRoute"))
        assertEquals(CardKind.EXPENSE, kind("LogExpense"))
        assertEquals(CardKind.PAYMENT, kind("OpenUpiPayment"))
    }

    @Test
    fun theNewMemoryAndPhoneActionsHaveTheirOwnKindsAndPlainDescriptions() {
        assertEquals(CardKind.REMEMBER, kind("RememberFact"))
        assertEquals(CardKind.FORGET, kind("ForgetFact"))
        assertEquals(CardKind.TORCH, kind("Flashlight"))
        assertEquals(CardKind.TIMER, kind("SetTimer"))
        assertEquals(CardKind.OPEN_APP, kind("OpenApp"))
        assertEquals(CardKind.VOLUME, kind("SetVolume"))
        assertEquals("10 minutes", ActionDescriber.describe(ProposedAction("SetTimer", mapOf("seconds" to "600"))).detail)
        assertEquals("1 hour", ActionDescriber.describe(ProposedAction("SetTimer", mapOf("seconds" to "3600"))).detail)
        assertEquals("45 seconds", ActionDescriber.describe(ProposedAction("SetTimer", mapOf("seconds" to "45"))).detail)
    }

    @Test
    fun aMessageIsWhatsAppUnlessTheChannelIsSms() {
        assertEquals(CardKind.WHATSAPP, kind("DraftMessage", "channel" to "whatsapp"))
        assertEquals(CardKind.SMS, kind("DraftMessage", "channel" to "sms"))
        assertEquals(CardKind.WHATSAPP, kind("DraftMessage"))
    }

    @Test
    fun anActionNobodyKnowsStillGetsACardOfKindOther() {
        assertEquals(CardKind.OTHER, kind("SomethingNew"))
    }

    @Test
    fun theFeedItemCarriesTheKindSoTheCardNeverParsesItsTitle() {
        val store = InMemoryLedgerStore()
        val proposals = InMemoryProposalStore()
        val at = LocalDateTime.of(2026, 11, 4, 9, 0)
        val action = ProposedAction("PlaceCall", mapOf("name" to "Arun", "number" to "98765 43210"))
        proposals.put(StoredProposal("c1", action, RiskLevel.L1, true, at, "call Arun", "token", source = "bar"))
        Ledger(store).record("c1", LedgerEvent.PROPOSED, at, action = action.action, source = "bar")
        val items = Feed.items(InMemoryFeedSource(proposals, store).rows(10), at.plusMinutes(1), 30L)
        assertEquals(CardKind.CALL, items.single().kind)
    }
}
