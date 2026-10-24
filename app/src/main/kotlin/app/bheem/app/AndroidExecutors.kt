package app.bheem.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import app.bheem.core.action.ProposedAction
import app.bheem.core.proposal.ActionExecutor
import app.bheem.core.proposal.ExecutionResult
import java.time.LocalDateTime
import java.time.ZoneId

class AndroidReminderExecutor(private val context: Context) : ActionExecutor {
    override val handles = "CreateReminder"

    private val alarms = context.getSystemService(AlarmManager::class.java)

    override suspend fun execute(action: ProposedAction): ExecutionResult {
        val at = LocalDateTime.parse(action.args.getValue("time")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val code = (action.args.getValue("text") + at).hashCode()
        val pending = pendingFor(action.args.getValue("text"), code)
        if (alarms.canScheduleExactAlarms()) {
            alarms.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp()), pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
        return ExecutionResult.Done(code.toString())
    }

    override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean {
        val code = undoToken?.toIntOrNull() ?: return false
        alarms.cancel(pendingFor(action.args.getValue("text"), code))
        return true
    }

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    private fun pendingFor(text: String, code: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_TEXT, text)
            .putExtra(ReminderReceiver.EXTRA_CODE, code)
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}

class AndroidCalendarExecutor(private val context: Context) : ActionExecutor {
    override val handles = "CreateCalendarEvent"

    override suspend fun execute(action: ProposedAction): ExecutionResult {
        val start = LocalDateTime.parse(action.args.getValue("start")).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, action.args.getValue("title"))
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + ONE_HOUR_MS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return launch(context, intent, "no calendar app")
    }

    override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean = false

    private companion object {
        const val ONE_HOUR_MS = 3_600_000L
    }
}

class AndroidMessageExecutor(private val context: Context) : ActionExecutor {
    override val handles = "DraftMessage"

    override suspend fun execute(action: ProposedAction): ExecutionResult {
        val text = action.args.getValue("text")
        val intent = if (action.args.getValue("channel") == "sms") {
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", text)
        } else {
            Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text).setPackage(WHATSAPP)
        }
        return launch(context, intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), "the messaging app is not installed")
    }

    override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean = false

    private companion object {
        const val WHATSAPP = "com.whatsapp"
    }
}

private fun launch(context: Context, intent: Intent, missing: String): ExecutionResult =
    try {
        context.startActivity(intent)
        ExecutionResult.Done(null)
    } catch (_: ActivityNotFoundException) {
        ExecutionResult.Failed(missing)
    }
