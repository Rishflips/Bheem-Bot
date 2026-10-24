package app.bheem.models

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class WavWriterTest {
    @Test
    fun theFileIsAHeaderOfFortyFourBytesAndTheSamplesAsLittleEndianPairs() {
        val bytes = WavWriter.speech(shortArrayOf(1, -2, 256))
        assertEquals(44 + 6, bytes.size)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertContentEquals(byteArrayOf(1, 0, -2, -1, 0, 1), bytes.copyOfRange(44, 50))
    }

    @Test
    fun theReaderGetsBackTheFormatAndTheSamples() {
        val samples = shortArrayOf(0, 16384, -16384, 32767, -32768)
        val audio = Wav.read(WavWriter.speech(samples)).requireSpeechFormat()
        assertEquals(16_000, audio.sampleRate)
        assertEquals(1, audio.channels)
        assertEquals(16, audio.bitsPerSample)
        assertContentEquals(floatArrayOf(0f, 0.5f, -0.5f, 32767f / 32768f, -1f), audio.toFloatSamples())
    }

    @Test
    fun oneSecondOfSamplesIsOneSecondOfAudio() {
        assertEquals(1_000L, Wav.read(WavWriter.speech(ShortArray(16_000))).durationMs)
    }

    @Test
    fun noSamplesStillMakeAValidFile() {
        val audio = Wav.read(WavWriter.speech(ShortArray(0)))
        assertEquals(0, audio.pcm.size)
        assertEquals(0L, audio.durationMs)
    }

    @Test
    fun anotherSampleRateIsWrittenInTheHeader() {
        assertEquals(44_100, Wav.read(WavWriter.encode(ShortArray(4), 44_100)).sampleRate)
    }
}
