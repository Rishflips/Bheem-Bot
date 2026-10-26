package app.bheem.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import app.bheem.core.action.ProposedAction
import app.bheem.core.proposal.ActionExecutor
import app.bheem.core.proposal.ExecutionResult
import app.bheem.core.proposal.HandOffLauncher
import app.bheem.core.proposal.HandOffRequest
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

    override suspend fun discard(action: ProposedAction, proposalId: String) {
        scheduler.cancelForProposal(proposalId)
    }
}

class AndroidHandOffLauncher(private val context: Context) : HandOffLauncher {
    override fun open(request: HandOffRequest): Boolean {
        val intent = when (request) {
            is HandOffRequest.Calendar -> {
                val start = request.start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                Intent(Intent.ACTION_INSERT)
                    .setData(CalendarContract.Events.CONTENT_URI)
                    .putExtra(CalendarContract.Events.TITLE, request.title)
                    .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
                    .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + ONE_HOUR_MS)
            }
            is HandOffRequest.Message ->
                if (request.channel == "sms") {
                    Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")).putExtra("sms_body", request.text)
                } else {
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, request.text).setPackage(WHATSAPP)
                }
        }
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val ONE_HOUR_MS = 3_600_000L
        const val WHATSAPP = "com.whatsapp"
    }
}
