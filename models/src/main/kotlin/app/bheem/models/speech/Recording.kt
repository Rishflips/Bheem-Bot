package app.bheem.models.speech

import app.bheem.models.WavWriter
import kotlin.math.abs
import kotlin.math.sqrt

class RecordingBuffer(maxMillis: Long = DEFAULT_MAX_MILLIS, private val sampleRate: Int = SAMPLE_RATE) {
    private val capacity = (maxMillis * sampleRate / 1000).toInt()
    private val data = ShortArray(capacity)
    private var size = 0

    val isFull: Boolean get() = size >= capacity

    val durationMillis: Long get() = size * 1000L / sampleRate

    /** Adds the first [length] samples of [chunk]; returns false once the cap has been reached. */
    fun append(chunk: ShortArray, length: Int): Boolean {
        val take = minOf(length, chunk.size, capacity - size)
        chunk.copyInto(data, size, 0, take)
        size += take
        return size < capacity
    }

    fun samples(): ShortArray = data.copyOf(size)

    companion object {
        const val SAMPLE_RATE = 16_000
        const val DEFAULT_MAX_MILLIS = 10_000L
    }
}

object Levels {
    private const val LOUD = 5_000f

    fun level(chunk: ShortArray, length: Int): Float {
        val n = minOf(length, chunk.size)
        if (n == 0) return 0f
        var sum = 0.0
        for (i in 0 until n) sum += chunk[i].toDouble() * chunk[i]
        return (sqrt(sum / n).toFloat() / LOUD).coerceIn(0f, 1f)
    }
}

sealed interface Utterance {
    class Captured(val wav: ByteArray) : Utterance

    data object TooShort : Utterance

    data object Silent : Utterance
}

object Utterances {
    private const val MIN_MILLIS = 400L
    private const val MIN_PEAK = 500

    fun of(samples: ShortArray): Utterance {
        if (samples.size * 1000L / RecordingBuffer.SAMPLE_RATE < MIN_MILLIS) return Utterance.TooShort
        if (samples.maxOf { abs(it.toInt()) } < MIN_PEAK) return Utterance.Silent
        return Utterance.Captured(WavWriter.speech(samples))
    }
}
