package app.bheem.core.brief

import app.bheem.core.memory.Citation
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.reminder.Reminder
import app.bheem.core.reminder.ReminderState
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class WaitingCard(val id: String, val title: String, val since: LocalDateTime)

/** Everything the brief reads. Every item here already came from the owner's own words; nothing from a notification or shared text. */
data class BriefInput(val reminders: List<Reminder>, val openTasks: List<MemoryRecord>, val waiting: List<WaitingCard>)

data class BriefSection(val title: String, val lines: List<String>)

data class BriefResult(val text: String, val headline: String, val sections: List<BriefSection>, val citations: List<Citation>, val count: Int)

/** The daily brief: a pure function of the time and the records, composed by code, with no model and no untrusted input. */
object Brief {
    private val day = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val stamp = DateTimeFormatter.ofPattern("EEE d MMM h:mm a", Locale.ENGLISH)
    private val short = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private const val CLASH_MINUTES = 30L
    private const val TASK_DAYS = 3L
    private const val CARD_HOURS = 24L

    fun compose(now: LocalDateTime, zone: ZoneId, input: BriefInput): BriefResult {
        fun at(r: Reminder) = Instant.ofEpochMilli(r.fireAtEpochMs).atZone(ZoneId.of(r.zoneId.ifBlank { zone.id })).toLocalDateTime()
        val citations = mutableListOf<Citation>()
        val sections = mutableListOf<BriefSection>()

        val today = input.reminders.filter { it.state == ReminderState.SCHEDULED && at(it).toLocalDate() == now.toLocalDate() }.sortedBy { it.fireAtEpochMs }
        if (today.isNotEmpty()) {
            sections += BriefSection("Due today", today.map { "${clock.format(at(it))} ${it.text}" })
            citations += today.map { Citation(it.proposalId, "reminder", at(it)) }
        }

        val clashes = today.zipWithNext().filter { (a, b) -> Duration.between(at(a), at(b)).toMinutes() <= CLASH_MINUTES }
        if (clashes.isNotEmpty()) {
            sections += BriefSection(
                "Clashes",
                clashes.map { (a, b) -> "${clock.format(at(a))} ${a.text} and ${clock.format(at(b))} ${b.text} are ${Duration.between(at(a), at(b)).toMinutes()} minutes apart" },
            )
        }

        val missed = input.reminders.filter { it.state == ReminderState.MISSED }.sortedBy { it.fireAtEpochMs }
        if (missed.isNotEmpty()) {
            sections += BriefSection("Missed", missed.map { "${it.text} (was due ${stamp.format(at(it))})" })
            citations += missed.map { Citation(it.proposalId, "reminder", at(it)) }
        }

        val oldTasks = input.openTasks.filter { it.doneAt == null && Duration.between(it.createdAt, now).toDays() >= TASK_DAYS }.sortedBy { it.createdAt }
        val oldCards = input.waiting.filter { Duration.between(it.since, now).toHours() >= CARD_HOURS }.sortedBy { it.since }
        if (oldTasks.isNotEmpty() || oldCards.isNotEmpty()) {
            sections += BriefSection(
                "Not followed up",
                oldTasks.map { "${it.text} (task from ${short.format(it.createdAt)})" } + oldCards.map { "${it.title} (waiting for your tap since ${short.format(it.since)})" },
            )
            citations += oldTasks.map { Citation(it.id, "task", it.createdAt) }
            citations += oldCards.map { Citation(it.id, "card", it.since) }
        }

        val header = "Your day, ${day.format(now)}:"
        val text = if (sections.isEmpty()) "$header\nNothing is due and nothing is waiting." else header + "\n" + sections.joinToString("\n") { s -> s.title + ":\n" + s.lines.joinToString("\n") { "- $it" } }
        val parts = buildList {
            if (today.isNotEmpty()) add(count(today.size, "reminder", "reminders"))
            if (clashes.isNotEmpty()) add(count(clashes.size, "clash", "clashes"))
            if (missed.isNotEmpty()) add("${missed.size} missed")
            if (oldTasks.size + oldCards.size > 0) add("${oldTasks.size + oldCards.size} to follow up")
        }
        val headline = "Your morning: " + if (parts.isEmpty()) "nothing due" else parts.joinToString(", ")
        return BriefResult(text, headline, sections, citations, today.size + clashes.size + missed.size + oldTasks.size + oldCards.size)
    }

    /** The cards waiting for a tap that came from the owner's own words; a card from any other source is not part of the brief. */
    fun waitingCards(items: List<app.bheem.core.feed.FeedItem>, trusted: (String) -> Boolean): List<WaitingCard> =
        items.filter { it.status == app.bheem.core.feed.FeedStatus.PENDING && trusted(it.id) }.map { WaitingCard(it.id, "${it.title}: ${it.detail}", it.createdAt) }

    private fun count(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
}
