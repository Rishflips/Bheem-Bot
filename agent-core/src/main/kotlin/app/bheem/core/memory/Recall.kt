package app.bheem.core.memory

import app.bheem.core.brief.Brief
import app.bheem.core.brief.BriefInput
import app.bheem.core.brief.BriefTime
import app.bheem.core.reminder.Reminder
import app.bheem.core.reminder.ReminderStore
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Citation(val recordId: String, val kind: String, val at: LocalDateTime)

data class RecallAnswer(val text: String, val citations: List<Citation>)

/**
 * Answers questions about what the owner told Bheem, from their own records only, composed by code so nothing can be invented.
 * [trusted] says whether a record came from the owner's own words; a record that did not is never returned or cited.
 */
class Recall(
    private val memory: MemoryStore,
    private val reminders: ReminderStore,
    private val zone: ZoneId,
    private val trusted: (String) -> Boolean = { true },
    private val brief: (() -> BriefInput)? = null,
) {
    fun answer(query: String, now: LocalDateTime): RecallAnswer {
        if (query.length > MAX_QUERY) return RecallAnswer("That question is too long for me to search.", emptyList())
        if (query.any { it.isISOControl() && it != '\n' && it != '\t' }) return RecallAnswer("I cannot search that.", emptyList())
        if (PLAN_WORDS.containsMatchIn(query)) {
            val planned = Scope.of(query, now.toLocalDate())
            if (planned != null && planned.days.size > 1) return schedule(planned)
            if (brief != null) {
                val day = planned?.days?.single() ?: now.toLocalDate()
                return composeBrief(if (day == now.toLocalDate()) now else day.atTime(BriefTime.AT))
            }
        }
        val scope = if (isMemoryQuestion(query)) null else Scope.of(query, now.toLocalDate())
        if (scope != null) return schedule(scope)
        if (brief != null && BRIEF_WORDS.containsMatchIn(query)) return composeBrief(now)
        if (!isMemoryQuestion(query) && REMINDERS.containsMatchIn(query)) return upcoming()
        return search(query, now)
    }

    private fun schedule(scope: Scope): RecallAnswer {
        val due = reminderRows().filter { zoned(it).toLocalDate() in scope.days }.sortedBy { it.fireAtEpochMs }
        if (due.isEmpty()) return RecallAnswer(if (scope.days.size == 1) "You have nothing on ${scope.label}." else "You have nothing ${scope.label}.", emptyList())
        val lines = due.joinToString("\n") { "- ${(if (scope.days.size == 1) time else stamp).format(zoned(it))} ${it.text}" }
        return RecallAnswer("${scope.headline}:\n$lines", due.map { citation(it) })
    }

    private fun composeBrief(now: LocalDateTime): RecallAnswer {
        val result = Brief.compose(now, zone, brief!!.invoke())
        return RecallAnswer(result.text, result.citations)
    }

    private fun upcoming(): RecallAnswer {
        val next = reminders.scheduled().filter { trusted(it.proposalId) }.sortedBy { it.fireAtEpochMs }.take(UPCOMING)
        if (next.isEmpty()) return RecallAnswer("You have no upcoming reminders.", emptyList())
        val lines = next.joinToString("\n") { "- ${stamp.format(zoned(it))} ${it.text}" }
        return RecallAnswer("Your upcoming reminders:\n$lines", next.map { citation(it) })
    }

    private fun search(query: String, now: LocalDateTime): RecallAnswer {
        val ask = Ask.answer(query, TrustedView(memory, trusted), now)
        val byId = RecordKind.entries.flatMap { memory.ofKind(it) }.associateBy { it.id }
        val records = ask.citations.mapNotNull { byId[it] }
        val citations = records.map { Citation(it.id, it.kind.name.lowercase(), it.createdAt) }.toMutableList()
        val wanted = MemorySearch.queryWords(query).toSet()
        val hits = if (wanted.isEmpty()) emptyList() else reminderRows()
            .map { r -> r to MemorySearch.queryWords(r.text).toSet().count { it in wanted } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Reminder, Int>> { it.second }.thenBy { it.first.fireAtEpochMs })
            .take(LISTED)
            .map { it.first }
        val foundNothing = ask.citations.isEmpty() && ask.text.startsWith("I found nothing")
        if (foundNothing && hits.isEmpty()) return RecallAnswer("I found nothing about that in your notes, tasks or reminders.", emptyList())
        val reminderLines = hits.joinToString("\n") { "- ${it.text} (reminder, ${stamp.format(zoned(it))})" }
        citations += hits.map { citation(it) }
        val text = when {
            hits.isEmpty() -> ask.text
            foundNothing -> "Here is what I found:\n$reminderLines"
            else -> ask.text + "\n" + reminderLines
        }
        return RecallAnswer(text, citations)
    }

    private fun reminderRows(): List<Reminder> = (reminders.scheduled() + reminders.missed()).filter { trusted(it.proposalId) }

    private fun zoned(r: Reminder): LocalDateTime = Instant.ofEpochMilli(r.fireAtEpochMs).atZone(ZoneId.of(r.zoneId.ifBlank { zone.id })).toLocalDateTime()

    private fun citation(r: Reminder) = Citation(r.proposalId, "reminder", zoned(r))

    private fun isMemoryQuestion(query: String): Boolean = MEMORY_WORDS.containsMatchIn(query)

    /** The records the owner's own words created: a record that fails [trusted] does not exist for the caller. */
    private class TrustedView(private val inner: MemoryStore, private val trusted: (String) -> Boolean) : MemoryStore {
        override fun add(record: MemoryRecord) = inner.add(record)

        override fun remove(id: String): Boolean = inner.remove(id)

        override fun markDone(id: String, at: LocalDateTime?): Boolean = inner.markDone(id, at)

        override fun ofKind(kind: RecordKind): List<MemoryRecord> = inner.ofKind(kind).filter { trusted(it.id) && !(it.kind == RecordKind.TASK && it.doneAt != null) }

        override fun search(query: String, limit: Int): List<MemoryRecord> = inner.search(query, limit).filter { trusted(it.id) }
    }

    private class Scope(val days: Set<LocalDate>, val label: String, val headline: String) {
        companion object {
            private val dayLabel = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
            private val weekdays = DayOfWeek.entries.associateBy { it.name.lowercase() }

            fun of(query: String, today: LocalDate): Scope? {
                val q = query.lowercase()
                fun one(date: LocalDate, lead: String) = Scope(setOf(date), dayLabel.format(date), "$lead${dayLabel.format(date)}")
                return when {
                    Regex("""\bnext week\b""").containsMatchIn(q) -> week(today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY)), "next week")
                    Regex("""\bthis week\b""").containsMatchIn(q) -> week(today, "this week")
                    Regex("""\b(tomorrow)\b""").containsMatchIn(q) -> one(today.plusDays(1), "Tomorrow, ")
                    Regex("""\b(today|tonight)\b""").containsMatchIn(q) -> one(today, "Today, ")
                    else -> weekdays.entries.firstOrNull { Regex("""\b${it.key}\b""").containsMatchIn(q) }
                        ?.let { one(today.with(java.time.temporal.TemporalAdjusters.next(it.value)), "") }
                }
            }

            private fun week(from: LocalDate, label: String): Scope {
                val end = from.with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toSet()
                return Scope(days, label, label.replaceFirstChar { it.uppercase() })
            }
        }
    }

    private companion object {
        const val MAX_QUERY = 500
        const val LISTED = 3
        const val UPCOMING = 8
        val PLAN_WORDS = Regex("""^\s*(please\s+)?(help me\s+)?(sort out|plan|organi[sz]e|arrange)(\s+out)?\s+(my|the)\s+""", RegexOption.IGNORE_CASE)
        val BRIEF_WORDS = Regex("""\b(brief|briefing|my day|summary)\b""", RegexOption.IGNORE_CASE)
        val REMINDERS = Regex("""\breminders?\b""", RegexOption.IGNORE_CASE)
        val time: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
        val stamp: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM h:mm a", Locale.ENGLISH)
        val MEMORY_WORDS = Regex("""\b(note|notes|noted|wrote|write|said|told|saved|save|spend|spent|task|tasks|expense|expenses)\b""", RegexOption.IGNORE_CASE)
    }
}
