package app.bheem.core.memory


/**
 * Finds the owner's own statements about themselves ("remember that my sister is called Meena", "I live in Chennai") and their requests to
 * forget one. Plain code over the owner's own sentence: the fact is the owner's words, never something a model wrote. Only lasting statements
 * count: something that happened, a plan for a day, or a request ("remind me", "add a task") is not a fact and goes on to the normal routing.
 */
object FactRules {
    private val ignore = setOf(RegexOption.IGNORE_CASE)
    private val remember = Regex("""^(?:please )?remember(?: that)? (.{3,300})$""", ignore)
    private val relation = Regex(
        """^my (name|birthday|sister|brother|mother|mom|mum|father|dad|wife|husband|partner|son|daughter|boss|manager|friend|best friend|dog|cat|favou?rite [a-z ]{3,20}) (?:is|are|was) (.{1,200})$""",
        ignore,
    )
    private val callMe = Regex("""^call me (.{1,60})$""", ignore)
    private val iAm = Regex("""^i (?:live in|stay in|work at|work in|work for|study at|am from|am allergic to) (.{1,100})$""", ignore)
    private val iLike = Regex("""^i (?:like|love|prefer|hate|dislike|enjoy) (.{2,100})$""", ignore)
    private val routine = Regex("""^i (?:usually|always|never|often|take|drink|eat|wake|sleep|go|use|ride|drive|walk|travel|commute)\b.{2,100}$""", ignore)
    private val fromNowOn = Regex("""^from now on,? (.{3,200})$""", ignore)
    private val requestOrEvent = Regex(
        """\b(remind|reminder|task|note|add|schedule|tell|send|message|text|book|today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|""" +
            """coming|arriving|visiting|leaving|calling|picking|lent|gave|paid|owe|owes|parked|bought|borrowed|met|saw|left|dropped|forgot|meeting|appointment)\b""",
        ignore,
    )

    fun remember(text: String): String? {
        val q = text.trim().trimEnd('.', '!', ' ')
        remember.matchEntire(q)?.let { m ->
            val fact = m.groupValues[1].trim()
            return if (lasting(fact)) fact else null
        }
        return if (lasting(q)) q else null
    }

    private fun lasting(sentence: String): Boolean {
        if (requestOrEvent.containsMatchIn(sentence)) return false
        return relation.matches(sentence) || callMe.matches(sentence) || iAm.matches(sentence) || iLike.matches(sentence) ||
            routine.matches(sentence) || fromNowOn.matches(sentence)
    }

    private val forget = Regex("""^(?:please )?forget(?: that| about)? (.{2,120})$""", ignore)

    fun forgetQuery(text: String): String? = forget.matchEntire(text.trim().trimEnd('.', '!', ' '))?.groupValues?.get(1)?.trim()
}
