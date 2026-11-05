package app.bheem.core.memory

/** The display groups on the What Bheem knows screen. Display only: a fact still means exactly what the owner said. */
enum class FactKind { ABOUT_YOU, PEOPLE, PREFERENCES, ROUTINE, GOALS, OTHER }

/**
 * Facts that can have only one current value. A new fact in the same slot can offer to replace the old one; everything
 * else (a sister, a preference, a routine) may have many values at once.
 */
object FactSlots {
    private val ignore = setOf(RegexOption.IGNORE_CASE)
    private val name = Regex("""^(?:my name is|call me)\b""", ignore)
    private val city = Regex("""^(?:i (?:live|stay) in|i moved to)\b""", ignore)
    private val work = Regex("""^(?:i work (?:at|for|as)|my job is)\b""", ignore)
    private val birthday = Regex("""^my birthday is\b""", ignore)
    private val partner = Regex("""^my (?:wife|husband|partner) is\b""", ignore)
    private val person = Regex(
        """^my (?:sister|brother|mother|mom|mum|father|dad|wife|husband|partner|son|daughter|boss|manager|friend|best friend)\b""",
        ignore,
    )
    private val preference = Regex("""^(?:i (?:like|love|prefer|hate|dislike|enjoy)\b|my (?:favou?rite|favorite)\b)""", ignore)
    private val routine = Regex(
        """^(?:i (?:usually|always|never|often|sometimes|wake|sleep|go|use|ride|drive|walk|travel|commute|take|drink|eat)\b|from now on\b)""",
        ignore,
    )
    private val goal = Regex("""^(?:i (?:want|hope|wish|plan to|am working towards|work towards)\b|my (?:goal|dream)\b)""", ignore)
    private val language = Regex("""^(?:i speak|i know|my languages?)\b""", ignore)

    fun slot(text: String): String? {
        val t = text.trim()
        return when {
            name.containsMatchIn(t) -> "name"
            birthday.containsMatchIn(t) -> "birthday"
            city.containsMatchIn(t) -> "city"
            work.containsMatchIn(t) -> "work"
            partner.containsMatchIn(t) -> "partner"
            else -> null
        }
    }

    fun kind(text: String): FactKind {
        val t = text.trim()
        val lower = t.lowercase()
        return when {
            lower.startsWith("people:") -> FactKind.PEOPLE
            lower.startsWith("preferences:") -> FactKind.PREFERENCES
            lower.startsWith("routine:") -> FactKind.ROUTINE
            lower.startsWith("goals:") -> FactKind.GOALS
            lower.startsWith("languages:") || lower.startsWith("about you:") || lower.startsWith("work:") -> FactKind.ABOUT_YOU
            slot(t) == "partner" -> FactKind.PEOPLE
            slot(t) != null -> FactKind.ABOUT_YOU
            person.containsMatchIn(t) -> FactKind.PEOPLE
            preference.containsMatchIn(t) -> FactKind.PREFERENCES
            routine.containsMatchIn(t) -> FactKind.ROUTINE
            goal.containsMatchIn(t) -> FactKind.GOALS
            language.containsMatchIn(t) -> FactKind.ABOUT_YOU
            else -> FactKind.OTHER
        }
    }
}
