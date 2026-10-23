package app.bheem.eval.speech

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFixtures {
    fun wav(sampleRate: Int = 16_000, channels: Int = 1, bits: Int = 16, seconds: Double = 1.0): ByteArray {
        val data = ByteArray((sampleRate * seconds).toInt() * channels * bits / 8) { (it % 7).toByte() }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + data.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * bits / 8)
            putShort((channels * bits / 8).toShort())
            putShort(bits.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(data.size)
        }.array()
        return header + data
    }
}
