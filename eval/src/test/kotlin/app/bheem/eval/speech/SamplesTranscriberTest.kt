package app.bheem.eval.speech

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.runBlocking

class SamplesTranscriberTest {
    private fun wavFile(bytes: ByteArray = WavFixtures.wav(seconds = 1.0)): String {
        val file = createTempDirectory("samples").resolve("take.wav")
        Files.write(file, bytes)
        return file.toString()
    }

    private class Clock(vararg val ticks: Long) {
        private var next = 0

        fun now(): Long = ticks[next++]
    }

    @Test
    fun readsTheSamplesRunsTheRecognizerAndTimesTheCall() = runBlocking {
        var seenSamples = 0
        var seenRate = 0
        val clock = Clock(1_000, 1_740)
        val transcriber = SamplesTranscriber("moonshine-small", clock::now) { samples, rate ->
            seenSamples = samples.size
            seenRate = rate
            "Pay Ravi 840 rupees.\n"
        }
        val result = transcriber.transcribe(wavFile())
        assertEquals("moonshine-small", transcriber.id)
        assertEquals(16_000, seenSamples)
        assertEquals(16_000, seenRate)
        assertEquals(Transcription("Pay Ravi 840 rupees.", "Pay Ravi 840 rupees.\n", firstChunkMs = 740, totalMs = 740), result)
    }

    @Test
    fun linesFromTheRecognizerBecomeOneTranscript() = runBlocking {
        val transcriber = SamplesTranscriber("m", Clock(0, 1)::now) { _, _ -> "pay Ravi\n840 rupees\n" }
        assertEquals("pay Ravi 840 rupees", transcriber.transcribe(wavFile()).text)
    }

    @Test
    fun audioNotInSpeechFormatIsRejectedBeforeTheRecognizerRuns() {
        val transcriber = SamplesTranscriber("m", Clock(0, 1)::now) { _, _ -> error("must not run") }
        val message = assertFailsWith<IllegalArgumentException> {
            runBlocking { transcriber.transcribe(wavFile(WavFixtures.wav(sampleRate = 44_100, channels = 2))) }
        }.message.orEmpty()
        assertContains(message, "16 kHz mono")
    }
}
