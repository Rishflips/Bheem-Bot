package app.bheem.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class CapabilityTest {
    private class Engine(override val capabilities: Set<Capability>) : LlmEngine {
        var completes = 0
        var loads = 0

        override suspend fun load(spec: ModelSpec): Long {
            loads++
            return 1
        }

        override suspend fun complete(request: GenerationRequest): Completion {
            completes++
            return Completion("ok", 1, 1, 1)
        }

        override suspend fun unload() = Unit
    }

    @Test
    fun anEngineThatSaysNothingSupportsText() {
        val bare = object : LlmEngine {
            override suspend fun load(spec: ModelSpec) = 0L

            override suspend fun complete(request: GenerationRequest) = Completion("", 0, 0, 0)

            override suspend fun unload() = Unit
        }
        assertEquals(setOf(Capability.TEXT), bare.capabilities)
    }

    @Test
    fun aRequestNamesWhatItNeeds() {
        assertEquals(setOf(Capability.TEXT), GenerationRequest("s", "u").requires())
        val audio = AudioClip("", AudioForm.WAV_BYTES, bytes = byteArrayOf(1))
        assertEquals(setOf(Capability.TEXT, Capability.AUDIO_IN), GenerationRequest("s", "u", audio = audio).requires())
    }

    @Test
    fun theGuardRefusesARequestTheEngineCannotServeBeforeAnyNativeCall() = runBlocking<Unit> {
        val inner = Engine(setOf(Capability.TEXT))
        val guarded = GuardedEngine(inner)
        val audio = AudioClip("", AudioForm.WAV_BYTES, bytes = byteArrayOf(1))
        val refused = assertFailsWith<UnsupportedCapability> { guarded.complete(GenerationRequest("s", "u", audio = audio)) }
        assertEquals(Capability.AUDIO_IN, refused.missing)
        assertTrue(refused.message.orEmpty().contains("audio"), refused.message)
        assertEquals(0, inner.completes)
    }

    @Test
    fun theGuardPassesEverythingElseThrough() = runBlocking<Unit> {
        val inner = Engine(setOf(Capability.TEXT, Capability.AUDIO_IN))
        val guarded = GuardedEngine(inner)
        assertEquals(setOf(Capability.TEXT, Capability.AUDIO_IN), guarded.capabilities)
        assertEquals(1, guarded.load(ModelSpec("m", "/p")).toInt())
        val audio = AudioClip("", AudioForm.WAV_BYTES, bytes = byteArrayOf(1))
        assertEquals("ok", guarded.complete(GenerationRequest("s", "u", audio = audio)).text)
        assertEquals("ok", guarded.complete(GenerationRequest("s", "u")).text)
        assertEquals(2, inner.completes)
    }

    @Test
    fun guardingAGuardedEngineDoesNotStackWrappers() {
        val inner = Engine(setOf(Capability.TEXT))
        val once = GuardedEngine(inner)
        assertTrue(GuardedEngine.of(once) === once)
    }
}
