package app.bheem.eval.speech

import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.gen.ReferenceClock
import app.bheem.eval.score.AmountNormalizer
import java.time.LocalDateTime
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

enum class EntityKind { AMOUNT, NAME, TIME }

data class Entity(val kind: EntityKind, val tokens: List<String>)

object EntityExtractor {
    private val nameKeys = listOf("payee", "merchant", "to")
    private val timeKeys = listOf("time", "start", "due")
    internal val properNoun = Regex("""^\p{Lu}\p{Ll}+""")
    internal val calendarWords = setOf(
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
    )

    fun fromCommand(referenceText: String, expected: ExpectedAction): List<Entity> {
        if (expected.action == "NoAction") return emptyList()
        val spoken = SpeechNormalizer.tokens(referenceText)
        val (calendar, names) = capitalizedWords(referenceText, expected)
        return listOfNotNull(amount(expected, spoken)) + times(expected, spoken) +
            calendar.map { Entity(EntityKind.TIME, listOf(it)) } + names
    }

    private fun amount(expected: ExpectedAction, spoken: List<String>): Entity? {
        val value = expected.args["amount"]?.let { AmountNormalizer.parse(it) } ?: return null
        val canonical = value.stripTrailingZeros().toPlainString()
        return if (spoken.any { Numbers.equal(it, canonical) }) Entity(EntityKind.AMOUNT, listOf(canonical)) else null
    }

    private fun times(expected: ExpectedAction, spoken: List<String>): List<Entity> {
        val moment = timeKeys.firstNotNullOfOrNull { expected.args[it] }?.let(::parseMoment) ?: return emptyList()
        val day = when (ChronoUnit.DAYS.between(ReferenceClock.TODAY, moment.toLocalDate())) {
            0L -> listOf("today")
            1L -> listOf("tomorrow")
            2L -> listOf("day", "after", "tomorrow")
            else -> null
        }
        val hour12 = if (moment.hour % 12 == 0) 12 else moment.hour % 12
        val clock = if (moment.minute == 0) "$hour12" else "$hour12:%02d".format(moment.minute)
        val hour = listOf(clock, if (moment.hour < 12) "am" else "pm")
        return listOfNotNull(day, hour).filter { spoken.containsSequence(it) }.map { Entity(EntityKind.TIME, it) }
    }

    private fun parseMoment(iso: String): LocalDateTime? =
        try {
            LocalDateTime.parse(iso, ReferenceClock.isoMinute)
        } catch (_: DateTimeParseException) {
            null
        }

    private fun capitalizedWords(referenceText: String, expected: ExpectedAction): Pair<List<String>, List<Entity>> {
        val spoken = SpeechNormalizer.tokens(referenceText)
        val fromArgs = nameKeys.mapNotNull { expected.args[it] }
            .map { it to SpeechNormalizer.tokens(it) }
            .filter { (_, tokens) -> tokens.isNotEmpty() && spoken.containsSequence(tokens) }
        val covered = fromArgs.flatMap { (raw, _) -> raw.lowercase().split(' ') }.toSet() + listOfNotNull(expected.args["channel"]?.lowercase())
        val calendar = mutableListOf<String>()
        val names = fromArgs.map { (_, tokens) -> Entity(EntityKind.NAME, tokens) }.toMutableList()
        for ((index, word) in referenceText.split(Regex("""\s+""")).withIndex()) {
            val core = word.trim { !it.isLetter() }
            if (index == 0 || !properNoun.containsMatchIn(core) || core.lowercase() in covered) continue
            val tokens = SpeechNormalizer.tokens(core)
            when {
                tokens.isEmpty() -> Unit
                core.lowercase() in calendarWords -> calendar += core.lowercase()
                else -> names += Entity(EntityKind.NAME, tokens)
            }
        }
        return calendar to names
    }
}

internal fun List<String>.containsSequence(sequence: List<String>): Boolean =
    sequence.isNotEmpty() && (0..size - sequence.size).any { start -> subList(start, start + sequence.size) == sequence }

internal object Numbers {
    private val plain = Regex("""\d+(?:\.\d+)?""")

    fun equal(token: String, canonical: String): Boolean =
        plain.matches(token) && token.toBigDecimal().compareTo(canonical.toBigDecimal()) == 0
}
