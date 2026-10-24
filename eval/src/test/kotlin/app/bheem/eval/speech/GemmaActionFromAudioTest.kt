package app.bheem.eval.speech

import app.bheem.eval.prompt.Prompts
import app.bheem.models.AudioClip
import app.bheem.models.AudioForm
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class GemmaActionFromAudioTest {
    private class CapturingEngine(private val reply: String) : LlmEngine {
        var request: GenerationRequest? = null

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            this.request = request
            return Completion(reply, firstChunkMs = 800, totalMs = 2900, chunkCount = 9)
        }

        override suspend fun unload() = Unit
    }

    @Test
    fun sendsTheClipWithThePlannerPromptAndReturnsTheActionLine() = runBlocking<Unit> {
        val engine = CapturingEngine("  OpenUpiPayment|Ravi|840 \n")
        val transcriber = GemmaActionFromAudio(engine, AudioForm.FILE)
        val result = transcriber.transcribe("/sdcard/a/S03-cmd-233-f.wav")
        val sent = checkNotNull(engine.request)
        assertEquals(Prompts.named("line").command, sent.system)
        assertContains(sent.user, "spoken command")
        assertContains(sent.user, "one line")
        assertEquals(AudioClip("/sdcard/a/S03-cmd-233-f.wav", AudioForm.FILE, beforeText = true), sent.audio)
        assertEquals(0.0, sent.temperature)
        assertEquals("OpenUpiPayment|Ravi|840", result.text)
        assertEquals("  OpenUpiPayment|Ravi|840 \n", result.raw)
        assertEquals(800L, result.firstChunkMs)
        assertEquals(2900L, result.totalMs)
        assertTrue(transcriber.producesAction)
    }

    @Test
    fun theIdEndsWithActionAndCarriesTheTag() {
        val engine = CapturingEngine("NoAction")
        assertEquals("gemma-audio-file-action", GemmaActionFromAudio(engine, AudioForm.FILE).id)
        assertEquals("gemma-audio-file+names-action", GemmaActionFromAudio(engine, AudioForm.FILE, names = listOf("Ravi"), tag = "+names").id)
    }

    @Test
    fun namesAndCommandWordsGoIntoTheUserText() = runBlocking<Unit> {
        val engine = CapturingEngine("NoAction")
        GemmaActionFromAudio(engine, AudioForm.FILE, names = listOf("Ravi", "Big Basket")).transcribe("/x.wav")
        assertContains(checkNotNull(engine.request).user, "Ravi, Big Basket")
        GemmaActionFromAudio(engine, AudioForm.FILE).transcribe("/x.wav")
        assertFalse("Ravi" in checkNotNull(engine.request).user)
    }

    @Test
    fun theOrdinaryTranscribersDoNotProduceActions() {
        val engine = CapturingEngine("ok")
        assertFalse(GemmaAudioTranscriber(engine, AudioForm.FILE).producesAction)
    }
}
