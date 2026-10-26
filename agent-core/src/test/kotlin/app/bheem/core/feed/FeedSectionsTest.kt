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
import kotlin.test.assertTrue

class FeedSectionsTest {
    private val t0 = LocalDateTime.of(2026, 10, 27, 8, 0)
    private val ledgerStore = InMemoryLedgerStore()
    private val ledger = Ledger(ledgerStore)
    private val proposals = InMemoryProposalStore()

    private fun rows() = InMemoryFeedSource(proposals, ledgerStore).rows(500)

    private fun items(now: LocalDateTime) = Feed.items(rows(), now, 30L)

    private fun add(id: String, at: LocalDateTime, action: ProposedAction, events: List<LedgerEvent> = listOf(LedgerEvent.PROPOSED), source: String = "bar") {
        proposals.put(StoredProposal(id, action, RiskLevel.L0, true, at, "typed $id", "token-$id", source = source))
        events.forEachIndexed { i, e -> ledger.record(id, e, at.plusSeconds(i.toLong()), action = action.action, source = source) }
    }

    private fun reminder(text: String, time: String) = ProposedAction("CreateReminder", mapOf("text" to text, "time" to time))

    private fun note(text: String) = ProposedAction("SaveNote", mapOf("text" to text))

    private val done = listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED, LedgerEvent.EXECUTED)

    @Test
    fun cardsWaitingOnTheUserAndFailedOnesAreInNeedsYou() {
        add("p", t0, note("a"))
        add("w", t0.plusMinutes(1), note("b"), listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED))
        add("f", t0.plusMinutes(2), note("c"), listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED, LedgerEvent.FAILED))
        add("d", t0.plusMinutes(3), note("d"), done)
        val sections = FeedSections.of(items(t0.plusHours(1)), t0.plusHours(1))
        assertEquals(setOf("p", "w", "f"), sections.needsYou.map { it.id }.toSet())
        assertEquals(listOf("d"), sections.recent.map { it.id })
        assertTrue(sections.upcoming.isEmpty())
    }

    @Test
    fun aDoneReminderStillInTheFutureIsUpcomingSoonestFirstAndNotAlsoRecent() {
        add("late", t0, reminder("call Ravi", "2026-10-29T09:00"), done)
        add("soon", t0.plusMinutes(1), reminder("take tablets", "2026-10-27T21:00"), done)
        add("past", t0.plusMinutes(2), reminder("old", "2026-10-27T07:00"), done)
        val now = t0.plusHours(2)
        val sections = FeedSections.of(items(now), now)
        assertEquals(listOf("soon", "late"), sections.upcoming.map { it.id })
        assertEquals(listOf("past"), sections.recent.map { it.id })
    }

    @Test
    fun anUpcomingReminderNeverFallsOffTheFeedBecauseOfNewerActivity() {
        add("far", t0, reminder("passport", "2026-12-01T09:00"), done)
        for (i in 1..30) add("n$i", t0.plusMinutes(i.toLong()), note("note $i"), done)
        val now = t0.plusHours(3)
        val sections = FeedSections.of(items(now), now)
        assertEquals(listOf("far"), sections.upcoming.map { it.id })
        assertTrue(sections.recent.size <= 20)
    }

    @Test
    fun dismissedAndUndoneItemsAreInNoSection() {
        add("x", t0, note("x"), listOf(LedgerEvent.PROPOSED, LedgerEvent.DISMISSED))
        add("y", t0.plusMinutes(1), reminder("y", "2026-10-30T09:00"), done + LedgerEvent.UNDONE)
        val sections = FeedSections.of(items(t0.plusHours(1)), t0.plusHours(1))
        assertTrue(sections.needsYou.isEmpty() && sections.upcoming.isEmpty() && sections.recent.isEmpty())
    }

    @Test
    fun aReminderWithAnUnreadableTimeIsRecentNotUpcoming() {
        add("odd", t0, ProposedAction("CreateReminder", mapOf("text" to "x", "time" to "soon")), done)
        val sections = FeedSections.of(items(t0.plusHours(1)), t0.plusHours(1))
        assertEquals(listOf("odd"), sections.recent.map { it.id })
    }

    @Test
    fun theLedgerTellsEveryOutcomeInPlainWordsIncludingDismissedAndUndone() {
        add("a", t0, note("locker 27"), listOf(LedgerEvent.PROPOSED))
        add("b", t0.plusMinutes(1), reminder("call Ravi", "2026-10-28T09:00"), done)
        add("c", t0.plusMinutes(2), note("tea"), listOf(LedgerEvent.PROPOSED, LedgerEvent.DISMISSED))
        add("d", t0.plusMinutes(3), note("milk"), done + LedgerEvent.UNDONE)
        add("e", t0.plusMinutes(4), note("keys"), listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED, LedgerEvent.FAILED), source = "voice")
        add("f", t0.plusMinutes(5), note("rude"), listOf(LedgerEvent.REJECTED))
        val lines = LedgerLines.of(rows())
        assertEquals(listOf("f", "e", "d", "c", "b", "a"), lines.map { it.id })
        val byId = lines.associateBy { it.id }
        assertEquals("Waiting for your OK", byId.getValue("a").outcome)
        assertEquals("Done", byId.getValue("b").outcome)
        assertEquals("You dismissed it", byId.getValue("c").outcome)
        assertEquals("You undid it", byId.getValue("d").outcome)
        assertEquals("Did not work", byId.getValue("e").outcome)
        assertEquals("Not allowed", byId.getValue("f").outcome)
        assertEquals("Reminder", byId.getValue("b").title)
        assertEquals("call Ravi, Wed 28 Oct, 9:00 AM", byId.getValue("b").detail)
        assertTrue(byId.getValue("e").viaVoice)
        assertTrue(!byId.getValue("a").viaVoice)
    }

    @Test
    fun todayRowsKeepsEveryReminderEvenWhenMoreThanTheLimitOfNewerRowsExist() {
        add("far", t0, reminder("passport", "2026-12-01T09:00"), done)
        for (i in 1..150) add("n$i", t0.plusMinutes(i.toLong()), note("note $i"), done)
        val source = InMemoryFeedSource(proposals, ledgerStore)
        assertTrue(source.rows(100).none { it.proposal.id == "far" })
        val now = t0.plusHours(5)
        val sections = FeedSections.of(Feed.items(source.todayRows(limit = 100), now, 30L), now)
        assertEquals(listOf("far"), sections.upcoming.map { it.id })
    }

    @Test
    fun aQuestionAskedOfTheNotesIsInPlainWordsInTheLedger() {
        add("q", t0, ProposedAction("AnswerFromMemory", mapOf("query" to "where is the scooter key")), listOf(LedgerEvent.READ))
        val line = LedgerLines.of(rows()).single()
        assertEquals("Question", line.title)
        assertEquals("where is the scooter key", line.detail)
        assertEquals("Answered from your notes", line.outcome)
    }

    @Test
    fun aReminderDueExactlyNowIsNotUpcoming() {
        add("edge", t0, reminder("x", "2026-10-27T09:00"), done)
        val now = LocalDateTime.of(2026, 10, 27, 9, 0)
        val sections = FeedSections.of(items(now), now)
        assertEquals(listOf("edge"), sections.recent.map { it.id })
    }
}
