package app.bheem.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

data class ResolvedTime(val date: LocalDate, val time: LocalTime?, val fallbackTime: LocalTime? = null) {
    fun toDateTime(default: LocalTime?): LocalDateTime? = (time ?: default ?: fallbackTime)?.let { date.atTime(it) }
}

object TimeResolver {
    private val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)
    private val weekdays = DayOfWeek.entries.associateBy { it.name.lowercase() }

    private val offset = Regex("""\bin (half an|an|a|\d+|[a-z]+(?: [a-z]+)?) (minute|minutes|min|mins|hour|hours|hr|hrs|day|days|week|weeks)\b""")
    private val weekday = Regex("""\b(?:(next|this|on) )?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""")
    private val colonTime = Regex("""\b(\d{1,2}):(\d{2})(?: ?(am|pm))?\b""")
    private val markedHour = Regex("""\b(\d{1,2}) ?(am|pm)\b""")
    private val hourAtNight = Regex("""\b(?:at )?(\d{1,2}) tonight\b|\btonight at (\d{1,2})\b""")

    private val unreadableDate = Regex(
        """\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?""" +
            """|next (?:week|month|year)|this (?:week|month|year)|weekend|christmas|diwali|eid|holi|new year)\b|\b\d{1,2}(?:st|nd|rd|th)\b|\b\d{1,2}[/-]\d{1,2}\b""",
        RegexOption.IGNORE_CASE,
    )

    fun hasUnreadableDate(text: String): Boolean = unreadableDate.containsMatchIn(text) && !mentionsDate(text)

    fun mentionsDate(text: String): Boolean {
        val t = normalize(text)
        return offset.containsMatchIn(t) || dateOf(t, LocalDate.of(2000, 1, 3)) != null
    }

    fun resolve(text: String, now: LocalDateTime): ResolvedTime? {
        val t = normalize(text)
        if (t.isEmpty() || hasUnreadableDate(text)) return null
        offset.find(t)?.let { m -> offsetFrom(m, now)?.let { return it.withClock(t) } }
        val date = dateOf(t, now.toLocalDate())
        if (date == null) {
            val clock = clockTime(t) ?: return null
            val today = now.toLocalDate()
            return ResolvedTime(if (clock.isAfter(now.toLocalTime())) today else today.plusDays(1), clock)
        }
        return ResolvedTime(date, clockTime(t))
    }

    private fun ResolvedTime.withClock(t: String): ResolvedTime = if (time != null) this else copy(time = clockTime(t))

    private fun offsetFrom(m: MatchResult, now: LocalDateTime): ResolvedTime? {
        val (amount, unit) = m.destructured
        if (amount == "half an") return if (unit.startsWith("h")) at(now.plusMinutes(30)) else null
        val count = when (amount) {
            "a", "an" -> 1
            else -> amount.toIntOrNull() ?: numberWords(amount) ?: return null
        }.toLong()
        return when {
            unit.startsWith("min") -> at(now.plusMinutes(count))
            unit.startsWith("h") -> at(now.plusHours(count))
            unit.startsWith("d") -> ResolvedTime(now.toLocalDate().plusDays(count), null, nowTime(now))
            else -> ResolvedTime(now.toLocalDate().plusWeeks(count), null, nowTime(now))
        }
    }

    private fun nowTime(now: LocalDateTime): LocalTime = now.toLocalTime().truncatedTo(ChronoUnit.MINUTES)

    private fun at(moment: LocalDateTime): ResolvedTime {
        val minute = moment.truncatedTo(ChronoUnit.MINUTES)
        return ResolvedTime(minute.toLocalDate(), minute.toLocalTime())
    }

    private fun numberWords(s: String): Int? {
        val words = s.split(' ')
        return when (words.size) {
            1 -> units[words[0]] ?: tens[words[0]]
            2 -> tens[words[0]]?.let { ten -> units[words[1]]?.takeIf { it in 1..9 }?.let { ten + it } }
            else -> null
        }
    }

    private fun dateOf(t: String, today: LocalDate): LocalDate? {
        if (Regex("""\bday after tomorrow\b""").containsMatchIn(t)) return today.plusDays(2)
        if (Regex("""\btomorrow\b""").containsMatchIn(t)) return today.plusDays(1)
        if (Regex("""\b(today|tonight)\b""").containsMatchIn(t)) return today
        val m = weekday.find(t) ?: return null
        val target = weekdays.getValue(m.groupValues[2])
        val ahead = (target.value - today.dayOfWeek.value + 7) % 7
        return today.plusDays(
            when (m.groupValues[1]) {
                "this" -> ahead.toLong()
                else -> if (ahead == 0) 7L else ahead.toLong()
            },
        )
    }

    private fun clockTime(t: String): LocalTime? {
        colonTime.find(t)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].toInt()
            val marker = m.groupValues[3]
            return when {
                minute !in 0..59 -> null
                marker.isNotEmpty() -> twelveHour(hour, minute, marker)
                hour in 0..23 -> LocalTime.of(hour, minute)
                else -> null
            }
        }
        markedHour.find(t)?.let { m -> return twelveHour(m.groupValues[1].toInt(), 0, m.groupValues[2]) }
        hourAtNight.find(t)?.let { m ->
            val hour = (m.groupValues[1].ifEmpty { m.groupValues[2] }).toInt()
            return if (hour in 1..11) LocalTime.of(hour + 12, 0) else null
        }
        return null
    }

    private fun twelveHour(hour: Int, minute: Int, marker: String): LocalTime? {
        if (hour !in 1..12) return null
        val h = hour % 12 + if (marker == "pm") 12 else 0
        return LocalTime.of(h, minute)
    }

    private fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("""\b([ap])\.m\.?"""), "$1m")
            .replace(Regex("""[^a-z0-9: ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
