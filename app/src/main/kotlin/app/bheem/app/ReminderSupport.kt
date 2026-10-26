package app.bheem.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.bheem.core.reminder.AlarmKind
import app.bheem.core.reminder.AlarmPort
import app.bheem.core.reminder.CatchUp
import app.bheem.core.reminder.RearmReason
import app.bheem.core.reminder.Reminder
import app.bheem.core.reminder.ReminderScheduler
import java.time.ZoneId

class AndroidAlarmPort(private val context: Context) : AlarmPort {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = alarms.canScheduleExactAlarms()

    override fun arm(reminder: Reminder, kind: AlarmKind) {
        val pending = pendingFor(reminder.id, create = true) ?: return
        when (kind) {
            AlarmKind.Exact -> alarms.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.fireAtEpochMs, ReminderNotifications.openApp(context, reminder.id)), pending)
            AlarmKind.Inexact -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAtEpochMs, pending)
        }
    }

    override fun cancel(id: Long) {
        pendingFor(id, create = false)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingFor(id: Long, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("bheem-reminder:$id"))
            .putExtra(ReminderReceiver.EXTRA_ID, id)
        val flags = PendingIntent.FLAG_IMMUTABLE or (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, id.toInt(), intent, flags)
    }
}

object ReminderNotifications {
    private const val CATCH_UP_ID = 1
    private const val COULD_NOT_SET_ID = 2

    fun openApp(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java).setData(Uri.parse("bheem-open:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun build(context: Context, title: String, text: String?, id: Long): Notification {
        val builder = Notification.Builder(context, Notifications.REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setContentIntent(openApp(context, id))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, Notifications.REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Reminder")
                    .build(),
            )
            .setAutoCancel(true)
        if (text != null) builder.setContentText(text)
        return builder.build()
    }

    fun reminder(context: Context, id: Long, text: String?) {
        context.getSystemService(NotificationManager::class.java).notify(id.toInt() + 1_000, build(context, "Reminder", text ?: "You have a reminder. Open Bheem.", id))
    }

    fun catchUp(context: Context, title: String, text: String) {
        context.getSystemService(NotificationManager::class.java).notify(CATCH_UP_ID, build(context, title, text, 0))
    }

    fun couldNotSet(context: Context, count: Int) {
        val text = if (count == 1) "One reminder could not be set. Open Bheem." else "$count reminders could not be set. Open Bheem."
        context.getSystemService(NotificationManager::class.java).notify(COULD_NOT_SET_ID, build(context, "Reminders need attention", text, 0))
    }

    fun clearCatchUp(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(CATCH_UP_ID)
    }
}

class ReminderServices(private val context: Context, val scheduler: ReminderScheduler) {
    companion object {
        fun lightweight(context: Context): ReminderServices {
            val app = context.applicationContext
            return ReminderServices(app, ReminderScheduler(SharedStorage.get(app).reminders, AndroidAlarmPort(app)))
        }
    }

    fun alarmFired(id: Long) {
        scheduler.fired(id)?.let { ReminderNotifications.reminder(context, it.id, it.text) }
    }

    fun rearm(reason: RearmReason, nowMs: Long = System.currentTimeMillis()) {
        val started = System.currentTimeMillis()
        val report = scheduler.rearm(nowMs, ZoneId.systemDefault(), reason)
        Log.i("BheemReminders", "rearm $reason: ${report.rearmed.size} armed, ${report.missed.size} missed, ${report.failed.size} failed, ${System.currentTimeMillis() - started} ms")
        val unseen = scheduler.unseenMissed()
        if (unseen.isNotEmpty()) {
            val notice = CatchUp.notice(unseen)
            ReminderNotifications.catchUp(context, notice.title, notice.text)
        }
        if (report.failed.isNotEmpty()) ReminderNotifications.couldNotSet(context, report.failed.size)
    }

    fun acknowledgeMissed() {
        scheduler.acknowledgeMissed()
        ReminderNotifications.clearCatchUp(context)
    }
}
