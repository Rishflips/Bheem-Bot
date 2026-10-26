package app.bheem.core.reminder

import kotlin.test.Test
import kotlin.test.assertEquals

class ReminderHealthTest {
    private val all = ReminderHealth.issues(notificationsOn = false, exactOn = false, ignoringBatteryOptimizations = false)

    @Test
    fun noReminderCardsAreShownBeforeThePersonHasAReminder() {
        assertEquals(emptyList(), ReminderHealth.shown(all, hasReminders = false))
    }

    @Test
    fun onceThereIsAReminderEveryIssueIsShown() {
        assertEquals(all, ReminderHealth.shown(all, hasReminders = true))
        assertEquals(listOf(ReminderIssue.NotificationsOff, ReminderIssue.ExactAlarmsOff, ReminderIssue.BatteryRestricted), all)
    }
}
