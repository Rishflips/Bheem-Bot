package app.bheem.core.assistant

import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.RecordKind
import app.bheem.core.reminder.Reminder
import app.bheem.core.reminder.ReminderState
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProactiveTest {
    private val at = LocalDateTime.of(2026, 11, 5, 9, 0)
    private val zone = ZoneId.of("Asia/Kolkata")

    private fun reminder(id: Long, text: String, fireAt: LocalDateTime, state: ReminderState = ReminderState.SCHEDULED) =
        Reminder(id, "p$id", text, fireAt.atZone(zone).toInstant().toEpochMilli(), zone.id, state)

    private fun task(id: String, text: String, created: LocalDateTime, doneAt: LocalDateTime? = null) =
        MemoryRecord(id, RecordKind.TASK, text, created, doneAt = doneAt)

    private fun shown(key: String, whenShown: LocalDateTime) = ShownNudge(key, whenShown)

    private fun completeFacts() = listOf(
        "My name is Rish",
        "I live in Chennai",
        "I work at a school",
        "People: Meena and Arun",
        "Routine: I wake at 6",
        "Preferences: Filter coffee",
        "Goals: A house of my own",
        "Languages: Tamil and English",
    )

    private val otherQuestions = setOf(3, 4, 5, 6, 7)

    @Test
    fun aClashIsFoundAndWorded() {
        val input = ProactiveInput(
            reminders = listOf(
                reminder(1, "call Amma", at.plusHours(1)),
                reminder(2, "take medicine", at.plusHours(1).plusMinutes(20)),
            ),
            facts = completeFacts(),
        )
        val nudge = Proactive.next(at, input, ProactiveState())
        assertEquals(Nudge.Kind.CLASH, nudge?.kind)
        assertEquals("clash:1:2", nudge?.key)
        assertEquals("Heads up: call Amma at 10:00 AM and take medicine at 10:20 AM are close together.", nudge?.text)
    }

    @Test
    fun remindersFortyFiveMinutesApartAreNotAClash() {
        val input = ProactiveInput(
            reminders = listOf(
                reminder(1, "call Amma", at.plusHours(1)),
                reminder(2, "take medicine", at.plusHours(1).plusMinutes(45)),
            ),
            facts = completeFacts(),
        )
        assertNull(Proactive.next(at, input, ProactiveState()))
    }

    @Test
    fun aFollowUpWaitsUntilTheTaskIsMoreThanThreeDaysOld() {
        val fresh = ProactiveInput(tasks = listOf(task("t1", "call the electrician", at.minusDays(3))), facts = completeFacts())
        assertNull(Proactive.next(at, fresh, ProactiveState()))

        val old = ProactiveInput(tasks = listOf(task("t1", "call the electrician", at.minusDays(3).minusMinutes(1))), facts = completeFacts())
        val nudge = Proactive.next(at, old, ProactiveState())
        assertEquals(Nudge.Kind.FOLLOW_UP, nudge?.kind)
        assertEquals("task:t1", nudge?.key)
        assertEquals("Still planning to call the electrician? Say remind me ... and I will set it.", nudge?.text)
    }

    @Test
    fun nothingIsSaidInQuietHours() {
        val input = ProactiveInput(
            reminders = listOf(reminder(1, "call Amma", at.plusHours(1)), reminder(2, "take medicine", at.plusHours(1).plusMinutes(10))),
            facts = completeFacts(),
        )
        assertNull(Proactive.next(at.withHour(22).withMinute(0), input, ProactiveState()))
        assertNull(Proactive.next(at.withHour(6).withMinute(59), input, ProactiveState()))

        val morning = at.withHour(7).withMinute(0)
        val clash = input.copy(
            reminders = listOf(reminder(1, "call Amma", morning.plusHours(1)), reminder(2, "take medicine", morning.plusHours(1).plusMinutes(10))),
        )
        assertEquals(Nudge.Kind.CLASH, Proactive.next(morning, clash, ProactiveState())?.kind)
    }

    @Test
    fun atMostThreeNudgesAreShownInADay() {
        val input = ProactiveInput(
            reminders = listOf(reminder(1, "call Amma", at.plusHours(1)), reminder(2, "take medicine", at.plusHours(1).plusMinutes(10))),
            facts = completeFacts(),
        )
        val full = ProactiveState(listOf(shown("a", at.minusHours(3)), shown("b", at.minusHours(2)), shown("c", at.minusHours(1))))
        assertNull(Proactive.next(at, input, full))
        val two = ProactiveState(listOf(shown("a", at.minusHours(3)), shown("b", at.minusHours(2))))
        assertEquals(Nudge.Kind.CLASH, Proactive.next(at, input, two)?.kind)
    }

    @Test
    fun theSameNudgeIsNotShownTwiceInSevenDays() {
        val input = ProactiveInput(
            reminders = listOf(reminder(1, "call Amma", at.plusHours(1)), reminder(2, "take medicine", at.plusHours(1).plusMinutes(10))),
            facts = completeFacts(),
        )
        assertNull(Proactive.next(at, input, ProactiveState(listOf(shown("clash:1:2", at.minusDays(6))))))
        assertEquals(Nudge.Kind.CLASH, Proactive.next(at, input, ProactiveState(listOf(shown("clash:1:2", at.minusDays(7).minusMinutes(1)))))?.kind)
    }

    @Test
    fun aGapQuestionIsChosenWhenTheCityFactIsMissing() {
        val facts = listOf("My name is Rish", "I work at a school")
        val input = ProactiveInput(facts = facts, keptQuestions = otherQuestions)
        val nudge = Proactive.next(at, input, ProactiveState())
        assertEquals(Nudge.Kind.GAP, nudge?.kind)
        assertEquals("question:1", nudge?.key)
        assertEquals(1, nudge?.questionIndex)
        assertEquals(Interview.QUESTIONS[1], nudge?.text)
    }

    @Test
    fun aGapQuestionIsSkippedWhenTheFactIsPresent() {
        val input = ProactiveInput(facts = completeFacts(), keptQuestions = otherQuestions)
        assertNull(Proactive.next(at, input, ProactiveState()))
    }

    @Test
    fun atMostOneFollowUpTaskIsShownInADay() {
        val input = ProactiveInput(
            tasks = listOf(
                task("t1", "call the electrician", at.minusDays(4)),
                task("t2", "pay the water bill", at.minusDays(5)),
            ),
            facts = completeFacts(),
        )
        assertNull(Proactive.next(at, input, ProactiveState(listOf(shown("task:t1", at.minusHours(1))))))
    }

    @Test
    fun atMostOneGapQuestionIsShownInADay() {
        val input = ProactiveInput(facts = listOf("My name is Rish"), keptQuestions = otherQuestions)
        assertNull(Proactive.next(at, input, ProactiveState(listOf(shown("question:1", at.minusHours(1))))))
    }

    @Test
    fun nothingIsShownWhenAllIsWell() {
        val input = ProactiveInput(facts = completeFacts(), keptQuestions = otherQuestions)
        assertNull(Proactive.next(at, input, ProactiveState()))
    }

    @Test
    fun theStateKeepsOnlyRecentShownKeys() {
        val old = ProactiveState(listOf(shown("old", at.minusDays(40))))
        val nudge = Nudge(Nudge.Kind.CLASH, "clash:1:2", "text")
        val updated = old.record(nudge, at)
        assertEquals(listOf(ShownNudge("clash:1:2", at)), updated.shown)
    }

    @Test
    fun theStateSurvivesItsStoredForm() {
        val state = ProactiveState(listOf(shown("clash:1:2", at), shown("question:3", at.minusDays(1))))
        assertEquals(state, ProactiveStateCodec.decode(ProactiveStateCodec.encode(state)))
        assertEquals(ProactiveState(), ProactiveStateCodec.decode(""))
        assertEquals(ProactiveState(), ProactiveStateCodec.decode("garbage"))
    }
}
