package app.bheem.eval.speech

import app.bheem.models.AudioClip
import app.bheem.models.AudioForm
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class GemmaAudioTranscriberTest {
    private class CapturingEngine(private val reply: String) : LlmEngine {
        var request: GenerationRequest? = null

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            this.request = request
            return Completion(reply, firstChunkMs = 640, totalMs = 1900, chunkCount = 12)
        }

        override suspend fun unload() = Unit
    }

    @Test
    fun sendsTheClipWithTheTranscriptionPromptAndReturnsACleanTranscript() = runBlocking {
        val engine = CapturingEngine("Transcript: \"pay Ravi 840 rupees\"")
        val result = GemmaAudioTranscriber(engine, AudioForm.FILE).transcribe("/sdcard/a/S01-cmd-233-q.wav")
        val sent = checkNotNull(engine.request)
        assertEquals(AudioClip("/sdcard/a/S01-cmd-233-q.wav", AudioForm.FILE, beforeText = true), sent.audio)
        assertEquals(TranscriptionPrompts.english.system, sent.system)
        assertEquals(TranscriptionPrompts.english.user, sent.user)
        assertEquals(0.0, sent.temperature)
        assertEquals("pay Ravi 840 rupees", result.text)
        assertEquals("Transcript: \"pay Ravi 840 rupees\"", result.raw)
        assertEquals(640L, result.firstChunkMs)
        assertEquals(1900L, result.totalMs)
    }

    @Test
    fun theFormAndThePositionOfTheAudioAreSettings() = runBlocking {
        val engine = CapturingEngine("ok")
        GemmaAudioTranscriber(engine, AudioForm.PCM_BYTES, audioFirst = false).transcribe("/x.wav")
        assertEquals(AudioClip("/x.wav", AudioForm.PCM_BYTES, beforeText = false), engine.request?.audio)
    }

    @Test
    fun eachFormHasItsOwnId() {
        val engine = CapturingEngine("ok")
        val ids = AudioForm.entries.map { GemmaAudioTranscriber(engine, it).id }
        assertEquals(listOf("gemma-audio-file", "gemma-audio-wav_bytes", "gemma-audio-pcm_bytes"), ids)
        assertEquals("gemma-audio-file-textfirst", GemmaAudioTranscriber(engine, AudioForm.FILE, audioFirst = false).id)
    }

    @Test
    fun theEnglishPromptAsksForOnlyTheTranscriptionWithDigits() {
        val prompt = TranscriptionPrompts.english
        assertEquals("transcribe-v1", prompt.version)
        for (word in listOf("Transcribe", "English", "no newlines", "digits")) assertContains(prompt.user, word)
    }
}
