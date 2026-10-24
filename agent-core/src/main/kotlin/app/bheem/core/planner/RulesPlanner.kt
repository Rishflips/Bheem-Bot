package app.bheem.core.planner

import app.bheem.core.pipeline.Planner
import app.bheem.core.time.TimeResolver
import app.bheem.models.host.ModelUnavailable
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

object RulesPlanner {
    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)
    private val reminderLead = Regex("""^(?:please )?(?:remind me|set (?:a )?reminder)(?: to| that)? (.+)$""", ignoreCase)
    private val noteLead = Regex("""^(?:please )?(?:take a note|make a note|save a note|note down|jot down|note)(?: that|:)? (.+)$""", ignoreCase)
    private val taskLead = Regex("""^(?:please )?(?:add a task|add task|new task|add a todo|task|todo|to-do)(?: to|:)? (.+)$""", ignoreCase)
    private val connector = Regex("""\b(?:at|on|in|by|tomorrow|today|tonight|next|this|after)\b""", ignoreCase)
    private val dateCue = Regex(
        "\\b(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|june?|july?|aug(?:ust)?|sep(?:t(?:ember)?)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?" +
            "|next (?:week|month|year)|this (?:week|month|year)|weekend|christmas|diwali|eid|holi|new year|noon|midnight)\\b|\\b\\d{1,2}(?:st|nd|rd|th)\\b|\\b\\d{1,2}[/-]\\d{1,2}\\b",
        ignoreCase,
    )
    private val leadingTime = Regex("""^((?:at|on|in|by|tomorrow|today|tonight|next|this|after)\b.*?) to (.+)$""", ignoreCase)

    fun plan(text: String, now: LocalDateTime): String? {
        val line = text.replace(Regex("""\s+"""), " ").trim()
        reminderLead.matchEntire(line)?.let { return reminder(it.groupValues[1], now) }
        noteLead.matchEntire(line)?.let { return "SaveNote|${clean(it.groupValues[1])}" }
        taskLead.matchEntire(line)?.let { return "CreateTask|${clean(it.groupValues[1])}" }
        return null
    }

    private fun reminder(body: String, now: LocalDateTime): String? {
        if (dateCue.containsMatchIn(body) && TimeResolver.resolve(body, now) == null) return null
        var text = body
        val phrases = mutableListOf<String>()
        leadingTime.matchEntire(body)?.let { m ->
            if (moment(m.groupValues[1], now) != null) {
                phrases += m.groupValues[1]
                text = m.groupValues[2]
            }
        }
        val cutAt = trailingTimeStart(text, now)
        if (cutAt != null) {
            phrases += text.substring(cutAt)
            text = text.substring(0, cutAt)
        }
        val time = if (phrases.isEmpty()) null else moment(phrases.joinToString(" "), now)
        return "CreateReminder|${clean(text)}|${time ?: ""}"
    }

    private fun trailingTimeStart(text: String, now: LocalDateTime): Int? {
        val starts = connector.findAll(text).map { it.range.first }.filter { it > 0 }.toList()
        var start = starts.lastOrNull { moment(text.substring(it), now) != null } ?: return null
        while (true) {
            val earlier = starts.lastOrNull { it < start && TimeResolver.resolve(text.substring(it, start), now) != null } ?: return start
            start = earlier
        }
    }

    private fun moment(phrase: String, now: LocalDateTime): LocalDateTime? {
        TimeResolver.resolve(phrase, now)?.toDateTime(default = null)?.let { return it }
        val today = TimeResolver.resolve("today $phrase", now)?.toDateTime(default = null) ?: return null
        if (today.isAfter(now)) return today
        return TimeResolver.resolve("tomorrow $phrase", now)?.toDateTime(default = null)
    }

    private fun clean(value: String): String =
        value.replace('|', ' ').replace(Regex("""\s+"""), " ").trim().trimEnd('.', '!', '?', ',', ' ')
}

enum class PlanSource { Model, Rules }

class RulesFallbackPlanner(private val primary: Planner) : Planner {
    @Volatile
    var lastSource: PlanSource = PlanSource.Model
        private set

    override suspend fun plan(userText: String, now: LocalDateTime): String =
        try {
            primary.plan(userText, now).also { lastSource = PlanSource.Model }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ModelUnavailable) {
            (RulesPlanner.plan(userText, now) ?: throw e).also { lastSource = PlanSource.Rules }
        }
}
