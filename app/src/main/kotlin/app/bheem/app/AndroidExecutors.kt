package app.bheem.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import app.bheem.core.action.ProposedAction
import app.bheem.core.proposal.ActionExecutor
import app.bheem.core.proposal.ExecutionResult
import app.bheem.core.reminder.ReminderScheduler
import java.time.LocalDateTime
import java.time.ZoneId

class AndroidReminderExecutor(private val scheduler: ReminderScheduler) : ActionExecutor {
    override val handles = "CreateReminder"

    override suspend fun execute(action: ProposedAction): ExecutionResult = ExecutionResult.Failed("a reminder needs its proposal id")

    override suspend fun execute(action: ProposedAction, proposalId: String): ExecutionResult {
        val zone = ZoneId.systemDefault()
        val at = LocalDateTime.parse(action.args.getValue("time")).atZone(zone).toInstant().toEpochMilli()
        return try {
            val scheduled = scheduler.schedule(proposalId, action.args.getValue("text"), at, zone.id)
            ExecutionResult.Done(scheduled.reminder.id.toString(), scheduled.note)
        } catch (e: SecurityException) {
            ExecutionResult.Failed("Android did not allow Bheem to set the alarm")
        }
    }

    override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean {
        val id = undoToken?.toLongOrNull() ?: return false
        return scheduler.cancel(id)
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
