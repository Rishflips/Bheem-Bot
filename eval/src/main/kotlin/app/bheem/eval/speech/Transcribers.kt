package app.bheem.eval.speech

import app.bheem.eval.prompt.Prompts
import app.bheem.models.AudioClip
import app.bheem.models.AudioForm
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine

data class Transcription(val text: String, val raw: String, val firstChunkMs: Long, val totalMs: Long)

interface Transcriber {
    val id: String

    /** True when [transcribe] returns the one-line action itself instead of a transcript. */
    val producesAction: Boolean get() = false

    suspend fun transcribe(audioPath: String): Transcription
}

data class TranscriptionPrompt(val version: String, val system: String, val user: String)

object TranscriptionPrompts {
    val commandWordList = listOf("pay", "remind me", "schedule", "message", "save a note", "add a task", "I spent")

    val english = TranscriptionPrompt(
        version = "transcribe-v1",
        system = "You transcribe speech to text.",
        user = "Transcribe the following speech segment in English into English text. " +
            "Only output the transcription, with no newlines. Write numbers as digits, for example 840 and not eight hundred and forty.",
    )

    fun withNames(names: List<String>): TranscriptionPrompt = withHints(names, commandWords = false)

    fun withHints(names: List<String>, commandWords: Boolean): TranscriptionPrompt {
        if (names.isEmpty() && !commandWords) return english
        var user = english.user
        if (commandWords) {
            user += " The speaker is giving a command to a phone assistant. Commands often start with these words: " +
                commandWordList.joinToString(", ") + "."
        }
        if (names.isNotEmpty()) user += " These names may be spoken: " + names.joinToString(", ") + "."
        val version = when {
            names.isEmpty() -> "transcribe-v3-commands"
            !commandWords -> "transcribe-v2-names"
            else -> "transcribe-v4-names-commands"
        }
        return english.copy(version = version, user = user)
    }
}

class GemmaAudioTranscriber(
    private val engine: LlmEngine,
    private val form: AudioForm,
    private val audioFirst: Boolean = true,
    private val prompt: TranscriptionPrompt = TranscriptionPrompts.english,
    tag: String = "",
) : Transcriber {
    override val id: String = "gemma-audio-${form.name.lowercase()}" + (if (audioFirst) "" else "-textfirst") + tag

    override suspend fun transcribe(audioPath: String): Transcription {
        val completion = engine.complete(
            GenerationRequest(prompt.system, prompt.user, maxOutputTokens = 128, temperature = 0.0, audio = AudioClip(audioPath, form, audioFirst)),
        )
        return Transcription(TranscriptCleaner.clean(completion.text), completion.text, completion.firstChunkMs, completion.totalMs)
    }
}

/** One call from audio to the typed action: the planner prompt is the system prompt and the clip is the user's message. */
class GemmaActionFromAudio(
    private val engine: LlmEngine,
    private val form: AudioForm,
    private val audioFirst: Boolean = true,
    private val names: List<String> = emptyList(),
    tag: String = "",
) : Transcriber {
    override val id: String = "gemma-audio-${form.name.lowercase()}" + (if (audioFirst) "" else "-textfirst") + tag + "-action"

    override val producesAction: Boolean = true

    override suspend fun transcribe(audioPath: String): Transcription {
        val user = "This audio is a spoken command for a phone assistant. Reply with the one line action only." +
            if (names.isEmpty()) "" else " These names may be spoken: " + names.joinToString(", ") + "."
        val completion = engine.complete(
            GenerationRequest(Prompts.named("line").command, user, maxOutputTokens = 64, temperature = 0.0, audio = AudioClip(audioPath, form, audioFirst)),
        )
        return Transcription(completion.text.trim(), completion.text, completion.firstChunkMs, completion.totalMs)
    }
}
