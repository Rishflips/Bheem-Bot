package app.bheem.core.time

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TimeReconcilerTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)

    private fun reconcile(text: String, model: String?) = TimeReconciler.reconcile(text, model, now)

    @Test
    fun theUsersWordsOverrideAWrongModelDate() {
        val r = reconcile("set a reminder for next Monday at 8 AM to submit the timesheet", "2026-10-28T08:00")
        assertEquals(LocalDateTime.of(2026, 10, 26, 8, 0), r.value)
        assertEquals(TimeSource.CODE, r.source)
        assertEquals(true, r.corrected)
    }

    @Test
    fun aModelTimeThatAgreesIsNotCalledCorrected() {
        val r = reconcile("remind me tomorrow at 7 PM about the recital", "2026-10-24T19:00")
        assertEquals(LocalDateTime.of(2026, 10, 24, 19, 0), r.value)
        assertEquals(TimeSource.CODE, r.source)
        assertEquals(false, r.corrected)
    }

    @Test
    fun anOffsetFromNowBeatsAModelThatPickedTomorrow() {
        val r = reconcile("ping me in two hours to check the oven", "2026-10-24T10:00")
        assertEquals(LocalDateTime.of(2026, 10, 23, 10, 0), r.value)
        assertEquals(true, r.corrected)
    }

    @Test
    fun withoutADateWordTheModelTimeIsKeptAndSecondsAreDropped() {
        val r = reconcile("remind me to send the invoice", "2026-10-24T17:00:00")
        assertEquals(LocalDateTime.of(2026, 10, 24, 17, 0), r.value)
        assertEquals(TimeSource.MODEL, r.source)
        assertEquals(false, r.corrected)
    }

    @Test
    fun aDateWithoutAClockTimeKeepsTheModelsTimeOfDay() {
        val r = reconcile("remind me tomorrow to call Ravi", "2026-10-25T09:30")
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 30), r.value)
        assertEquals(TimeSource.CODE, r.source)
        assertEquals(true, r.corrected)
    }

    @Test
    fun aDateWithoutAClockTimeAndNoUsableModelTimeLeavesTheTimeOpen() {
        val r = reconcile("remind me tomorrow to call Ravi", null)
        assertNull(r.value)
        assertEquals(TimeSource.NONE, r.source)
    }

    @Test
    fun anUnreadableModelTimeIsTreatedAsMissing() {
        assertNull(reconcile("remind me to send the invoice", "tomorrow").value)
        assertNull(reconcile("remind me to send the invoice", "").value)
        assertNull(reconcile("remind me to send the invoice", null).value)
        assertEquals(TimeSource.NONE, reconcile("remind me to send the invoice", "2026-13-40T99:00").source)
    }
}
