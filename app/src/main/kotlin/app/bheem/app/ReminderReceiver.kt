package app.bheem.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.bheem.core.reminder.RearmTriggers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class AsyncReceiver : BroadcastReceiver() {
    final override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(context.applicationContext, intent)
            } catch (e: Exception) {
                Log.w("BheemReminders", "receiver failed: ${e::class.java.simpleName}")
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
        if (id < 0) return
        val services = runCatching { ReminderServices.lightweight(context) }.getOrNull()
        if (services == null) {
            ReminderNotifications.reminder(context, id, null)
            return
        }
        services.alarmFired(id)
    }

    companion object {
        const val EXTRA_ID = "reminder_id"
    }
}

class RearmReceiver : AsyncReceiver() {
    override suspend fun handle(context: Context, intent: Intent) {
        val reason = RearmTriggers.reasonFor(intent.action) ?: return
        ReminderServices.lightweight(context).rearm(reason)
    }
}
