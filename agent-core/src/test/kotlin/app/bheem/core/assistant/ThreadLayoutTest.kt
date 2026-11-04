package app.bheem.core.assistant

import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.gate.RiskLevel
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ThreadLayoutTest {
    private val nine = LocalDateTime.of(2026, 11, 4, 9, 0, 0)

    private fun user(text: String, at: LocalDateTime) = Turn.User(text, spoken = false, at)

    private fun card(id: String, created: LocalDateTime, status: FeedStatus = FeedStatus.PENDING, due: LocalDateTime? = null) =
        FeedItem(id, "Reminder", "call Arun", status, RiskLevel.L1, canUndo = false, failure = null, createdAt = created, dueAt = due)

    /** A readable shape of the layout: D for a day divider, T:text for a turn, C:id for a card. */
    private fun shape(entries: List<ThreadEntry>) = entries.map {
        when (it) {
            is ThreadEntry.Day -> "D"
            is ThreadEntry.TurnEntry -> "T:" + when (val t = it.turn) {
                is Turn.User -> t.text
                is Turn.Notice -> t.text
                is Turn.Plan -> "plan"
                else -> "other"
            }
            is ThreadEntry.CardEntry -> "C:" + it.item.id
        }
    }

    @Test
    fun aSingleCardSitsUnderTheTurnThatMadeIt() {
        val turns = listOf(user("remind me at 9", nine), Turn.Notice("later", nine.plusMinutes(5)))
        val entries = ThreadLayout.build(turns, listOf(card("p1", nine.plusSeconds(1))))
        assertEquals(listOf("D", "T:remind me at 9", "C:p1", "T:later"), shape(entries))
    }

    @Test
    fun aPlansCardsFollowThePlanInStepOrderWhateverTheirTimes() {
        val plan = Turn.Plan("Two steps", listOf("b", "a"), emptyList(), nine)
        val turns = listOf(user("do two things", nine), plan, Turn.Notice("after", nine.plusMinutes(2)))
        val cards = listOf(card("a", nine.plusSeconds(1)), card("b", nine.plusSeconds(2)), card("solo", nine.plusMinutes(1)))
        assertEquals(listOf("D", "T:do two things", "T:plan", "C:b", "C:a", "C:solo", "T:after"), shape(ThreadLayout.build(turns, cards)))
    }

    @Test
    fun aPlanStepThatIsGoneIsSkippedWithoutBreakingTheRest() {
        val plan = Turn.Plan("Two steps", listOf("gone", "a"), emptyList(), nine)
        val entries = ThreadLayout.build(listOf(plan), listOf(card("a", nine.plusSeconds(1))))
        assertEquals(listOf("D", "T:plan", "C:a"), shape(entries))
    }

    @Test
    fun aDoneCardOlderThanEveryShownTurnIsLeftOut() {
        val turns = listOf(user("hi", nine))
        val old = card("old", nine.minusDays(3), FeedStatus.DONE)
        assertEquals(listOf("D", "T:hi"), shape(ThreadLayout.build(turns, listOf(old))))
    }

    @Test
    fun aWaitingCardOlderThanEveryShownTurnStaysAtTheTop() {
        val turns = listOf(user("hi", nine))
        val old = card("old", nine.minusDays(3), FeedStatus.PENDING)
        assertEquals(listOf("D", "C:old", "D", "T:hi"), shape(ThreadLayout.build(turns, listOf(old))))
    }

    @Test
    fun withNoTurnsEveryCardShowsOldestFirst() {
        val cards = listOf(card("new", nine.plusHours(1)), card("old", nine))
        assertEquals(listOf("D", "C:old", "C:new"), shape(ThreadLayout.build(emptyList(), cards)))
    }

    @Test
    fun aNewDayGetsADivider() {
        val turns = listOf(user("yesterday", nine.minusDays(1)), user("today", nine))
        val entries = ThreadLayout.build(turns, emptyList())
        assertEquals(listOf("D", "T:yesterday", "D", "T:today"), shape(entries))
        assertEquals(LocalDate.of(2026, 11, 3), (entries[0] as ThreadEntry.Day).date)
        assertEquals(LocalDate.of(2026, 11, 4), (entries[2] as ThreadEntry.Day).date)
    }

    @Test
    fun theFirstWaitingCardIsFoundByItsPositionInTheLayout() {
        val turns = listOf(user("a", nine), user("b", nine.plusMinutes(1)))
        val cards = listOf(card("done", nine.plusSeconds(1), FeedStatus.DONE), card("w", nine.plusSeconds(70)))
        val entries = ThreadLayout.build(turns, cards)
        assertEquals(listOf("D", "T:a", "C:done", "T:b", "C:w"), shape(entries))
        assertEquals(4, ThreadLayout.firstWaiting(entries))
        assertEquals(1, ThreadLayout.waitingCount(entries))
        assertNull(ThreadLayout.firstWaiting(ThreadLayout.build(turns, emptyList())))
    }

    @Test
    fun dayLabelsSayTodayAndYesterdayAndOtherwiseGiveTheDate() {
        val today = LocalDate.of(2026, 11, 4)
        assertIs<DayLabel.Today>(DayLabel.of(today, today))
        assertIs<DayLabel.Yesterday>(DayLabel.of(today.minusDays(1), today))
        assertEquals(DayLabel.Other(LocalDate.of(2026, 11, 2)), DayLabel.of(LocalDate.of(2026, 11, 2), today))
    }
}
