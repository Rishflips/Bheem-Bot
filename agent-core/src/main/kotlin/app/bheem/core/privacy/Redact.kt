package app.bheem.core.privacy

/** What may be written where a person's own words must not go: logs and the diagnostics a person can share. */
object Redact {
    const val MAX_REASON = 120

    private val whitespace = Regex("""\s+""")

    /** The length of [text] and nothing of its content. */
    fun describe(text: String): String = "<${text.length} chars>"

    /** A refusal reason made safe to show or share: one line, no control characters, at most [MAX_REASON] characters. */
    fun reason(text: String): String {
        val oneLine = text.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().replace(whitespace, " ")
        if (oneLine.isEmpty()) return "no reason recorded"
        return if (oneLine.length <= MAX_REASON) oneLine else oneLine.take(MAX_REASON) + "…"
    }
}
