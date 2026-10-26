package app.bheem.core.planner

object PreRouter {
    enum class Route { Model, NoAction, Memory }

    private val ignoreCase = setOf(RegexOption.IGNORE_CASE)
    private val timeWords = Regex("""\b(today|tomorrow|tonight|next|monday|tuesday|wednesday|thursday|friday|saturday|sunday|am|pm|morning|evening|noon)\b|\d""", ignoreCase)

    private val notAssistantWork = listOf(
        Regex("""^(please )?play\b""", ignoreCase),
        Regex("""^(please )?(call|dial|phone) [\p{L}' ]{1,30}$""", ignoreCase),
    )

    private val alwaysNotAssistantWork = listOf(
        Regex("""^(please )?(explain|translate|define|summari[sz]e)\b""", ignoreCase),
        Regex("""^(what('s| is)|how('s| is)) the weather\b""", ignoreCase),
        Regex("""^tell me a joke\b""", ignoreCase),
    )

    private val aboutMyOwnRecords = listOf(
        Regex("""^(where|what) (did|do) i (keep|put|leave|save|park|note|write|add|spend|pay)\b""", ignoreCase),
        Regex("""^what (notes|tasks|reminders) (did|do|have) i\b""", ignoreCase),
        Regex("""^which (notes|tasks|reminders)\b.*\b(i|my)\b""", ignoreCase),
        Regex("""^(show|list) (me )?(all |everything )?(my |i |of )?(notes|tasks|reminders|spent|saved)\b""", ignoreCase),
        Regex("""^show me everything (i|my)\b""", ignoreCase),
        Regex("""^how many (tasks|notes|reminders)\b""", ignoreCase),
        Regex("""^what (was|is) the .*\b(noted|saved|wrote)\b""", ignoreCase),
    )

    fun route(text: String): Route {
        val line = text.trim().trimEnd('.', '?', '!', ' ').replace(Regex("""\s+"""), " ")
        if (alwaysNotAssistantWork.any { it.containsMatchIn(line) }) return Route.NoAction
        if (aboutMyOwnRecords.any { it.containsMatchIn(line) }) return Route.Memory
        if (notAssistantWork.any { it.containsMatchIn(line) } && !timeWords.containsMatchIn(line)) return Route.NoAction
        return Route.Model
    }
}
