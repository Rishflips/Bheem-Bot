package app.bheem.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.bheem.core.reminder.AlarmKind
import app.bheem.core.reminder.AlarmPort
import app.bheem.core.reminder.CatchUp
import app.bheem.core.reminder.Reminder
import app.bheem.core.reminder.ReminderScheduler
import java.time.ZoneId

class AndroidAlarmPort(private val context: Context) : AlarmPort {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean = alarms.canScheduleExactAlarms()

    override fun arm(reminder: Reminder, kind: AlarmKind) {
        val pending = pendingFor(reminder.id, reminder.text)
        when (kind) {
            AlarmKind.Exact -> alarms.setAlarmClock(AlarmManager.AlarmClockInfo(reminder.fireAtEpochMs, openApp(reminder.id)), pending)
            AlarmKind.Inexact -> alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.fireAtEpochMs, pending)
        }
    }

    override fun cancel(id: Long) {
        alarms.cancel(pendingFor(id, ""))
    }

    private fun openApp(id: Long): PendingIntent = ReminderNotifications.openApp(context, id)

    private fun pendingFor(id: Long, text: String): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setData(Uri.parse("bheem-reminder:$id"))
            .putExtra(ReminderReceiver.EXTRA_ID, id)
            .putExtra(ReminderReceiver.EXTRA_TEXT, text)
        return PendingIntent.getBroadcast(context, id.toInt(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

object ReminderNotifications {
    private const val CATCH_UP_ID = 1

    fun openApp(context: Context, id: Long): PendingIntent =
        PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java).setData(Uri.parse("bheem-open:$id")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    fun reminder(context: Context, id: Long, text: String) {
        val notification = Notification.Builder(context, Notifications.REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Reminder")
            .setContentText(text)
            .setContentIntent(openApp(context, id))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(
                Notification.Builder(context, Notifications.REMINDERS)
                    .setSmallIcon(android.R.drawable.ic_popup_reminder)
                    .setContentTitle("Reminder")
                    .build(),
            )
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(id.toInt() + 1_000, notification)
    }

    fun catchUp(context: Context, title: String, text: String) {
        val notification = Notification.Builder(context, Notifications.REMINDERS)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context, 0))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(CATCH_UP_ID, notification)
    }
}

class ReminderServices(private val context: Context, val scheduler: ReminderScheduler) {
    fun alarmFired(id: Long) {
        scheduler.fired(id)?.let { ReminderNotifications.reminder(context, it.id, it.text) }
    }

    fun rearm(nowMs: Long = System.currentTimeMillis()) {
        val report = scheduler.rearm(nowMs, ZoneId.systemDefault())
        if (report.missed.isNotEmpty()) {
            val notice = CatchUp.notice(report.missed)
            ReminderNotifications.catchUp(context, notice.title, notice.text)
        }
    }
}
