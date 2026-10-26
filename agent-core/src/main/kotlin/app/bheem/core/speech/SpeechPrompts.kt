package app.bheem.core.speech

import app.bheem.core.parse.ThinkingFilter
import app.bheem.models.speech.Hints

data class TranscriptionPrompt(val version: String, val system: String, val user: String)

object SpeechPrompts {
    val commandWords = listOf("pay", "remind me", "schedule", "message", "save a note", "add a task", "I spent")

    val english = TranscriptionPrompt(
        version = "transcribe-v1",
        system = "You transcribe speech to text.",
        user = "Transcribe the following speech segment in English into English text. " +
            "Only output the transcription, with no newlines. Write numbers as digits, for example 840 and not eight hundred and forty.",
    )

    fun forHints(hints: Hints): TranscriptionPrompt {
        val words = hints.commandWords.filter { it.isNotBlank() }
        val names = hints.names.filter { it.isNotBlank() }
        if (words.isEmpty() && names.isEmpty()) return english
        var user = english.user
        if (words.isNotEmpty()) {
            user += " The speaker is giving a command to a phone assistant. Commands often start with these words: " + words.joinToString(", ") + "."
        }
        if (names.isNotEmpty()) user += " These names may be spoken: " + names.joinToString(", ") + "."
        val version = when {
            names.isEmpty() -> "transcribe-v3-commands"
            words.isEmpty() -> "transcribe-v2-names"
            else -> "transcribe-v4-names-commands"
        }
        return english.copy(version = version, user = user)
    }
}

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
