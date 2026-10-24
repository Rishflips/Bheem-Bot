package app.bheem.models

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WavTest {
    private fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

    private fun chunk(id: String, body: ByteArray, declared: Long = body.size.toLong()): ByteArray {
        val padding = if (body.size % 2 == 1) 1 else 0
        return le(8 + body.size + padding).apply {
            put(id.toByteArray(Charsets.US_ASCII))
            putInt(declared.toInt())
            put(body)
            if (padding == 1) put(0)
        }.array()
    }

    private fun format(tag: Int, sampleRate: Int, channels: Int, bits: Int, extensible: Boolean = false): ByteArray {
        val body = le(if (extensible) 40 else 16).apply {
            putShort(tag.toShort())
            putShort(channels.toShort())
            putInt(sampleRate)
            putInt(sampleRate * channels * bits / 8)
            putShort((channels * bits / 8).toShort())
            putShort(bits.toShort())
            if (extensible) {
                putShort(22)
                putShort(bits.toShort())
                putInt(4)
                putShort(1)
                put(ByteArray(14))
            }
        }.array()
        return chunk("fmt ", body)
    }

    private fun wav(vararg chunks: ByteArray): ByteArray {
        val body = chunks.fold(ByteArray(0)) { acc, c -> acc + c }
        return le(12).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(body.size + 4)
            put("WAVE".toByteArray(Charsets.US_ASCII))
        }.array() + body
    }

    private fun speech(samples: Int = 16_000) = wav(format(1, 16_000, 1, 16), chunk("data", ByteArray(samples * 2) { 1 }))

    @Test
    fun readsTheFormatAndTheSamples() {
        val audio = Wav.read(speech())
        assertEquals(16_000, audio.sampleRate)
        assertEquals(1, audio.channels)
        assertEquals(16, audio.bitsPerSample)
        assertEquals(32_000, audio.pcm.size)
        assertEquals(1_000L, audio.durationMs)
        assertEquals(500L, Wav.read(speech(8_000)).durationMs)
    }

    @Test
    fun skipsOtherChunksIncludingOddSizedOnesBeforeTheData() {
        val bytes = wav(format(1, 16_000, 1, 16), chunk("LIST", ByteArray(5) { 7 }), chunk("data", ByteArray(64) { 2 }))
        val audio = Wav.read(bytes)
        assertEquals(64, audio.pcm.size)
        assertEquals(2, audio.pcm[0].toInt())
    }

    @Test
    fun aDataSizeLargerThanTheFileMeansTheRestOfTheFile() {
        val bytes = wav(format(1, 16_000, 1, 16), chunk("data", ByteArray(100) { 3 }, declared = 0xFFFFFFFFL))
        assertEquals(100, Wav.read(bytes).pcm.size)
        val zero = wav(format(1, 16_000, 1, 16), chunk("data", ByteArray(100) { 3 }, declared = 0))
        assertEquals(100, Wav.read(zero).pcm.size)
    }

    @Test
    fun aHalfSampleAtTheEndIsDropped() {
        val bytes = wav(format(1, 16_000, 1, 16), chunk("data", ByteArray(5) { 1 }))
        assertEquals(4, Wav.read(bytes).pcm.size)
    }

    @Test
    fun theExtensibleHeaderIsAcceptedForSixteenBitPcm() {
        val bytes = wav(format(0xFFFE, 16_000, 1, 16, extensible = true), chunk("data", ByteArray(64)))
        assertEquals(16, Wav.read(bytes).bitsPerSample)
    }

    @Test
    fun badFilesAreRejectedWithAClearReason() {
        assertContains(assertFailsWith<IllegalArgumentException> { Wav.read(ByteArray(10)) }.message.orEmpty(), "WAV")
        assertContains(assertFailsWith<IllegalArgumentException> { Wav.read("RIFX0000WAVEfmt ".toByteArray()) }.message.orEmpty(), "RIFF")
        assertContains(assertFailsWith<IllegalArgumentException> { Wav.read(wav(chunk("data", ByteArray(8)))) }.message.orEmpty(), "fmt")
        assertContains(assertFailsWith<IllegalArgumentException> { Wav.read(wav(format(1, 16_000, 1, 16))) }.message.orEmpty(), "data")
        val floatSamples = wav(format(3, 16_000, 1, 32), chunk("data", ByteArray(8)))
        assertContains(assertFailsWith<IllegalArgumentException> { Wav.read(floatSamples) }.message.orEmpty(), "PCM")
    }

    @Test
    fun sixteenBitSamplesBecomeFloatsBetweenMinusOneAndOne() {
        val samples = shortArrayOf(0, 16384, -16384, 32767, -32768)
        val pcm = le(10).apply { samples.forEach { putShort(it) } }.array()
        val audio = Wav.read(wav(format(1, 16_000, 1, 16), chunk("data", pcm)))
        assertContentEquals(floatArrayOf(0f, 0.5f, -0.5f, 32767f / 32768f, -1f), audio.toFloatSamples())
    }

    @Test
    fun floatSamplesNeedSpeechFormatAudio() {
        val stereo = Wav.read(wav(format(1, 44_100, 2, 16), chunk("data", ByteArray(16))))
        assertContains(assertFailsWith<IllegalArgumentException> { stereo.toFloatSamples() }.message.orEmpty(), "16 kHz mono")
    }

    @Test
    fun theSpeechFormatIsSixteenKilohertzMonoSixteenBit() {
        Wav.read(speech()).requireSpeechFormat()
        val stereo = Wav.read(wav(format(1, 44_100, 2, 16), chunk("data", ByteArray(16))))
        val message = assertFailsWith<IllegalArgumentException> { stereo.requireSpeechFormat() }.message.orEmpty()
        assertContains(message, "44100")
        assertContains(message, "2 channels")
        val eightBit = Wav.read(wav(format(1, 16_000, 1, 8), chunk("data", ByteArray(16))))
        assertContains(assertFailsWith<IllegalArgumentException> { eightBit.requireSpeechFormat() }.message.orEmpty(), "8-bit")
    }
}
