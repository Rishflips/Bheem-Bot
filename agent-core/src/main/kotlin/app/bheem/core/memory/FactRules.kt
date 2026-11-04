package app.bheem.core.memory


/**
 * Finds the owner's own statements about themselves ("remember that my sister is called Meena", "I live in Chennai") and their requests to
 * forget one. Plain code over the owner's own sentence: the fact is the owner's words, never something a model wrote. A request to be
 * reminded, or a plan for a particular day, is not a fact.
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
    private val fromNowOn = Regex("""^from now on,? (.{3,200})$""", ignore)
    /** "remember that ..." makes a fact only when it is about the owner; "remember that Suresh is allergic to peanuts" stays a note, and "remember to ..." is a reminder. */
    private val aboutTheOwner = Regex("""^(my|i|i'm|im|we|our|call me)\b""", ignore)
    private val eventWords =Regex("""\b(today|tomorrow|tonight|monday|tuesday|wednesday|thursday|friday|saturday|sunday|coming|arriving|visiting|leaving|calling|picking)\b""", ignore)

    fun remember(text: String): String? {
        val q = text.trim().trimEnd('.', '!', ' ')
        remember.matchEntire(q)?.let { m ->
            val fact = m.groupValues[1].trim()
            return if (aboutTheOwner.containsMatchIn(fact)) fact else null
        }
        relation.matchEntire(q)?.let { m -> return if (eventWords.containsMatchIn(m.groupValues[2])) null else q }
        if (callMe.matches(q) || iAm.matches(q) || iLike.matches(q) || fromNowOn.matches(q)) return q
        return null
    }

    private val forget = Regex("""^(?:please )?forget(?: that| about)? (.{2,120})$""", ignore)

    fun forgetQuery(text: String): String? = forget.matchEntire(text.trim().trimEnd('.', '!', ' '))?.groupValues?.get(1)?.trim()
}
