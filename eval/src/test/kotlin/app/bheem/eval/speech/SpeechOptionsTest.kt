package app.bheem.eval.speech

import app.bheem.models.AudioForm
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SpeechOptionsTest {
    @Test
    fun aNameListBecomesAudioForms() {
        assertEquals(listOf(AudioForm.FILE), SpeechOptions.forms("file"))
        assertEquals(listOf(AudioForm.FILE, AudioForm.WAV_BYTES, AudioForm.PCM_BYTES), SpeechOptions.forms("file, wav,pcm"))
    }

    @Test
    fun allMeansEveryForm() {
        assertEquals(AudioForm.entries, SpeechOptions.forms("all"))
    }

    @Test
    fun noValueMeansTheFileForm() {
        assertEquals(listOf(AudioForm.FILE), SpeechOptions.forms(null))
        assertEquals(listOf(AudioForm.FILE), SpeechOptions.forms(""))
    }

    @Test
    fun repeatedNamesAreKeptOnce() {
        assertEquals(listOf(AudioForm.WAV_BYTES), SpeechOptions.forms("wav,wav"))
    }

    @Test
    fun anUnknownNameIsRejectedWithTheValidOnes() {
        val message = assertFailsWith<IllegalArgumentException> { SpeechOptions.forms("mp3") }.message.orEmpty()
        assertContains(message, "mp3")
        assertContains(message, "file, wav, pcm, all")
    }

    @Test
    fun theEngineIsGemmaUnlessMoonshineIsNamed() {
        assertEquals(SpeechEngine.GEMMA, SpeechOptions.engine(null))
        assertEquals(SpeechEngine.GEMMA, SpeechOptions.engine("gemma"))
        assertEquals(SpeechEngine.MOONSHINE, SpeechOptions.engine("Moonshine"))
        val message = assertFailsWith<IllegalArgumentException> { SpeechOptions.engine("whisper") }.message.orEmpty()
        assertContains(message, "whisper")
        assertContains(message, "gemma, moonshine")
    }

    @Test
    fun onAndOffAreTheOnlySwitchValues() {
        assertEquals(true, SpeechOptions.switch("on", default = false))
        assertEquals(false, SpeechOptions.switch("off", default = true))
        assertEquals(true, SpeechOptions.switch(null, default = true))
        assertEquals(false, SpeechOptions.switch(null, default = false))
        assertFailsWith<IllegalArgumentException> { SpeechOptions.switch("yes", default = true) }
    }
}
