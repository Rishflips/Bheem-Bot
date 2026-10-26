package app.bheem.eval.speech

import app.bheem.core.speech.TranscriptCleaner
import app.bheem.models.Wav
import java.nio.file.Files
import java.nio.file.Paths

class SamplesTranscriber(
    override val id: String,
    private val clock: () -> Long,
    private val recognize: (FloatArray, Int) -> String,
) : Transcriber {
    override suspend fun transcribe(audioPath: String): Transcription {
        val audio = Wav.read(Files.readAllBytes(Paths.get(audioPath))).requireSpeechFormat()
        val started = clock()
        val raw = recognize(audio.toFloatSamples(), audio.sampleRate)
        val elapsed = clock() - started
        return Transcription(TranscriptCleaner.clean(raw), raw, firstChunkMs = elapsed, totalMs = elapsed)
    }
}
