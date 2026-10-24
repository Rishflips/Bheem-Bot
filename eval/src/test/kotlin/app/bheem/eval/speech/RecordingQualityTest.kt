package app.bheem.eval.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingQualityTest {
    private fun level(ms: Int, amplitude: Int): ShortArray = ShortArray(16 * ms) { if (it % 2 == 0) amplitude.toShort() else (-amplitude).toShort() }

    /** A pause at the room's noise level, a stretch of "voice", and another pause. */
    private fun speech(ms: Int, voice: Int = 4_000, floor: Int = 20): ShortArray {
        val pause = ms / 4
        return level(pause, floor) + level(ms - 2 * pause, voice) + level(pause, floor)
    }

    @Test
    fun aClearRecordingOfNormalLengthIsFine() {
        val result = RecordingQuality.check(speech(2_000))
        assertEquals(RecordingVerdict.OK, result.verdict)
        assertTrue(result.ok)
        assertEquals(2_000L, result.durationMs)
    }

    @Test
    fun aVeryShortRecordingIsRejected() {
        val result = RecordingQuality.check(speech(300))
        assertEquals(RecordingVerdict.TOO_SHORT, result.verdict)
        assertFalse(result.ok)
    }

    @Test
    fun silenceIsRejected() {
        assertEquals(RecordingVerdict.TOO_QUIET, RecordingQuality.check(ShortArray(32_000)).verdict)
    }

    @Test
    fun aTapOrAThumpInAQuietRoomIsNotSpeech() {
        val room = level(2_000, 17)
        for (start in listOf(1_600, 20_000)) for (i in 0 until 960) room[start + i] = if (i % 2 == 0) 2_600 else -2_600
        assertEquals(RecordingVerdict.TOO_QUIET, RecordingQuality.check(room).verdict)
    }

    @Test
    fun aSteadySoundWithNoSpeechIsNotSpeechHoweverLoud() {
        assertEquals(RecordingVerdict.TOO_QUIET, RecordingQuality.check(level(2_000, 300)).verdict)
        assertEquals(RecordingVerdict.TOO_QUIET, RecordingQuality.check(level(2_000, 2_500)).verdict)
    }

    @Test
    fun aQuietVoiceStillCounts() {
        assertEquals(RecordingVerdict.OK, RecordingQuality.check(speech(2_000, voice = 400)).verdict)
    }

    @Test
    fun speechOverSteadyBackgroundNoiseIsAccepted() {
        assertEquals(RecordingVerdict.OK, RecordingQuality.check(speech(2_500, voice = 3_500, floor = 300)).verdict)
    }

    @Test
    fun aRecordingThatClipsIsRejected() {
        val result = RecordingQuality.check(level(2_000, 32_767))
        assertEquals(RecordingVerdict.CLIPPED, result.verdict)
        assertTrue(result.clippedFraction > 0.5f)
    }

    @Test
    fun aFewClippedSamplesAreAcceptable() {
        val samples = speech(2_000)
        for (i in 0 until 20) samples[8_000 + i * 100] = 32_767
        assertEquals(RecordingVerdict.OK, RecordingQuality.check(samples).verdict)
    }

    @Test
    fun theTooShortCheckComesBeforeTheOthers() {
        assertEquals(RecordingVerdict.TOO_SHORT, RecordingQuality.check(ShortArray(1_000)).verdict)
    }

    @Test
    fun eachProblemHasAMessageThatTellsTheSpeakerWhatToDo() {
        for (verdict in RecordingVerdict.entries) {
            val message = RecordingQuality.message(verdict)
            assertTrue(message.isNotBlank(), verdict.name)
            assertFalse('—' in message, verdict.name)
        }
        assertTrue("hold" in RecordingQuality.message(RecordingVerdict.TOO_SHORT).lowercase())
        assertTrue("closer" in RecordingQuality.message(RecordingVerdict.TOO_QUIET).lowercase())
        assertTrue("no speech" in RecordingQuality.message(RecordingVerdict.TOO_QUIET).lowercase())
        assertTrue("further" in RecordingQuality.message(RecordingVerdict.CLIPPED).lowercase())
    }
}
