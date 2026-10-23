package app.bheem.eval.speech

import app.bheem.models.AudioClip
import app.bheem.models.AudioForm
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine

data class Transcription(val text: String, val raw: String, val firstChunkMs: Long, val totalMs: Long)

interface Transcriber {
    val id: String

    suspend fun transcribe(audioPath: String): Transcription
}

data class TranscriptionPrompt(val version: String, val system: String, val user: String)

object TranscriptionPrompts {
    val english = TranscriptionPrompt(
        version = "transcribe-v1",
        system = "You transcribe speech to text.",
        user = "Transcribe the following speech segment in English into English text. " +
            "Only output the transcription, with no newlines. Write numbers as digits, for example 840 and not eight hundred and forty.",
    )
}

class GemmaAudioTranscriber(
    private val engine: LlmEngine,
    private val form: AudioForm,
    private val audioFirst: Boolean = true,
    private val prompt: TranscriptionPrompt = TranscriptionPrompts.english,
) : Transcriber {
    override val id: String = "gemma-audio-${form.name.lowercase()}" + if (audioFirst) "" else "-textfirst"

    override suspend fun transcribe(audioPath: String): Transcription {
        val completion = engine.complete(
            GenerationRequest(prompt.system, prompt.user, maxOutputTokens = 128, temperature = 0.0, audio = AudioClip(audioPath, form, audioFirst)),
        )
        return Transcription(TranscriptCleaner.clean(completion.text), completion.text, completion.firstChunkMs, completion.totalMs)
    }
}
