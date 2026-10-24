package app.bheem.core.feed

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.LedgerEntry
import app.bheem.core.ledger.LedgerStore
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.proposal.InMemoryProposalStore
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

enum class FeedStatus { PENDING, WORKING, DONE, FAILED }

data class FeedItem(
    val id: String,
    val title: String,
    val detail: String,
    val status: FeedStatus,
    val level: RiskLevel,
    val canUndo: Boolean,
    val failure: String?,
    val createdAt: LocalDateTime,
    val note: String? = null,
)

data class FeedRow(val proposal: StoredProposal, val last: LedgerEntry)

interface FeedSource {
    fun rows(limit: Int): List<FeedRow>
}

class InMemoryFeedSource(private val proposals: InMemoryProposalStore, private val ledger: LedgerStore) : FeedSource {
    override fun rows(limit: Int): List<FeedRow> =
        proposals.all().sortedByDescending { it.createdAt }
            .mapNotNull { p -> ledger.lastEvent(p.id)?.let { FeedRow(p, it) } }
            .take(limit)
}

object Feed {
    private const val MAX_ITEMS = 20

    fun items(rows: List<FeedRow>, now: LocalDateTime, undoWindowSeconds: Long): List<FeedItem> =
        rows.mapNotNull { (p, last) ->
            val status = when (last.event) {
                LedgerEvent.PROPOSED -> FeedStatus.PENDING
                LedgerEvent.APPROVED -> FeedStatus.WORKING
                LedgerEvent.EXECUTED -> FeedStatus.DONE
                LedgerEvent.FAILED -> FeedStatus.FAILED
                else -> return@mapNotNull null
            }
            val d = ActionDescriber.describe(p.action)
            FeedItem(
                p.id, d.title, d.detail, status, p.level,
                canUndo = status == FeedStatus.DONE && !now.isAfter(last.at.plusSeconds(undoWindowSeconds)),
                failure = last.detail.takeIf { status == FeedStatus.FAILED && it.isNotEmpty() },
                createdAt = p.createdAt,
                note = last.detail.takeIf { status == FeedStatus.DONE && it.isNotEmpty() },
            )
        }
            .sortedWith(compareBy<FeedItem> { it.status != FeedStatus.PENDING && it.status != FeedStatus.WORKING }.thenByDescending { it.createdAt })
            .take(MAX_ITEMS)
}
