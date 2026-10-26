package app.bheem.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object Notifications {
    const val REMINDERS = "reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(REMINDERS, context.getString(R.string.notification_channel_reminders), NotificationManager.IMPORTANCE_HIGH))
    }
}
