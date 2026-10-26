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

    fun doneLabel(a: ProposedAction): String = when (a.action) {
        "CreateCalendarEvent" -> "Opened in your calendar"
        "DraftMessage" -> if (a.args["channel"] == "sms") "Opened in your messages" else "Opened in WhatsApp"
        else -> "Done"
    }

    fun reminderTime(a: ProposedAction): LocalDateTime? =
        if (a.action == "CreateReminder") runCatching { LocalDateTime.parse(a.args["time"].orEmpty()) }.getOrNull() else null

    /** Like [describe], but a stored action with an unreadable value still gets a card instead of breaking the whole feed. */
    fun safe(a: ProposedAction): Described = runCatching { describe(a) }.getOrElse { Described(a.action, a.args.values.joinToString(", ")) }

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
    val doneLabel: String = "Done",
    val heardAs: String? = null,
    val dueAt: LocalDateTime? = null,
)

data class FeedRow(val proposal: StoredProposal, val last: LedgerEntry)

interface FeedSource {
    fun rows(limit: Int): List<FeedRow>

    fun rowsFor(action: String, limit: Int): List<FeedRow>
}

/** The newest rows plus every recent reminder, so a reminder set far ahead stays in Upcoming however much newer activity there is. */
fun FeedSource.todayRows(limit: Int, reminderLimit: Int = 200): List<FeedRow> =
    (rows(limit) + rowsFor("CreateReminder", reminderLimit)).distinctBy { it.proposal.id }

class InMemoryFeedSource(private val proposals: InMemoryProposalStore, private val ledger: LedgerStore) : FeedSource {
    override fun rows(limit: Int): List<FeedRow> =
        proposals.all().sortedByDescending { it.createdAt }
            .mapNotNull { p -> ledger.lastEvent(p.id)?.let { FeedRow(p, it) } }
            .take(limit)

    override fun rowsFor(action: String, limit: Int): List<FeedRow> =
        proposals.all().filter { it.action.action == action }.sortedByDescending { it.createdAt }
            .mapNotNull { p -> ledger.lastEvent(p.id)?.let { FeedRow(p, it) } }
            .take(limit)
}

object Feed {
    private const val MAX_ITEMS = 20

    fun items(rows: List<FeedRow>, now: LocalDateTime, undoWindowSeconds: Long): List<FeedItem> {
        val all = rows.mapNotNull { (p, last) ->
            val status = when (last.event) {
                LedgerEvent.PROPOSED -> FeedStatus.PENDING
                LedgerEvent.APPROVED -> FeedStatus.WORKING
                LedgerEvent.EXECUTED -> FeedStatus.DONE
                LedgerEvent.FAILED -> FeedStatus.FAILED
                else -> return@mapNotNull null
            }
            val d = ActionDescriber.safe(p.action)
            FeedItem(
                p.id, d.title, d.detail, status, p.level,
                canUndo = status == FeedStatus.DONE && p.undoToken != null && !now.isAfter(last.at.plusSeconds(undoWindowSeconds)),
                failure = last.detail.takeIf { status == FeedStatus.FAILED && it.isNotEmpty() },
                createdAt = p.createdAt,
                note = last.detail.takeIf { status == FeedStatus.DONE && it.isNotEmpty() },
                doneLabel = ActionDescriber.doneLabel(p.action),
                heardAs = p.userText.takeIf { p.source == "voice" },
                dueAt = ActionDescriber.reminderTime(p.action),
            )
        }
        val (upcoming, others) = all.partition { it.status == FeedStatus.DONE && it.dueAt?.isAfter(now) == true }
        val ordered = others.sortedWith(compareBy<FeedItem> { it.status != FeedStatus.PENDING && it.status != FeedStatus.WORKING }.thenByDescending { it.createdAt })
        return ordered.take(MAX_ITEMS) + upcoming.sortedBy { it.dueAt }
    }
}

/** What the Today screen shows, in three groups. Dismissed and undone items are in none of them; the ledger shows them. */
data class FeedSections(val needsYou: List<FeedItem>, val upcoming: List<FeedItem>, val recent: List<FeedItem>) {
    companion object {
        fun of(items: List<FeedItem>, now: LocalDateTime): FeedSections {
            val needs = items.filter { it.status != FeedStatus.DONE }
            val done = items.filter { it.status == FeedStatus.DONE }
            val (upcoming, recent) = done.partition { it.dueAt?.isAfter(now) == true }
            return FeedSections(needs, upcoming.sortedBy { it.dueAt }, recent)
        }
    }
}

data class LedgerLine(
    val id: String,
    val title: String,
    val detail: String,
    val outcome: String,
    val at: LocalDateTime,
    val viaVoice: Boolean,
    val reason: String?,
)

/** The activity ledger in plain words. Unlike the feed it keeps dismissed, undone and refused items. */
object LedgerLines {
    fun of(rows: List<FeedRow>): List<LedgerLine> = rows.map { (p, last) ->
        val question = p.action.action == "AnswerFromMemory"
        val d = if (question) Described("Question", p.action.args["query"].orEmpty()) else ActionDescriber.safe(p.action)
        LedgerLine(
            p.id, d.title, d.detail, if (question) "Answered from your notes" else outcome(last.event), p.createdAt, p.source == "voice",
            reason = last.detail.takeIf { it.isNotEmpty() && (last.event == LedgerEvent.FAILED || last.event == LedgerEvent.REJECTED) },
        )
    }

    private fun outcome(event: LedgerEvent) = when (event) {
        LedgerEvent.PROPOSED -> "Waiting for your OK"
        LedgerEvent.APPROVED -> "Working on it"
        LedgerEvent.EXECUTED -> "Done"
        LedgerEvent.FAILED -> "Did not work"
        LedgerEvent.UNDONE -> "You undid it"
        LedgerEvent.DISMISSED -> "You dismissed it"
        LedgerEvent.REJECTED -> "Not allowed"
        LedgerEvent.READ -> "Read"
    }
}
