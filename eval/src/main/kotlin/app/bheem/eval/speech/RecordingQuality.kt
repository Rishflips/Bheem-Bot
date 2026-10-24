package app.bheem.eval.speech

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

enum class RecordingVerdict { OK, TOO_SHORT, TOO_QUIET, CLIPPED }

data class RecordingQualityResult(
    val verdict: RecordingVerdict,
    val durationMs: Long,
    val peak: Float,
    val clippedFraction: Float,
    val speechMs: Long,
) {
    val ok: Boolean get() = verdict == RecordingVerdict.OK
}

/**
 * Speech is taken to be frames of 20 ms that stand clearly above the recording's own noise floor, and at least half a second
 * of them is required. A tap on the screen or a steady hum lasts too short or has no frames above its own floor.
 */
object RecordingQuality {
    private const val MIN_MS = 700L
    private const val FRAME = 320
    private const val FRAME_MS = 20L
    private const val MIN_SPEECH_FRAMES = 25
    private const val ABOVE_FLOOR = 3.0
    private const val MIN_SPEECH_RMS = 150.0
    private const val CLIP_LEVEL = 32_000
    private const val CLIP_FRACTION = 0.01f

    fun check(samples: ShortArray, sampleRate: Int = 16_000): RecordingQualityResult {
        val durationMs = samples.size * 1000L / sampleRate
        val peak = (samples.maxOfOrNull { abs(it.toInt()) } ?: 0) / 32768f
        val clipped = if (samples.isEmpty()) 0f else samples.count { abs(it.toInt()) >= CLIP_LEVEL }.toFloat() / samples.size
        val speechFrames = speechFrames(samples)
        val verdict = when {
            durationMs < MIN_MS -> RecordingVerdict.TOO_SHORT
            clipped > CLIP_FRACTION -> RecordingVerdict.CLIPPED
            speechFrames < MIN_SPEECH_FRAMES -> RecordingVerdict.TOO_QUIET
            else -> RecordingVerdict.OK
        }
        return RecordingQualityResult(verdict, durationMs, peak, clipped, speechFrames * FRAME_MS)
    }

    private fun speechFrames(samples: ShortArray): Int {
        val levels = (0 until samples.size / FRAME).map { frame ->
            var sum = 0.0
            for (i in frame * FRAME until (frame + 1) * FRAME) sum += samples[i].toDouble() * samples[i]
            sqrt(sum / FRAME)
        }
        if (levels.isEmpty()) return 0
        val floor = levels.sorted()[(levels.size * 0.2).toInt()]
        val threshold = max(floor * ABOVE_FLOOR, MIN_SPEECH_RMS)
        return levels.count { it >= threshold }
    }

    fun message(verdict: RecordingVerdict): String = when (verdict) {
        RecordingVerdict.OK -> "Saved."
        RecordingVerdict.TOO_SHORT -> "That was too short. Press and hold the button while you read the whole sentence."
        RecordingVerdict.TOO_QUIET -> "That was too quiet, or no speech was heard. Hold the phone a little closer and speak up."
        RecordingVerdict.CLIPPED -> "That was too loud. Hold the phone further from your mouth."
    }
}
