package app.bheem.core.assistant

import app.bheem.core.brief.BriefLayout
import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.gate.RiskLevel
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ViewItemsTest {
    private val at = LocalDateTime.of(2026, 11, 4, 9, 0)

    private fun card(id: String, status: FeedStatus = FeedStatus.PENDING) =
        ThreadEntry.CardEntry(FeedItem(id, "Reminder", id, status, RiskLevel.L1, canUndo = false, failure = null, createdAt = at))

    private fun plan(vararg steps: String) = ThreadEntry.TurnEntry(Turn.Plan("Two steps", steps.toList(), emptyList(), at), "plan")

    @Test
    fun aPlanAndItsStepCardsBecomeOneGroupInStepOrder() {
        val entries = listOf(plan("a", "b"), card("a"), card("b"), card("loose"))
        val items = ViewItems.group(entries)
        assertEquals(2, items.size)
        val group = items[0] as ViewItem.PlanGroup
        assertEquals(listOf("a", "b"), group.cards.map { it.id })
        assertEquals("loose", ((items[1] as ViewItem.Single).entry as ThreadEntry.CardEntry).item.id)
    }

    @Test
    fun aPlanWhoseCardsAreGoneIsStillShownAsAnEmptyGroup() {
        val items = ViewItems.group(listOf(plan("a")))
        assertEquals(0, (items.single() as ViewItem.PlanGroup).cards.size)
    }

    @Test
    fun aPlanKnowsHowManyOfItsStepsAreDone() {
        val entries = listOf(plan("a", "b"), card("a", FeedStatus.DONE), card("b"))
        val group = ViewItems.group(entries).single() as ViewItem.PlanGroup
        assertEquals(1, group.done)
        assertEquals(1, group.waiting)
    }

    @Test
    fun theBriefTextBecomesAHeadlineAndSectionsOfRows() {
        val view = BriefLayout.parse("Your day, Wed 4 Nov:\nDue today:\n- 5:00 PM call Arun\n- 7:00 PM call the bank\nClashes:\n- two things at 7 PM")
        assertEquals("Your day, Wed 4 Nov", view.headline)
        assertEquals(listOf("Due today", "Clashes"), view.sections.map { it.title })
        assertEquals(listOf("5:00 PM call Arun", "7:00 PM call the bank"), view.sections[0].rows)
        assertNull(view.note)
    }

    @Test
    fun anEmptyDayKeepsItsSentenceAsANote() {
        val view = BriefLayout.parse("Your day, Wed 4 Nov:\nNothing is due and nothing is waiting.")
        assertEquals(0, view.sections.size)
        assertEquals("Nothing is due and nothing is waiting.", view.note)
    }
}
