package app.bheem.core.gate

import app.bheem.core.action.ActionCatalog
import app.bheem.core.action.ProposedAction
import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class Provenance { USER, TAINTED }

enum class RiskLevel { READ, L0, L1, L2 }

data class GateRequest(
    val proposal: ProposedAction,
    val provenance: Provenance,
    val userText: String,
    val source: String,
    val now: LocalDateTime,
)

sealed interface GateDecision {
    data class Propose(val action: ProposedAction, val level: RiskLevel, val requiresTap: Boolean) : GateDecision

    data object Nothing : GateDecision

    data class Reject(val reason: String) : GateDecision
}

data class GatePolicy(val tapForEveryAction: Boolean = true)

class Gate(private val policy: GatePolicy = GatePolicy(), private val limiter: SourceRateLimiter? = null) {
    fun decide(request: GateRequest, rateLimited: Boolean = true): GateDecision {
        val proposal = request.proposal
        if (proposal.action == "NoAction") return GateDecision.Nothing
        if (proposal.action == "OpenUpiPayment") return reject("payments come later")
        val keys = ActionCatalog.required[proposal.action] ?: return reject("unknown action")
        val tainted = request.provenance == Provenance.TAINTED
        if (tainted && proposal.action !in fromUntrustedText) return reject("${proposal.action} is not allowed from untrusted text")
        if (rateLimited && limiter != null && !limiter.tryAcquire(request.source, request.now)) return reject("rate limit exceeded for ${request.source}")

        val args = mutableMapOf<String, String>()
        for (key in keys) {
            val value = proposal.args[key]?.trim().orEmpty()
            if (value.isEmpty()) return reject("missing arg $key")
            when (val c = checked(proposal.action, key, value, request.now)) {
                is Checked.Ok -> args[key] = c.value
                is Checked.Bad -> return reject(c.reason)
            }
        }
        if (proposal.action == "DraftMessage") groundingProblem(args, request.userText)?.let { return reject(it) }

        val level = levelOf(proposal.action, tainted)
        val tap = level == RiskLevel.L1 || level == RiskLevel.L2 || (level == RiskLevel.L0 && policy.tapForEveryAction)
        return GateDecision.Propose(ProposedAction(proposal.action, args), level, tap)
    }

    private sealed interface Checked {
        data class Ok(val value: String) : Checked

        data class Bad(val reason: String) : Checked
    }

    private fun reject(reason: String) = GateDecision.Reject(reason)

    private fun levelOf(action: String, tainted: Boolean): RiskLevel = when (action) {
        "AnswerFromMemory" -> RiskLevel.READ
        "DraftMessage" -> RiskLevel.L1
        else -> if (tainted) RiskLevel.L1 else RiskLevel.L0
    }

    private fun checked(action: String, key: String, value: String, now: LocalDateTime): Checked = when (key) {
        "amount" -> amount(value)
        "direction" -> oneOf(value.lowercase(), directions, "direction must be debit or credit")
        "channel" -> oneOf(value.lowercase(), channels, "channel must be whatsapp or sms")
        "time", "start" -> moment(key, value, now)
        else -> plainText(key, value)
    }

    private fun oneOf(value: String, allowed: Set<String>, reason: String): Checked = if (value in allowed) Checked.Ok(value) else Checked.Bad(reason)

    private fun plainText(key: String, value: String): Checked = when {
        value.any { it.isISOControl() } -> Checked.Bad("$key has control characters")
        value.length > (maxLength[key] ?: DEFAULT_MAX) -> Checked.Bad("$key is too long")
        else -> Checked.Ok(value)
    }

    private fun amount(value: String): Checked {
        if (!amountShape.matches(value)) return Checked.Bad("amount is not a valid number")
        val number = BigDecimal(value)
        if (number.signum() <= 0 || number > MAX_EXPENSE) return Checked.Bad("amount is out of range")
        return Checked.Ok(number.setScale(2).toPlainString())
    }

    private fun moment(key: String, value: String, now: LocalDateTime): Checked {
        val parsed = try {
            LocalDateTime.parse(value).truncatedTo(ChronoUnit.MINUTES)
        } catch (_: DateTimeException) {
            return Checked.Bad("$key is not a valid date and time")
        }
        return when {
            parsed.isBefore(now.truncatedTo(ChronoUnit.MINUTES)) -> Checked.Bad("$key is in the past")
            parsed.isAfter(now.plusYears(2)) -> Checked.Bad("$key is too far ahead")
            else -> Checked.Ok(parsed.toString())
        }
    }

    private fun groundingProblem(args: Map<String, String>, userText: String): String? {
        val said = tokens(userText)
        val recipient = tokens(args.getValue("to"))
        val at = if (recipient.isEmpty() || recipient.size > MAX_NAME_TOKENS) -1 else indexOfRun(said, recipient)
        if (at < 0) return "recipient is not in the command"
        val wording = tokens(args.getValue("text"))
        val content = wording.filter { it !in functionWords }
        val saidSet = said.toSet()
        if (content.isEmpty() || content.any { it !in saidSet }) return "message text is not from the command"
        if (negated(said.drop(at + recipient.size)) != negated(wording)) return "message text is not from the command"
        return null
    }

    private fun indexOfRun(haystack: List<String>, needle: List<String>): Int =
        (0..haystack.size - needle.size).firstOrNull { start -> needle.indices.all { haystack[start + it] == needle[it] } } ?: -1

    private fun negated(tokens: List<String>) = tokens.any { it in negators }

    private fun tokens(s: String): List<String> = s.lowercase().split(Regex("""[^\p{L}\p{M}\p{N}]+""")).filter { it.isNotEmpty() }

    private companion object {
        val fromUntrustedText = setOf("LogExpense", "CreateReminder")
        val directions = setOf("debit", "credit")
        val channels = setOf("whatsapp", "sms")
        val maxLength = mapOf("text" to 500, "query" to 500, "title" to 200, "to" to 80, "merchant" to 80)
        val amountShape = Regex("""\d{1,8}(\.\d{1,2})?""")
        val MAX_EXPENSE = BigDecimal("10000000")
        const val DEFAULT_MAX = 200
        val functionWords = setOf(
            "a", "an", "the", "is", "am", "are", "was", "were", "be", "been", "will", "ll", "m", "s", "re", "ve", "d", "would",
            "to", "of", "in", "on", "at", "for", "by", "with", "and", "or", "so", "that", "this", "please",
        )
        val negators = setOf("not", "no", "never", "t", "cannot", "nothing", "without")
        const val MAX_NAME_TOKENS = 3
    }
}
