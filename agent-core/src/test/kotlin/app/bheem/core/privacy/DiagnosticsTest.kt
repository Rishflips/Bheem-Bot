package app.bheem.core.privacy

import app.bheem.core.gate.Gate
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.Outcome
import app.bheem.core.pipeline.Planner
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DiagnosticsTest {
    private val t0 = LocalDateTime.of(2026, 10, 27, 9, 0)
    private val store = InMemoryLedgerStore()
    private val ledger = Ledger(store)

    private fun refuse(id: String, minutes: Long, reason: String, action: String = "DraftMessage", source: String = "bar") {
        ledger.record(id, LedgerEvent.REJECTED, t0.plusMinutes(minutes), reason, action, source)
    }

    @Test
    fun itListsTheNewestFiftyRefusalsAndOnlyRefusals() {
        for (i in 1..60) refuse("r$i", i.toLong(), "reason $i")
        ledger.record("ok", LedgerEvent.PROPOSED, t0.plusMinutes(100), "args={text=secret}", "SaveNote", "bar")
        val lines = Diagnostics.refusals(store, limit = 50)
        assertEquals(50, lines.size)
        assertEquals("reason 60", lines.first().reason)
        assertEquals("reason 11", lines.last().reason)
        assertTrue(lines.none { it.action == "SaveNote" })
    }

    @Test
    fun aReasonIsMadeSafeBeforeItIsShown() {
        refuse("r1", 1, "unknown action " + "x".repeat(400) + "\nwith a second line")
        val line = Diagnostics.refusals(store, 50).single()
        assertTrue(line.reason.length <= Redact.MAX_REASON + 1)
        assertFalse(line.reason.any { it.isISOControl() })
    }

    @Test
    fun theReportSaysItWasNeverSentAndHoldsOnlyTimeActionSourceAndReason() {
        refuse("r1", 1, "recipient is not in the command", "DraftMessage", "bar")
        refuse("r2", 2, "payments come later", "OpenUpiPayment", "voice")
        val report = Diagnostics.report(Diagnostics.refusals(store, 50))
        assertTrue(report.startsWith("Bheem diagnostics"))
        assertTrue(report.contains("never sent anywhere"))
        assertTrue(report.contains("2026-10-27 09:02 | OpenUpiPayment | voice | payments come later"))
        assertTrue(report.contains("2026-10-27 09:01 | DraftMessage | bar | recipient is not in the command"))
    }

    @Test
    fun anEmptyReportSaysSo() {
        assertTrue(Diagnostics.report(emptyList()).contains("No refusals"))
    }

    @Test
    fun whatAPersonSaidNeverReachesTheDiagnosticsEvenWhenTheRequestIsRefused() = runBlocking<Unit> {
        val secret = "message mom my bank pin is 4242"
        val pipeline = CommandPipeline(Planner { _, _ -> "DraftMessage|whatsapp|dad|send money" }, Gate(), ledger, newId = { "p1" })
        assertIs<Outcome.Refused>(pipeline.handle(secret, t0))
        val report = Diagnostics.report(Diagnostics.refusals(store, 50))
        assertFalse(report.contains("4242"))
        assertFalse(report.contains("bank pin"))
        assertTrue(report.contains("recipient is not in the command"))
    }

    @Test
    fun aPlannerFailureNamesTheKindOfErrorNotItsMessage() = runBlocking<Unit> {
        val pipeline = CommandPipeline(Planner { _, _ -> throw IllegalStateException("could not process: message mom my pin is 4242") }, Gate(), ledger, newId = { "p1" })
        val refused = assertIs<Outcome.Refused>(pipeline.handle("remind me to call mom my pin is 4242", t0))
        assertFalse(refused.reason.contains("4242"))
        val report = Diagnostics.report(Diagnostics.refusals(store, 50))
        assertFalse(report.contains("4242"))
        assertTrue(report.contains("IllegalStateException"))
    }

    @Test
    fun anOutputThatEchoesWhatThePersonSaidNeverReachesTheReportOrTheLedgerRow() = runBlocking<Unit> {
        for (echoed in listOf("4242|x", "ravi.k@okaxis|5000", "Mom's PIN is 4242")) {
            val pipeline = CommandPipeline(Planner { _, _ -> echoed }, Gate(), ledger, newId = { "p-" + echoed.length })
            assertIs<Outcome.Refused>(pipeline.handle("remind me about the pin 4242", t0))
        }
        val report = Diagnostics.report(Diagnostics.refusals(store, 50))
        assertFalse(report.contains("4242"), report)
        assertFalse(report.contains("okaxis"), report)
        assertFalse(report.contains("Mom"), report)
        assertTrue(store.all().filter { it.event == LedgerEvent.REJECTED }.all { it.detail.length <= Redact.MAX_REASON + 1 })
    }

    @Test
    fun aHugeOutputIsBoundedWhenTheRefusalIsRecordedNotOnlyWhenItIsShown() = runBlocking<Unit> {
        val pipeline = CommandPipeline(Planner { _, _ -> "A".repeat(1_000_000) }, Gate(), ledger, newId = { "p1" })
        assertIs<Outcome.Refused>(pipeline.handle("note something", t0))
        assertTrue(store.all().single { it.event == LedgerEvent.REJECTED }.detail.length <= Redact.MAX_REASON + 1)
    }
}
