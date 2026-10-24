package app.bheem.core.reminder

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReminderSchedulerTest {
    private val ist = ZoneId.of("Asia/Kolkata")
    private val store = InMemoryReminderStore()
    private val alarms = FakeAlarms()
    private val scheduler = ReminderScheduler(store, alarms)

    private class FakeAlarms(var exact: Boolean = true, var failArm: Boolean = false) : AlarmPort {
        val armed = linkedMapOf<Long, Pair<Long, AlarmKind>>()
        var armCalls = 0

        override fun canScheduleExact() = exact

        override fun arm(reminder: Reminder, kind: AlarmKind) {
            armCalls++
            if (failArm) throw SecurityException("not allowed")
            armed[reminder.id] = reminder.fireAtEpochMs to kind
        }

        override fun cancel(id: Long) {
            armed.remove(id)
        }
    }

    private fun at(hour: Int, day: Int = 25) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(ist).toInstant().toEpochMilli()

    @Test
    fun theRowIsWrittenThenAnAlarmIsArmedWithTheRowIdAsItsKey() {
        val result = scheduler.schedule("p1", "call Arun", at(17), ist.id)
        val row = store.get(result.reminder.id)!!
        assertEquals(ReminderState.SCHEDULED, row.state)
        assertEquals("call Arun", row.text)
        assertEquals(at(17) to AlarmKind.Exact, alarms.armed[row.id])
        assertTrue(result.exact)
        assertNull(result.note)
    }

    @Test
    fun withoutTheExactPermissionTheAlarmIsInexactAndTheResultSaysSo() {
        alarms.exact = false
        val result = scheduler.schedule("p1", "call Arun", at(17), ist.id)
        assertEquals(at(17) to AlarmKind.Inexact, alarms.armed[result.reminder.id])
        assertFalse(result.exact)
        assertTrue(result.note.orEmpty().contains("late"))
    }

    @Test
    fun twoRemindersWithTheSameTextAndTimeDoNotCollide() {
        val a = scheduler.schedule("p1", "stretch", at(17), ist.id).reminder
        val b = scheduler.schedule("p2", "stretch", at(17), ist.id).reminder
        assertNotEquals(a.id, b.id)
        assertEquals(setOf(a.id, b.id), alarms.armed.keys)
        assertTrue(scheduler.cancel(a.id))
        assertEquals(setOf(b.id), alarms.armed.keys)
        assertEquals(ReminderState.SCHEDULED, store.get(b.id)!!.state)
    }

    @Test
    fun approvingTheSameProposalAgainReusesTheRowAndArmsAgain() {
        val first = scheduler.schedule("p1", "stretch", at(17), ist.id).reminder
        val second = scheduler.schedule("p1", "stretch", at(17), ist.id).reminder
        assertEquals(first.id, second.id)
        assertEquals(1, store.scheduled().size)
        assertEquals(1, alarms.armed.size)
    }

    @Test
    fun anAlarmThatCannotBeArmedLeavesNoScheduledRowAndThrows() {
        alarms.failArm = true
        assertFailsWith<SecurityException> { scheduler.schedule("p1", "stretch", at(17), ist.id) }
        assertEquals(emptyList(), store.scheduled())
        assertEquals(ReminderState.CANCELLED, store.byProposal("p1")!!.state)
    }

    @Test
    fun cancellingRemovesTheAlarmAndTheRowStaysAsCancelled() {
        val r = scheduler.schedule("p1", "stretch", at(17), ist.id).reminder
        assertTrue(scheduler.cancel(r.id))
        assertEquals(emptyMap(), alarms.armed)
        assertEquals(ReminderState.CANCELLED, store.get(r.id)!!.state)
        assertTrue(scheduler.cancel(r.id))
    }

    @Test
    fun aFiredReminderCannotBeCancelledOrFiredTwice() {
        val r = scheduler.schedule("p1", "stretch", at(17), ist.id).reminder
        assertEquals("stretch", scheduler.fired(r.id)?.text)
        assertNull(scheduler.fired(r.id))
        assertFalse(scheduler.cancel(r.id))
        assertEquals(ReminderState.FIRED, store.get(r.id)!!.state)
    }

    @Test
    fun aCancelledReminderDoesNotNotifyIfItsAlarmStillArrives() {
        val r = scheduler.schedule("p1", "stretch", at(17), ist.id).reminder
        scheduler.cancel(r.id)
        assertNull(scheduler.fired(r.id))
    }

    @Test
    fun rearmingPutsFutureRemindersBackUnderTheSameIds() {
        val a = scheduler.schedule("p1", "a", at(17), ist.id).reminder
        val b = scheduler.schedule("p2", "b", at(18), ist.id).reminder
        alarms.armed.clear()
        val report = scheduler.rearm(nowMs = at(9), zone = ist)
        assertEquals(setOf(a.id, b.id), alarms.armed.keys)
        assertEquals(listOf(a.id, b.id), report.rearmed.map { it.id })
        assertEquals(emptyList(), report.missed)
    }

    @Test
    fun remindersThatCameDueWhileOffAreMarkedMissedAndNeverArmed() {
        val past = scheduler.schedule("p1", "old", at(8), ist.id).reminder
        val future = scheduler.schedule("p2", "new", at(18), ist.id).reminder
        alarms.armed.clear()
        alarms.armCalls = 0
        val report = scheduler.rearm(nowMs = at(12), zone = ist)
        assertEquals(listOf(past.id), report.missed.map { it.id })
        assertEquals(ReminderState.MISSED, store.get(past.id)!!.state)
        assertEquals(setOf(future.id), alarms.armed.keys)
        assertEquals(1, alarms.armCalls)
    }

    @Test
    fun aReminderThatFellDueJustBeforeTheAppStartedIsArmedToFireNowNotCalledMissed() {
        val r = scheduler.schedule("p1", "call Arun", at(17), ist.id).reminder
        alarms.armed.clear()
        val report = scheduler.rearm(nowMs = at(17) + 30_000, zone = ist)
        assertEquals(emptyList(), report.missed)
        assertEquals(listOf(r.id), report.rearmed.map { it.id })
        assertEquals(setOf(r.id), alarms.armed.keys)
        assertEquals(ReminderState.SCHEDULED, store.get(r.id)!!.state)
    }

    @Test
    fun aReminderOlderThanTheGraceWindowIsMissed() {
        val r = scheduler.schedule("p1", "call Arun", at(17), ist.id).reminder
        val report = scheduler.rearm(nowMs = at(17) + 3 * 60_000, zone = ist)
        assertEquals(listOf(r.id), report.missed.map { it.id })
    }

    @Test
    fun aLongPowerOffGivesOneReportAndNoAlarms() {
        repeat(300) { scheduler.schedule("p$it", "r$it", at(8), ist.id) }
        alarms.armed.clear()
        alarms.armCalls = 0
        val report = scheduler.rearm(nowMs = at(8, day = 30), zone = ist)
        assertEquals(300, report.missed.size)
        assertEquals(0, alarms.armCalls)
        assertEquals(emptyList(), store.scheduled())
    }

    @Test
    fun rearmingTwiceIsHarmless() {
        scheduler.schedule("p1", "a", at(17), ist.id)
        scheduler.rearm(at(9), ist)
        val second = scheduler.rearm(at(9), ist)
        assertEquals(1, alarms.armed.size)
        assertEquals(1, second.rearmed.size)
    }

    @Test
    fun rearmingUsesTheExactPermissionAsItIsNow() {
        val r = scheduler.schedule("p1", "a", at(17), ist.id).reminder
        alarms.exact = false
        scheduler.rearm(at(9), ist)
        assertEquals(AlarmKind.Inexact, alarms.armed[r.id]!!.second)
        alarms.exact = true
        scheduler.rearm(at(9), ist)
        assertEquals(AlarmKind.Exact, alarms.armed[r.id]!!.second)
    }

    @Test
    fun aChangeOfTimeZoneKeepsTheWallClockTime() {
        val london = ZoneId.of("Europe/London")
        val r = scheduler.schedule("p1", "call home", at(17), ist.id).reminder
        scheduler.rearm(at(9), london)
        val expected = LocalDateTime.of(2026, 10, 25, 17, 0).atZone(london).toInstant().toEpochMilli()
        assertEquals(expected, store.get(r.id)!!.fireAtEpochMs)
        assertEquals("Europe/London", store.get(r.id)!!.zoneId)
        assertEquals(expected, alarms.armed[r.id]!!.first)
    }

    @Test
    fun theCatchUpTextListsAFewAndCountsTheRest() {
        val missed = (1..12).map { Reminder(it.toLong(), "p$it", "task $it", at(8) + it * 60_000L, ist.id, ReminderState.MISSED) }
        val notice = CatchUp.notice(missed)
        assertEquals("You missed 12 reminders", notice.title)
        assertEquals("task 12, task 11, task 10 and 9 more", notice.text)
    }

    @Test
    fun theCatchUpTextForOneReminderIsPlain() {
        val one = listOf(Reminder(1, "p", "take the medicine", at(8), ist.id, ReminderState.MISSED))
        val notice = CatchUp.notice(one)
        assertEquals("You missed a reminder", notice.title)
        assertEquals("take the medicine", notice.text)
    }

    @Test
    fun healthListsWhatCouldStopAReminder() {
        assertEquals(emptyList(), ReminderHealth.issues(notificationsOn = true, exactOn = true, ignoringBatteryOptimizations = true))
        assertEquals(
            listOf(ReminderIssue.NotificationsOff, ReminderIssue.ExactAlarmsOff, ReminderIssue.BatteryRestricted),
            ReminderHealth.issues(notificationsOn = false, exactOn = false, ignoringBatteryOptimizations = false),
        )
    }

    @Test
    fun theBatteryHintNamesTheMakerSetting() {
        assertTrue(OemHint.text("Xiaomi").contains("Autostart"))
        assertTrue(OemHint.text("samsung").contains("Never sleeping apps"))
        assertTrue(OemHint.text("realme").contains("battery"))
        assertTrue(OemHint.text("motorola").contains("battery"))
    }
}
