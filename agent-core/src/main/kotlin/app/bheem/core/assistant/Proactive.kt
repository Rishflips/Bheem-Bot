package app.bheem.core.assistant

import app.bheem.core.memory.FactKind
import app.bheem.core.memory.FactSlots
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.RecordKind
import app.bheem.core.reminder.Reminder
import app.bheem.core.reminder.ReminderState
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One quiet thing Bheem may say when the app opens. A nudge is only text: it never creates a card or runs an action. */
data class Nudge(
    val kind: Kind,
    val key: String,
    val text: String,
    val questionIndex: Int? = null,
) {
    enum class Kind { CLASH, FOLLOW_UP, GAP }
}

/** Everything the nudge decision reads, all from the owner's own confirmed records. */
data class ProactiveInput(
    val reminders: List<Reminder> = emptyList(),
    val tasks: List<MemoryRecord> = emptyList(),
    val facts: List<String> = emptyList(),
    val keptQuestions: Set<Int> = emptySet(),
)

data class ShownNudge(val key: String, val at: LocalDateTime)

/** The keys already shown and when, kept next to the other preferences. */
data class ProactiveState(val shown: List<ShownNudge> = emptyList()) {
    fun record(nudge: Nudge, at: LocalDateTime): ProactiveState =
        copy(shown = (shown + ShownNudge(nudge.key, at)).filter { !it.at.isBefore(at.minusDays(RETAIN_DAYS)) })

    private companion object {
        const val RETAIN_DAYS = 30L
    }
}

object ProactiveStateCodec {
    private const val FIELD = "\t"

    fun encode(state: ProactiveState): String =
        state.shown.joinToString("\n") { "${it.at}$FIELD${it.key}" }

    fun decode(raw: String): ProactiveState {
        if (raw.isBlank()) return ProactiveState()
        val shown = raw.lineSequence().mapNotNull { line ->
            val parts = line.split(FIELD, limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val at = runCatching { LocalDateTime.parse(parts[0]) }.getOrNull() ?: return@mapNotNull null
            val key = parts[1].trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ShownNudge(key, at)
        }.toList()
        return ProactiveState(shown)
    }
}

/**
 * Picks at most one nudge, in the order the owner finds useful: a reminder clash, an old open task, then a missing
 * interview answer. Silence is the default. Nothing is shown between 22:00 and 07:00, more than three are never shown
 * in a day, and a key is never repeated within seven days.
 */
object Proactive {
    private const val QUIET_FROM_HOUR = 22
    private const val QUIET_UNTIL_HOUR = 7
    private const val MAX_PER_DAY = 3
    private const val REPEAT_DAYS = 7L
    private const val CLASH_WINDOW_MINUTES = 30L
    private const val CLASH_HORIZON_HOURS = 24L
    private const val FOLLOW_UP_DAYS = 3L
    private const val CLASH_PREFIX = "clash:"
    private const val TASK_PREFIX = "task:"
    private const val QUESTION_PREFIX = "question:"

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun next(now: LocalDateTime, input: ProactiveInput, state: ProactiveState): Nudge? {
        if (now.hour >= QUIET_FROM_HOUR || now.hour < QUIET_UNTIL_HOUR) return null
        if (state.shown.count { it.at.toLocalDate() == now.toLocalDate() } >= MAX_PER_DAY) return null
        return clash(now, input.reminders).firstOrNull { allowed(it.key, now, state) }
            ?: followUp(now, input.tasks, state).firstOrNull { allowed(it.key, now, state) }
            ?: gap(now, input.facts, input.keptQuestions, state).firstOrNull { allowed(it.key, now, state) }
    }

    /** The questions whose answers are already among the confirmed facts, used to keep the gap question from repeating. */
    fun answeredQuestions(facts: List<String>): Set<Int> {
        val slots = facts.mapNotNull { FactSlots.slot(it) }.toSet()
        val kinds = facts.map { FactSlots.kind(it) }.toSet()
        val lower = facts.map { it.trim().lowercase() }
        return buildSet {
            if ("name" in slots) add(0)
            if ("city" in slots) add(1)
            if ("work" in slots || lower.any { it.startsWith("work:") }) add(2)
            if (FactKind.PEOPLE in kinds) add(3)
            if (FactKind.ROUTINE in kinds) add(4)
            if (FactKind.PREFERENCES in kinds) add(5)
            if (FactKind.GOALS in kinds) add(6)
            if (lower.any { it.startsWith("languages:") || it.startsWith("i speak") || it.startsWith("i know") || it.startsWith("my language") }) add(7)
        }
    }

    private fun allowed(key: String, now: LocalDateTime, state: ProactiveState): Boolean =
        state.shown.none { it.key == key && it.at.isAfter(now.minusDays(REPEAT_DAYS)) }

    private fun shownKindToday(prefix: String, now: LocalDateTime, state: ProactiveState): Boolean =
        state.shown.any { it.key.startsWith(prefix) && it.at.toLocalDate() == now.toLocalDate() }

    private fun clash(now: LocalDateTime, reminders: List<Reminder>): List<Nudge> {
        val limit = now.plusHours(CLASH_HORIZON_HOURS)
        val upcoming = reminders.filter { it.state == ReminderState.SCHEDULED }.mapNotNull { reminder ->
            val fireAt = fireAt(reminder)
            if (fireAt.isAfter(now) && !fireAt.isAfter(limit)) reminder to fireAt else null
        }.sortedWith(compareBy({ it.second }, { it.first.id }))
        return upcoming.zipWithNext().mapNotNull { (first, second) ->
            if (java.time.Duration.between(first.second, second.second).toMinutes() >= CLASH_WINDOW_MINUTES) return@mapNotNull null
            val a = first.first
            val b = second.first
            Nudge(
                Nudge.Kind.CLASH,
                "$CLASH_PREFIX${a.id}:${b.id}",
                "Heads up: ${a.text.trim()} at ${TIME.format(first.second)} and ${b.text.trim()} at ${TIME.format(second.second)} are close together.",
            )
        }
    }

    private fun followUp(now: LocalDateTime, tasks: List<MemoryRecord>, state: ProactiveState): List<Nudge> {
        if (shownKindToday(TASK_PREFIX, now, state)) return emptyList()
        val cutoff = now.minusDays(FOLLOW_UP_DAYS)
        return tasks.filter { it.kind == RecordKind.TASK && it.doneAt == null && it.createdAt.isBefore(cutoff) }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
            .map { task ->
                val text = task.text.trim().trimEnd('.', '!', '?', ' ')
                Nudge(Nudge.Kind.FOLLOW_UP, "$TASK_PREFIX${task.id}", "Still planning to $text? Say remind me ... and I will set it.")
            }
    }

    private fun gap(now: LocalDateTime, facts: List<String>, keptQuestions: Set<Int>, state: ProactiveState): List<Nudge> {
        if (shownKindToday(QUESTION_PREFIX, now, state)) return emptyList()
        val kept = keptQuestions + answeredQuestions(facts)
        return Interview.QUESTIONS.indices.filter { it !in kept }.map { index ->
            Nudge(Nudge.Kind.GAP, "$QUESTION_PREFIX$index", Interview.QUESTIONS[index], index)
        }
    }

    private fun fireAt(reminder: Reminder): LocalDateTime =
        Instant.ofEpochMilli(reminder.fireAtEpochMs).atZone(ZoneId.of(reminder.zoneId)).toLocalDateTime()
}
