package app.bheem.core.assistant

/** Turns what is on the screen into what is worth saying aloud: no list marks, no source line, whole sentences, a sane length. */
object SpokenText {
    private const val MAX_CHARS = 450
    private val bullet = Regex("""^\s*(?:[-*•]|\d+[.)])\s+""")

    fun of(text: String): String? {
        val lines = text.lines()
            .map { it.filter { c -> !c.isISOControl() || c == '\t' }.replace(bullet, "").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("From:") }
        if (lines.isEmpty()) return null
        val sentences = lines.joinToString(" ") { line -> line.trimEnd(':', ';', ',').let { if (it.last() in ".!?") it else "$it." } }
        return cut(sentences.replace(Regex("""\s+"""), " ").trim())
    }

    private fun cut(text: String): String {
        if (text.length <= MAX_CHARS) return text
        val head = text.take(MAX_CHARS)
        val end = maxOf(head.lastIndexOf(". "), head.lastIndexOf("! "), head.lastIndexOf("? "))
        return if (end > 0) head.substring(0, end + 1) else head.substringBeforeLast(' ') + "."
    }
}
