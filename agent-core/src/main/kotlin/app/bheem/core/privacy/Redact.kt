package app.bheem.core.privacy

/** What may be written where a person's own words must not go: logs and the diagnostics a person can share. */
object Redact {
    const val MAX_REASON = 120

    private const val READ_LIMIT = MAX_REASON * 4
    private val whitespace = Regex("""\s+""")

    /** The length of [text] and nothing of its content. */
    fun describe(text: String): String = "<${text.length} chars>"

    /**
     * A refusal reason made safe to show or share: one line, no control or formatting characters (bidi overrides and zero-width
     * characters included), at most [MAX_REASON] characters, cut on a code point boundary. Only the start of a long text is read.
     */
    fun reason(text: String): String {
        val kept = StringBuilder()
        for (ch in text.take(READ_LIMIT)) {
            when {
                ch.isISOControl() -> kept.append(' ')
                Character.getType(ch) == Character.FORMAT.toInt() -> Unit
                else -> kept.append(ch)
            }
        }
        val oneLine = kept.toString().trim().replace(whitespace, " ")
        if (oneLine.isEmpty()) return "no reason recorded"
        if (oneLine.length <= MAX_REASON) return oneLine
        var end = MAX_REASON
        if (Character.isHighSurrogate(oneLine[end - 1])) end--
        return oneLine.substring(0, end) + "…"
    }
}
