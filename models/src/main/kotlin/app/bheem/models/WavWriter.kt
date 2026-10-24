package app.bheem.models

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavWriter {
    private const val HEADER = 44

    fun speech(samples: ShortArray): ByteArray = encode(samples, 16_000)

    fun encode(samples: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteBuffer.allocate(HEADER + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
        out.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(HEADER - 8 + dataBytes)
        out.put("WAVE".toByteArray(Charsets.US_ASCII))
        out.put("fmt ".toByteArray(Charsets.US_ASCII)).putInt(16).putShort(1).putShort(1)
        out.putInt(sampleRate).putInt(sampleRate * 2).putShort(2).putShort(16)
        out.put("data".toByteArray(Charsets.US_ASCII)).putInt(dataBytes)
        for (sample in samples) out.putShort(sample)
        return out.array()
    }
}
