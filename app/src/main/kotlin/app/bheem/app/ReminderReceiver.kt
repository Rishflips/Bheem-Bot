package app.bheem.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class AsyncReceiver : BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    protected abstract suspend fun handle(context: Context, intent: Intent)
}

class ReminderReceiver : AsyncReceiver() {
    override suspend fun handle(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1)
        if (id < 0) {
            val legacy = intent.getStringExtra(EXTRA_TEXT) ?: return
            ReminderNotifications.reminder(context, intent.getIntExtra("code", 0).toLong(), legacy)
            return
        }
        val services = runCatching { ReminderServices.lightweight(context) }.getOrNull()
        if (services != null) {
            services.alarmFired(id)
        } else {
            ReminderNotifications.reminder(context, id, intent.getStringExtra(EXTRA_TEXT).orEmpty().ifBlank { "Reminder" })
        }
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TEXT = "text"
    }
}

class RearmReceiver : AsyncReceiver() {
    override suspend fun handle(context: Context, intent: Intent) {
        ReminderServices.lightweight(context).rearm()
    }
}
