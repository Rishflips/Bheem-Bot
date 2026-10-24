package app.bheem.eval.speech

import app.bheem.core.parse.ThinkingFilter

object TranscriptCleaner {
    private val fenceLine = Regex("""(?m)^[ \t]*```\w*[ \t]*$""")
    private val label = Regex("""^(?:transcript|transcription)\s*:\s*""", RegexOption.IGNORE_CASE)
    private val wrappers = listOf('"' to '"', '“' to '”', '`' to '`')

    fun clean(raw: String): String {
        val withoutFences = fenceLine.replace(ThinkingFilter.strip(raw), "")
        val oneLine = withoutFences.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        return unwrap(label.replace(oneLine, "").trim())
    }

    private fun unwrap(text: String): String {
        for ((open, close) in wrappers) {
            if (text.length >= 2 && text.first() == open && text.last() == close) {
                val inner = text.substring(1, text.length - 1)
                if (open !in inner && close !in inner) return inner.trim()
            }
        }
        return text
    }
}
