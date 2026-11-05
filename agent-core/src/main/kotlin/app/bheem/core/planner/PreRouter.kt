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

    private val actionCues = Regex(
        """\b(remind|reminder|note|notes|task|tasks|todo|to-do|add|save|put|book|schedule|meeting|event|calendar|alarm|timer|call|dial|ring|message|text|whatsapp|sms|email|mail|send|tell|pay|paid|spent|spend|bought|buy|pick up|cancel|delete|remove|move|reschedule|route|navigate|directions|open|torch|flashlight|volume|list|track|log)\b""",
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

    private const val day = "(today|tonight|tomorrow|this week|next week|monday|tuesday|wednesday|thursday|friday|saturday|sunday)"

    private val aboutMyOwnRecords = listOf(
        Regex("""^(where|what) (did|do) i (keep|put|leave|save|park|note|write|add|spend|pay)\b""", ignoreCase),
        Regex("""^what (notes|tasks) (did|do|have) i\b""", ignoreCase),
        Regex("""^which (notes|tasks)\b.*\b(i|my)\b""", ignoreCase),
        Regex("""^(show|list) (me )?(all |everything )?(my |i |of )?(notes|tasks|spent|saved)\b""", ignoreCase),
        Regex("""^show me everything (i|my)\b""", ignoreCase),
        Regex("""^how many (tasks|notes)\b""", ignoreCase),
        Regex("""^(can you |could you |please )?(check|read|see|look at|go through|open)( all)? my( phone)? (notes|tasks)$""", ignoreCase),
        Regex("""^what (was|is) the .*\b(noted|saved|wrote)\b""", ignoreCase),
        Regex("""^what('s| is| do i have| have i got| am i doing| is there)( on| for| up)?( my)?( schedule| agenda| plate)?( on| for)? $day\b""", ignoreCase),
        Regex("""^(what|which) reminders\b""", ignoreCase),
        Regex("""^what('s| is) (coming up|next)\b""", ignoreCase),
        Regex("""^what('s| is) my (day|week|schedule|agenda)\b""", ignoreCase),
        Regex("""^(give me|show me|read me|what is|what's)( my)?( morning| daily)?( brief| briefing| summary)\b""", ignoreCase),
        Regex("""^(my )?(morning |daily )?(brief|briefing|summary)\b""", ignoreCase),
        Regex("""^(please )?(help me )?(sort out|plan|organi[sz]e|arrange)( out)? (my|the) (day|week|today|tomorrow|weekend|next week|this week|$day)\b""", ignoreCase),
    )

    /**
     * "What is my name", "who are you": questions about the owner or about Bheem. They go to the persona chat, which gets the owner's
     * confirmed facts, never to the notes search (old notes are not facts) and never to a fixed paragraph.
     */
    private val aboutTheOwnerOrBheem = listOf(
        Regex("""^(what('s| is)|whats) (my|your) name( bheem)?$""", ignoreCase),
        Regex("""^who am i$""", ignoreCase),
        Regex("""^do you (know|remember) (my name|who i am|me)$""", ignoreCase),
        Regex("""^what do you (know|remember) about me$""", ignoreCase),
        Regex("""^tell me about (me|myself)$""", ignoreCase),
        Regex("""^where do i (live|work|stay|study)$""", ignoreCase),
        Regex("""^how old am i$""", ignoreCase),
        Regex("""^when is my birthday$""", ignoreCase),
        Regex("""^(who|what) are you( bheem)?$""", ignoreCase),
        Regex("""^what (should|do|can) i call you$""", ignoreCase),
        Regex("""^are you (a bot|a robot|an ai|real|human|a human)$""", ignoreCase),
        Regex("""^(tell me about|what is|what's|whats) your memory$""", ignoreCase),
        Regex("""^what do you (know|remember)$""", ignoreCase),
        Regex("""^what (have i told|did i tell) you( about me| about myself)?$""", ignoreCase),
    )

    private val paymentVerb = Regex("""^(please )?(send|transfer|pay|wire|remit)\b""", ignoreCase)
    private val money = Regex("""(₹|\brs\b|\binr\b|\brupees?\b|\blakhs?\b|\bmoney\b)""", ignoreCase)
    private val payBill = Regex("""^(please )?pay (the |my )?[\p{L} ]{0,30}\bbill\b""", ignoreCase)
    private val aboutAMessage = Regex("""\b(whatsapp|sms|message|text|tell|saying|email)\b""", ignoreCase)

    private val callRequest = Regex("""^(please )?(call|dial|phone|ring) ([\p{L}' ]{1,30})$""", ignoreCase)
    private val routeRequest = Regex("""^(please )?(route|navigate|directions?|take me|drive me|guide me)( to| for)? (.{2,60})$""", ignoreCase)
    private val leadingFiller = Regex("""^(my|the) """, ignoreCase)

    private fun plain(text: String) = text.trim().trimEnd('.', '?', '!', ' ').replace(Regex("""\s+"""), " ")

    private fun isPlainRequest(line: String) = !timeWords.containsMatchIn(line) && !doingSomethingWords.containsMatchIn(line) && !compound.containsMatchIn(line)

    /** "call Amma" with nothing else in it: the name to call, for code to resolve from the owner's own notes. */
    fun callTarget(text: String): String? {
        val line = plain(text)
        val found = callRequest.find(line) ?: return null
        if (!isPlainRequest(line)) return null
        return found.groupValues[3].trim().replace(leadingFiller, "").takeIf { it.isNotEmpty() }
    }

    /** "route to the office": where to go, for the maps hand-off. */
    fun routeTarget(text: String): String? {
        val line = plain(text)
        val found = routeRequest.find(line) ?: return null
        if (!isPlainRequest(line)) return null
        return found.groupValues[4].trim().takeIf { it.isNotEmpty() }
    }

    fun route(text: String): Route {
        val line = text.trim().trimEnd('.', '?', '!', ' ').replace(Regex("""\s+"""), " ")
        if (!aboutAMessage.containsMatchIn(line) && paymentVerb.containsMatchIn(line) && (money.containsMatchIn(line) || payBill.containsMatchIn(line))) return Route.Payment
        if (alwaysNotAssistantWork.any { it.containsMatchIn(line) }) return Route.NoAction
        if (aboutMyOwnRecords.any { it.containsMatchIn(line) } && !compound.containsMatchIn(line)) return Route.Memory
        if (aboutTheOwnerOrBheem.any { it.containsMatchIn(line.replace(",", "")) }) return Route.NoAction
        if (notAssistantWork.any { it.containsMatchIn(line) } && !timeWords.containsMatchIn(line) && !doingSomethingWords.containsMatchIn(line)) return Route.NoAction
        if (!actionCues.containsMatchIn(line) && !timeWords.containsMatchIn(line)) return Route.NoAction
        return Route.Model
    }
}
