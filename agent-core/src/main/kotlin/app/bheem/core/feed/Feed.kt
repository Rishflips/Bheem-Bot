package app.bheem.core.feed

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.proposal.StoredProposal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Described(val title: String, val detail: String)

object ActionDescriber {
    private val moment = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

    private fun channelName(channel: String) = when (channel) {
        "whatsapp" -> "WhatsApp"
        "sms" -> "SMS"
        else -> channel
    }

    fun describe(a: ProposedAction): Described {
        val args = a.args
        fun arg(key: String) = args[key].orEmpty()
        fun at(key: String) = LocalDateTime.parse(arg(key)).format(moment)
        return when (a.action) {
            "CreateReminder" -> Described("Reminder", "${arg("text")}, ${at("time")}")
            "CreateCalendarEvent" -> Described("Calendar event", "${arg("title")}, ${at("start")}")
            "CreateTask" -> Described("Task", arg("text"))
            "SaveNote" -> Described("Note", arg("text"))
            "LogExpense" -> Described("Expense", "₹${arg("amount")} at ${arg("merchant")}")
            "DraftMessage" -> Described("Message to ${arg("to")} on ${channelName(arg("channel"))}", arg("text"))
            "OpenUpiPayment" -> Described("Pay ${arg("payee")}", "₹${arg("amount")}")
            else -> Described(a.action, args.values.joinToString(", "))
        }
    }
}

enum class FeedStatus { PENDING, DONE, FAILED }

data class FeedItem(
    val id: String,
    val title: String,
    val detail: String,
    val status: FeedStatus,
    val level: RiskLevel,
    val canUndo: Boolean,
    val failure: String?,
    val createdAt: LocalDateTime,
)

object Feed {
    private const val MAX_ITEMS = 20

    fun items(proposals: List<StoredProposal>, ledger: Ledger, now: LocalDateTime, undoWindowSeconds: Long): List<FeedItem> =
        proposals.mapNotNull { p ->
            val last = ledger.history(p.id).lastOrNull() ?: return@mapNotNull null
            val status = when (last.event) {
                LedgerEvent.PROPOSED, LedgerEvent.APPROVED -> FeedStatus.PENDING
                LedgerEvent.EXECUTED -> FeedStatus.DONE
                LedgerEvent.FAILED -> FeedStatus.FAILED
                else -> return@mapNotNull null
            }
            val d = ActionDescriber.describe(p.action)
            FeedItem(
                p.id, d.title, d.detail, status, p.level,
                canUndo = ledger.canUndo(p.id, now, undoWindowSeconds),
                failure = last.detail.takeIf { status == FeedStatus.FAILED && it.isNotEmpty() },
                createdAt = p.createdAt,
            )
        }
            .sortedWith(compareBy<FeedItem> { it.status != FeedStatus.PENDING }.thenByDescending { it.createdAt })
            .take(MAX_ITEMS)
}
