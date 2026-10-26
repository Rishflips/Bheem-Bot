package app.bheem.core.planner

object PreRouter {
    enum class Route { Model, NoAction, Memory, Payment }

    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)

    private val timeWords = Regex(
        """\b(today|tomorrow|tonight|later|soon|next|weekend|afternoon|noon|midnight|am|pm|after|before|hours?|minutes?|mins?|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""" +
            """|\bat night\b|\bin the (morning|evening)\b|\d""",
        ignoreCase,
    )

    private val doingSomethingWords = Regex(
        """\b(whatsapp|sms|message|text|tell|send|remind|reminder|add|note|save|put|book|schedule|saying|email)\b""",
        ignoreCase,
    )

    private val compound = Regex("""\b(and|then|also)\s+(add|remind|note|save|put|book|schedule|tell|message|send|create)\b""", ignoreCase)

    private val alwaysNotAssistantWork = listOf(
        Regex("""^(what('s| is)|how('s| is)) the weather\b""", ignoreCase),
        Regex("""^tell me a joke\b""", ignoreCase),
    )

    private val notAssistantWork = listOf(
        Regex("""^(please )?(explain|translate|define)\b""", ignoreCase),
        Regex("""^(please )?play\b""", ignoreCase),
        Regex("""^(please )?(call|dial|phone) [\p{L}' ]{1,30}$""", ignoreCase),
    )

    private val aboutMyOwnRecords = listOf(
        Regex("""^(where|what) (did|do) i (keep|put|leave|save|park|note|write|add|spend|pay)\b""", ignoreCase),
        Regex("""^what (notes|tasks) (did|do|have) i\b""", ignoreCase),
        Regex("""^which (notes|tasks)\b.*\b(i|my)\b""", ignoreCase),
        Regex("""^(show|list) (me )?(all |everything )?(my |i |of )?(notes|tasks|spent|saved)\b""", ignoreCase),
        Regex("""^show me everything (i|my)\b""", ignoreCase),
        Regex("""^how many (tasks|notes)\b""", ignoreCase),
        Regex("""^what (was|is) the .*\b(noted|saved|wrote)\b""", ignoreCase),
    )

    private val paymentVerb = Regex("""^(please )?(send|transfer|pay|wire|remit)\b""", ignoreCase)
    private val money = Regex("""(₹|\brs\b|\binr\b|\brupees?\b|\blakhs?\b|\bmoney\b)""", ignoreCase)
    private val payBill = Regex("""^(please )?pay (the |my )?[\p{L} ]{0,30}\bbill\b""", ignoreCase)
    private val aboutAMessage = Regex("""\b(whatsapp|sms|message|text|tell|saying|email)\b""", ignoreCase)

    fun route(text: String): Route {
        val line = text.trim().trimEnd('.', '?', '!', ' ').replace(Regex("""\s+"""), " ")
        if (!aboutAMessage.containsMatchIn(line) && paymentVerb.containsMatchIn(line) && (money.containsMatchIn(line) || payBill.containsMatchIn(line))) return Route.Payment
        if (alwaysNotAssistantWork.any { it.containsMatchIn(line) }) return Route.NoAction
        if (aboutMyOwnRecords.any { it.containsMatchIn(line) } && !compound.containsMatchIn(line)) return Route.Memory
        if (notAssistantWork.any { it.containsMatchIn(line) } && !timeWords.containsMatchIn(line) && !doingSomethingWords.containsMatchIn(line)) return Route.NoAction
        return Route.Model
    }
}
