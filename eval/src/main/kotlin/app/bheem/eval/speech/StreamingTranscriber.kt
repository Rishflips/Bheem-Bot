package app.bheem.eval.speech

import app.bheem.models.Wav
import java.nio.file.Files
import java.nio.file.Paths

interface AudioStream : AutoCloseable {
    fun add(samples: FloatArray, sampleRate: Int)

    fun finish(): String

    override fun close()
}

class StreamingTranscriber(
    override val id: String,
    private val open: () -> AudioStream,
    private val clock: () -> Long,
    private val pause: (Long) -> Unit,
    private val chunkMs: Int = 100,
) : Transcriber {
    override suspend fun transcribe(audioPath: String): Transcription {
        val audio = Wav.read(Files.readAllBytes(Paths.get(audioPath))).requireSpeechFormat()
        val samples = audio.toFloatSamples()
        val rate = audio.sampleRate
        val chunk = rate * chunkMs / 1000
        open().use { stream ->
            val started = clock()
            var lastDelivered = started
            var audioEndedAt = started
            var from = 0
            while (from < samples.size) {
                val to = minOf(from + chunk, samples.size)
                val exists = started + to * 1000L / rate
                val wait = exists - clock()
                if (wait > 0) pause(wait)
                lastDelivered = clock()
                audioEndedAt = exists
                stream.add(samples.copyOfRange(from, to), rate)
                from = to
            }
            val raw = stream.finish()
            val end = clock()
            return Transcription(TranscriptCleaner.clean(raw), raw, firstChunkMs = end - lastDelivered, totalMs = end - audioEndedAt)
        }
    }
}
