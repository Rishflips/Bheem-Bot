package app.bheem.models.speech

import app.bheem.models.AudioClip
import app.bheem.models.AudioForm
import app.bheem.models.AudioPayload
import app.bheem.models.AudioPayloads
import app.bheem.models.Wav
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RecordingTest {
    private fun tone(millis: Int, amplitude: Int): ShortArray =
        ShortArray(millis * 16) { (amplitude * sin(2 * PI * 440 * it / 16_000)).toInt().toShort() }

    @Test
    fun theBufferKeepsWhatWasRecordedUpToItsCapAndSaysWhenItIsFull() {
        val buffer = RecordingBuffer(maxMillis = 1_000)
        assertTrue(buffer.append(ShortArray(8_000) { 1 }, 8_000))
        assertEquals(500L, buffer.durationMillis)
        assertFalse(buffer.append(ShortArray(12_000) { 2 }, 12_000))
        assertTrue(buffer.isFull)
        assertEquals(16_000, buffer.samples().size)
        assertEquals(2.toShort(), buffer.samples().last())
    }

    @Test
    fun onlyTheFilledPartOfAChunkIsKept() {
        val buffer = RecordingBuffer(maxMillis = 1_000)
        buffer.append(ShortArray(4_000) { 5 }, 100)
        assertEquals(100, buffer.samples().size)
    }

    @Test
    fun theLevelIsZeroForSilenceOneForLoudAndGrowsWithVolume() {
        assertEquals(0f, Levels.level(ShortArray(1_600), 1_600))
        assertEquals(1f, Levels.level(ShortArray(1_600) { 30_000 }, 1_600))
        val quiet = Levels.level(tone(100, 500), 1_600)
        val loud = Levels.level(tone(100, 5_000), 1_600)
        assertTrue(quiet in 0f..1f && loud in 0f..1f && loud > quiet, "$quiet $loud")
        assertEquals(0f, Levels.level(ShortArray(0), 0))
    }

    @Test
    fun aClearUtteranceBecomesWavBytesInTheSpeechFormat() {
        val result = assertIs<Utterance.Captured>(Utterances.of(tone(1_500, 8_000)))
        val wav = Wav.read(result.wav).requireSpeechFormat()
        assertEquals(1_500L, wav.durationMs)
    }

    @Test
    fun aVeryShortPressIsTooShort() {
        assertEquals(Utterance.TooShort, Utterances.of(tone(200, 8_000)))
    }

    @Test
    fun aLongEnoughRecordingOfNothingIsSilent() {
        assertEquals(Utterance.Silent, Utterances.of(tone(1_500, 100)))
        assertEquals(Utterance.Silent, Utterances.of(ShortArray(24_000)))
    }

    @Test
    fun anAudioClipCanCarryItsBytesAndTheEngineNeverReadsAFile() {
        val bytes = byteArrayOf(1, 2, 3)
        val payload = AudioPayloads.of(AudioClip("", AudioForm.WAV_BYTES, bytes = bytes)) { error("a file must not be read") }
        assertContentEquals(bytes, assertIs<AudioPayload.Bytes>(payload).bytes)
    }
}
