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
    fun decide(request: GateRequest): GateDecision {
        val proposal = request.proposal
        if (proposal.action == "NoAction") return GateDecision.Nothing
        val keys = ActionCatalog.required[proposal.action] ?: return reject("unknown action ${proposal.action}")
        val tainted = request.provenance == Provenance.TAINTED
        if (tainted && proposal.action !in fromUntrustedText) return reject("${proposal.action} is not allowed from untrusted text")
        if (limiter != null && !limiter.tryAcquire(request.source, request.now)) return reject("rate limit exceeded for ${request.source}")

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
        "OpenUpiPayment" -> RiskLevel.L2
        else -> if (tainted) RiskLevel.L1 else RiskLevel.L0
    }

    private fun checked(action: String, key: String, value: String, now: LocalDateTime): Checked = when (key) {
        "amount" -> amount(action, value)
        "direction" -> oneOf(value.lowercase(), directions, "direction must be debit or credit")
        "channel" -> oneOf(value.lowercase(), channels, "channel must be whatsapp or sms")
        "time", "start" -> moment(key, value, now)
        "payee" -> payee(value)
        else -> plainText(key, value)
    }

    private fun oneOf(value: String, allowed: Set<String>, reason: String): Checked = if (value in allowed) Checked.Ok(value) else Checked.Bad(reason)

    private fun plainText(key: String, value: String): Checked = when {
        value.any { it.isISOControl() } -> Checked.Bad("$key has control characters")
        value.length > (maxLength[key] ?: DEFAULT_MAX) -> Checked.Bad("$key is too long")
        else -> Checked.Ok(value)
    }

    private fun amount(action: String, value: String): Checked {
        if (!amountShape.matches(value)) return Checked.Bad("amount is not a valid number")
        val number = BigDecimal(value)
        val limit = if (action == "OpenUpiPayment") MAX_PAYMENT else MAX_EXPENSE
        if (number.signum() <= 0 || number > limit) return Checked.Bad("amount is out of range")
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

    private fun payee(value: String): Checked = when {
        value.any { it.isISOControl() } -> Checked.Bad("payee has control characters")
        '@' in value -> if (upiAddress.matches(value)) Checked.Ok(value) else Checked.Bad("payee is not a valid name or UPI address")
        value.length > 80 -> Checked.Bad("payee is too long")
        else -> Checked.Ok(value)
    }

    private fun groundingProblem(args: Map<String, String>, userText: String): String? {
        val said = tokens(userText).toSet()
        if (tokens(args.getValue("to")).any { it !in said }) return "recipient is not in the command"
        val words = tokens(args.getValue("text"))
        if (words.isEmpty() || words.count { it in said }.toDouble() / words.size < MIN_GROUNDED) return "message text is not from the command"
        return null
    }

    private fun tokens(s: String): List<String> = s.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }

    private companion object {
        val fromUntrustedText = setOf("LogExpense", "CreateReminder")
        val directions = setOf("debit", "credit")
        val channels = setOf("whatsapp", "sms")
        val maxLength = mapOf("text" to 500, "query" to 500, "title" to 200, "to" to 80, "merchant" to 80)
        val amountShape = Regex("""\d{1,8}(\.\d{1,2})?""")
        val upiAddress = Regex("""[A-Za-z0-9._-]{2,64}@[A-Za-z][A-Za-z0-9]{1,63}""")
        val MAX_EXPENSE = BigDecimal("10000000")
        val MAX_PAYMENT = BigDecimal("100000")
        const val DEFAULT_MAX = 200
        const val MIN_GROUNDED = 0.5
    }
}
