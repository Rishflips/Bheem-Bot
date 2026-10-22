package app.bheem.models

import kotlin.test.Test
import kotlin.test.assertEquals

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
}
