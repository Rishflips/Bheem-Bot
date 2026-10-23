package app.bheem.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ContractsTest {
    @Test
    fun specDefaultsAreCpuAndThinkingOff() {
        val spec = ModelSpec(id = "m", path = "/tmp/m.litertlm")
        assertEquals(BackendKind.CPU, spec.backend)
        assertEquals(ThinkingMode.OFF, spec.thinking)
    }

    @Test
    fun requestDefaultsAreDeterministic() {
        val request = GenerationRequest(system = "s", user = "u")
        assertEquals(0.0, request.temperature)
        assertEquals(256, request.maxOutputTokens)
    }

    @Test
    fun aRequestHasNoAudioUnlessOneIsAttached() {
        assertNull(GenerationRequest(system = "s", user = "u").audio)
        val clip = AudioClip("/sdcard/a.wav")
        assertEquals(AudioForm.FILE, clip.form)
        assertTrue(clip.beforeText)
        assertEquals(clip, GenerationRequest("s", "u", audio = clip).audio)
    }
}
