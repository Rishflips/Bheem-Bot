package app.bheem.core.assistant

import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.gate.RiskLevel
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BubbleLookTest {
    private val at = LocalDateTime.of(2026, 11, 4, 9, 0)

    private fun turn(t: Turn, n: Int) = ThreadEntry.TurnEntry(t, "t$n")

    private fun card() = ThreadEntry.CardEntry(
        FeedItem("c", "Reminder", "x", FeedStatus.PENDING, RiskLevel.L1, canUndo = false, failure = null, createdAt = at),
    )

    private fun user(n: Int) = turn(Turn.User("hi", false, at), n)

    private fun bheem(n: Int) = turn(Turn.Notice("ok", at), n)

    @Test
    fun theBheemLabelShowsOncePerRunOfBheemTurns() {
        val entries = listOf(ThreadEntry.Day(LocalDate.of(2026, 11, 4), "d"), user(1), bheem(2), bheem(3), user(4), bheem(5))
        assertTrue(Bubbles.look(entries, 2).showLabel)
        assertFalse(Bubbles.look(entries, 3).showLabel)
        assertTrue(Bubbles.look(entries, 5).showLabel)
    }

    @Test
    fun aCardBetweenTwoBheemTurnsStartsANewRun() {
        val entries = listOf(bheem(1), card(), bheem(2))
        assertTrue(Bubbles.look(entries, 0).showLabel)
        assertTrue(Bubbles.look(entries, 2).showLabel)
    }

    @Test
    fun aBubbleFollowedByACardHasSquareBottomCorners() {
        val entries = listOf(user(1), bheem(2), card(), bheem(3))
        assertTrue(Bubbles.look(entries, 1).squareBottom)
        assertFalse(Bubbles.look(entries, 3).squareBottom)
    }

    @Test
    fun theOwnersBubbleNeverShowsALabel() {
        assertFalse(Bubbles.look(listOf(user(1)), 0).showLabel)
    }

    @Test
    fun aNoticeThatEndsInAQuestionMarkIsAClarifyingQuestion() {
        assertEquals(NoticeKind.CLARIFY, NoticeKind.of("What time should I remind you?"))
        assertEquals(NoticeKind.CLARIFY, NoticeKind.of("What time should I remind you?  "))
        assertEquals(NoticeKind.INFO, NoticeKind.of("I could not do that: payments come later."))
    }
}
