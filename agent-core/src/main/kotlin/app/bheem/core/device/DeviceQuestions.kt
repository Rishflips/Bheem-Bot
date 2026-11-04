package app.bheem.core.device

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Questions the phone itself can answer: the time, the date, the battery, whether it is online, and sums. Answered by code from a
 * [DeviceSnapshot], so they are instant, always right for this moment, and never made up by a model. Only a message that is nothing
 * but one of these questions matches; anything else returns null and goes the normal way.
 */
object DeviceQuestions {
    fun answer(text: String, phone: DeviceSnapshot): String? {
        val q = normalise(text)
        if (q.isEmpty()) return null
        return time(q, phone) ?: dateAndDay(q, phone) ?: daysUntil(q, phone) ?: battery(q, phone) ?: network(q, phone) ?: doNotDisturb(q, phone)
            ?: storage(q, phone) ?: alarm(q, phone) ?: sum(q)
    }

    private fun normalise(text: String): String {
        var q = text.lowercase(Locale.ENGLISH).replace("’", "").replace("'", "")
            .replace("wi-fi", "wifi").replace("wi fi", "wifi")
        q = q.replace(Regex("""(?<=\d),(?=\d{3})"""), "")
        q = q.replace(Regex("""\s+"""), " ").trim().trimEnd('?', '!', '.', ' ')
        for (prefix in listOf("hey bheem ", "bheem ", "please ", "can you tell me ", "could you tell me ", "tell me ")) {
            if (q.startsWith(prefix) && q.length > prefix.length + 3) q = q.removePrefix(prefix)
        }
        return q.removeSuffix(" please")
    }

    private val timeAsk = Regex("""^(what time is it( right now| now| currently)?|(what is|whats|what) the time( right now| now)?|current time|time now|do you know the time|the time)$""")
    private val dateAsk = Regex("""^(what is todays date|whats todays date|what is the date( today)?|whats the date( today)?|what date is it( today)?|todays date|the date today|what is the date)$""")
    private val dayAsk = Regex("""^(what day is it( today)?|which day is it( today)?|which day is today|what day is today|what is the day today|whats the day today|what day of the week is it|what is today)$""")
    private val relativeAsk = Regex("""^(what day is (it )?(tomorrow|yesterday)|what day was yesterday|what day will it be tomorrow|what is (tomorrows|yesterdays) date|whats (tomorrows|yesterdays) date|what is the date (tomorrow|yesterday)|what was yesterdays date|what date is (tomorrow|yesterday))$""")

    private val dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val alarmFormat = DateTimeFormatter.ofPattern("EEEE d MMMM 'at' h:mm a", Locale.ENGLISH)

    private fun time(q: String, phone: DeviceSnapshot): String? = if (timeAsk.matches(q)) "It is ${phone.now.format(clock)}." else null

    private fun dateAndDay(q: String, phone: DeviceSnapshot): String? {
        val today = phone.now.toLocalDate()
        return when {
            dateAsk.matches(q) -> "Today is ${today.format(dayFormat)}."
            dayAsk.matches(q) -> "Today is ${today.format(dayFormat)}."
            relativeAsk.matches(q) ->
                if ("yesterday" in q) "Yesterday was ${today.minusDays(1).format(dayFormat)}." else "Tomorrow is ${today.plusDays(1).format(dayFormat)}."
            else -> null
        }
    }

    private val untilAsk = Regex("""^(?:how many days (?:are there |is there |are left |left )?(?:until|till|to|before)|days (?:until|till|left until|left till|left for|left to)) (.+)$""")

    private val months = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    private fun monthOf(word: String): Int? {
        val w = word.trim('.')
        if (w.length < 3) return null
        val index = months.indexOfFirst { it == w || it.startsWith(w) && (w.length == 3 || w == "sept") }
        return if (index >= 0) index + 1 else null
    }

    private val dayMonth = Regex("""^(\d{1,2})(?:st|nd|rd|th)? ([a-z.]+)(?: (\d{4}))?$""")
    private val monthDay = Regex("""^([a-z.]+) (\d{1,2})(?:st|nd|rd|th)?(?:,? (\d{4}))?$""")

    private fun daysUntil(q: String, phone: DeviceSnapshot): String? {
        val target = untilAsk.matchEntire(q)?.groupValues?.get(1)?.removePrefix("the ") ?: return null
        val parts = dayMonth.matchEntire(target)?.let { Triple(it.groupValues[1], it.groupValues[2], it.groupValues[3]) }
            ?: monthDay.matchEntire(target)?.let { Triple(it.groupValues[2], it.groupValues[1], it.groupValues[3]) }
            ?: return null
        val month = monthOf(parts.second) ?: return null
        val today = phone.now.toLocalDate()
        val date = try {
            val year = parts.third.toIntOrNull()
            var d = LocalDate.of(year ?: today.year, month, parts.first.toInt())
            if (year == null && d.isBefore(today)) d = d.plusYears(1)
            d
        } catch (e: DateTimeException) {
            return null
        }
        val days = ChronoUnit.DAYS.between(today, date)
        val label = date.format(dayFormat)
        return when {
            days == 0L -> "That is today, $label."
            days < 0L -> "$label was ${-days} ${if (days == -1L) "day" else "days"} ago."
            else -> "$days ${if (days == 1L) "day" else "days"} until $label."
        }
    }

    private val batteryAsk = Regex("""^(how much (battery|charge)\b.*|(what is|whats) my battery\b.*|battery (level|percentage|percent|left)|my battery)$""")
    private val chargingAsk = Regex("""^(is (my )?(phone|it) (charging|plugged in|on charge)|am i charging)$""")

    private fun battery(q: String, phone: DeviceSnapshot): String? {
        val percent = phone.batteryPercent
        if (batteryAsk.matches(q)) {
            if (percent == null) return "I cannot read the battery right now."
            return when (phone.charging) {
                true -> "Battery is at $percent%, and it is charging."
                false -> "Battery is at $percent%, and it is not charging."
                null -> "Battery is at $percent%."
            }
        }
        if (chargingAsk.matches(q)) {
            val level = if (percent != null) " ($percent%)" else ""
            return when (phone.charging) {
                true -> "Yes, it is charging$level."
                false -> "No, it is not charging$level."
                null -> "I cannot tell if it is charging."
            }
        }
        return null
    }

    private val networkAsk = Regex("""^(am i online|am i connected( to (the )?internet)?|do i have (internet|wifi|network|data)|is (the )?(internet|wifi|data) on|am i on wifi|is my wifi on)$""")

    private fun network(q: String, phone: DeviceSnapshot): String? {
        if (!networkAsk.matches(q)) return null
        return when (phone.network) {
            Network.WIFI -> "Your phone is connected to Wi-Fi."
            Network.MOBILE -> "Your phone is using mobile data."
            Network.OFFLINE -> "Your phone is offline."
            Network.UNKNOWN -> "I cannot read the network state right now."
        }
    }

    private val dndAsk = Regex("""^(is (do not disturb|dnd|dont disturb)( mode)? (on|off|active|enabled)|(do not disturb|dnd)( on)?|am i on do not disturb)$""")

    private fun doNotDisturb(q: String, phone: DeviceSnapshot): String? {
        if (!dndAsk.matches(q)) return null
        return when (phone.doNotDisturb) {
            true -> "Do not disturb is on."
            false -> "Do not disturb is off."
            null -> "I cannot read the do not disturb setting right now."
        }
    }

    private val storageAsk = Regex("""^(how much (free )?(space|storage)\b.*|(free|storage) (space|storage|left)|(what is|whats) my storage)$""")

    private fun storage(q: String, phone: DeviceSnapshot): String? {
        if (!storageAsk.matches(q)) return null
        val free = phone.storageFreeGb ?: return "I cannot read the storage right now."
        val total = phone.storageTotalGb
        return if (total != null) "$free GB free of $total GB." else "$free GB free."
    }

    private val alarmAsk = Regex("""^(what is my next alarm|whats my next alarm|when is my next alarm|when is my alarm|when does my alarm ring|is (an|my) alarm set|do i have an alarm( set)?|next alarm)$""")

    private fun alarm(q: String, phone: DeviceSnapshot): String? {
        if (!alarmAsk.matches(q)) return null
        val at = phone.nextAlarm ?: return "You have no alarm set."
        return "Your next alarm is ${at.format(alarmFormat)}."
    }

    private val number = """(\d+(?:\.\d+)?)"""
    private val percentOf = Regex("""^(?:what is |whats |calculate |compute )?$number ?(?:percent|%) of $number$""")
    private val binary = Regex("""^(?:what is |whats |calculate |compute )?(-?\d+(?:\.\d+)?) ?(plus|\+|minus|-|times|x|\*|multiplied by|divided by|/|over) ?(-?\d+(?:\.\d+)?)$""")

    private fun plain(value: BigDecimal): String =
        value.setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()

    private fun sum(q: String): String? {
        percentOf.matchEntire(q)?.let {
            val rate = BigDecimal(it.groupValues[1])
            val base = BigDecimal(it.groupValues[2])
            return "${plain(rate)}% of ${plain(base)} is ${plain(rate.multiply(base).divide(BigDecimal(100)))}."
        }
        val m = binary.matchEntire(q) ?: return null
        val a = BigDecimal(m.groupValues[1])
        val b = BigDecimal(m.groupValues[3])
        val op = m.groupValues[2]
        val (word, result) = when (op) {
            "plus", "+" -> "plus" to a.add(b)
            "minus", "-" -> "minus" to a.subtract(b)
            "times", "x", "*", "multiplied by" -> "times" to a.multiply(b)
            else -> {
                if (b.signum() == 0) return "I cannot divide by zero."
                "divided by" to a.divide(b, 6, RoundingMode.HALF_UP)
            }
        }
        return "${plain(a)} $word ${plain(b)} is ${plain(result)}."
    }
}
