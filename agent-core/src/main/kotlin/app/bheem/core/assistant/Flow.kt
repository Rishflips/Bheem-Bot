package app.bheem.core.assistant

import app.bheem.core.proposal.ApprovalResult

private const val JOBS = "remind|note|add|save|put|book|schedule|tell|message|email|create|set"

/** Verbs that start a command of their own after a plain "and". "message" and "tell" do not: "remind me to pay the bill and message Ravi" is one reminder. */
private const val FRESH_JOBS = "remind|note|add|save|put|book|schedule|create|set"

/** Splits one sentence that holds several jobs ("remind me ... and note ...") into one sentence per job, so each goes through the normal single-action path. */
object CompoundSplitter {
    private const val MIN_WORDS = 2
    private const val MAX_JOBS = 5
    private val joiner = Regex(
        """\s*(?:,\s*)?(?:\b(?:and then|and also|then also|then|also)\b\s+(?=(?:$JOBS)\b)|(?:\band\b\s+|,\s+)(?=(?:$FRESH_JOBS)\b))""",
        RegexOption.IGNORE_CASE,
    )
    private val startsWithAJob = Regex("""^(?:please\s+)?(?:$JOBS)\b""", RegexOption.IGNORE_CASE)

    fun split(text: String): List<String>? {
        val parts = text.trim().trimEnd('.', '!', '?').split(joiner).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size < 2 || parts.size > MAX_JOBS) return null
        if (parts.any { it.split(Regex("""\s+""")).size < MIN_WORDS || !startsWithAJob.containsMatchIn(it) }) return null
        return parts
    }
}

/** Joins a short answer ("5 pm") to the command that was waiting for it ("remind me to call Arun"). Only time words and plain filler count as an answer. */
object Clarifier {
    private const val MAX_WORDS = 6
    private val timeWords = setOf(
        "today", "tomorrow", "tonight", "morning", "evening", "afternoon", "night", "noon", "midnight", "am", "pm", "next", "this", "weekend", "week", "day",
        "hour", "hours", "minute", "minutes", "min", "mins", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "after", "before",
    )
    private val filler = setOf("at", "on", "in", "by", "around", "about", "for", "the", "a", "an", "and", "of", "to", "oclock")
    private val number = Regex("""\d{1,2}([:.]\d{2})?(am|pm)?""", RegexOption.IGNORE_CASE)

    fun combine(pending: String, reply: String): String? {
        val words = reply.trim().lowercase().split(Regex("""\s+""")).map { it.trim('.', ',', '!', '?').replace("'", "") }.filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_WORDS) return null
        if (words.any { it !in timeWords && it !in filler && !number.matches(it) }) return null
        if (words.none { it in timeWords || number.matches(it) }) return null
        return "${pending.trim()} ${reply.trim()}"
    }
}

object FollowUp {
    private val yes = setOf("yes", "yeah", "yep", "ok", "okay", "sure", "confirm", "do it", "go ahead", "do all", "yes please", "ok do it", "please do", "approve", "approve all")

    fun isConfirmation(text: String): Boolean = text.trim().trimEnd('.', '!', ' ').lowercase() in yes
}

data class PlanResult(val done: List<String>, val stoppedAt: String?, val reason: String?)

/**
 * "Do all": the owner's one tap approves the steps of a plan in order, each through the same approval as a tap on its own card, so the gate
 * re-checks every step. It stops after a step that hands over to another app, and at the first failure; the steps left over still wait for a tap.
 */
class PlanRunner(private val approve: suspend (String) -> ApprovalResult, private val isHandOff: (String) -> Boolean) {
    suspend fun approveAll(ids: List<String>): PlanResult {
        val done = mutableListOf<String>()
        for ((index, id) in ids.withIndex()) {
            when (val result = approve(id)) {
                ApprovalResult.Executed, ApprovalResult.Undone -> {
                    done += id
                    if (isHandOff(id)) {
                        val next = ids.getOrNull(index + 1) ?: return PlanResult(done, null, null)
                        return PlanResult(done, next, "waiting for you after a hand-off")
                    }
                }
                is ApprovalResult.Failed -> return PlanResult(done, id, result.reason)
                is ApprovalResult.Refused -> return PlanResult(done, id, result.reason)
            }
        }
        return PlanResult(done, null, null)
    }
}
