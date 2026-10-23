package app.bheem.models

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AudioPayloadsTest {
    private val pcm = ByteArray(32) { it.toByte() }

    private val wav: ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII))
        putInt(36 + pcm.size)
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII))
        putInt(16)
        putShort(1)
        putShort(1)
        putInt(16_000)
        putInt(32_000)
        putShort(2)
        putShort(16)
        put("data".toByteArray(Charsets.US_ASCII))
        putInt(pcm.size)
    }.array() + pcm

    private fun payload(form: AudioForm, bytes: ByteArray = wav) =
        AudioPayloads.of(AudioClip("/sdcard/a.wav", form)) { path ->
            assertEquals("/sdcard/a.wav", path)
            bytes
        }

    @Test
    fun aFileClipIsHandedOverByPathWithoutReadingIt() {
        val payload = AudioPayloads.of(AudioClip("/sdcard/a.wav", AudioForm.FILE)) { error("the file must not be read") }
        assertEquals(AudioPayload.File("/sdcard/a.wav"), payload)
    }

    @Test
    fun wavBytesAreTheWholeFileAsRead() {
        val payload = payload(AudioForm.WAV_BYTES) as AudioPayload.Bytes
        assertContentEquals(wav, payload.bytes)
    }

    @Test
    fun pcmBytesAreTheSamplesWithoutTheHeader() {
        val payload = payload(AudioForm.PCM_BYTES) as AudioPayload.Bytes
        assertContentEquals(pcm, payload.bytes)
    }

    @Test
    fun rawSamplesNeedSpeechFormatAudio() {
        val stereo = wav.copyOf().also { it[22] = 2 }
        val message = assertFailsWith<IllegalArgumentException> { payload(AudioForm.PCM_BYTES, stereo) }.message.orEmpty()
        assertContains(message, "16 kHz mono")
    }
}
