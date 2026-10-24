package app.bheem.core.planner

import app.bheem.models.BackendKind
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelState
import app.bheem.models.host.ModelUnavailable
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class LlmPlannerTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)

    private class FakeEngine(private val answer: String = "NoAction", private val fail: Boolean = false) : LlmEngine {
        val requests = mutableListOf<GenerationRequest>()

        override suspend fun load(spec: ModelSpec): Long = 1

        override suspend fun complete(request: GenerationRequest): Completion {
            requests += request
            check(!fail) { "engine failed" }
            return Completion(answer, 10, 20, 1)
        }

        override suspend fun unload() = Unit
    }

    private class FakeHost(val engine: LlmEngine, val unavailable: String? = null) : ModelHost {
        var acquired = 0
        var released = 0
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Ready(BackendKind.GPU))

        override suspend fun acquire(): LlmEngine {
            unavailable?.let { throw ModelUnavailable(it) }
            acquired++
            return engine
        }

        override fun release() {
            released++
        }

        override fun trim() = Unit

        override suspend fun shutdown() = Unit

        override fun abortLoad(reason: String) = Unit
    }

    @Test
    fun theUserTextAndTheDatedPromptAreSentAndTheAnswerIsReturned() {
        val engine = FakeEngine(answer = "CreateTask|renew the passport")
        val host = FakeHost(engine)
        val out = runBlocking { LlmPlanner(host).plan("add renew the passport to my tasks", now) }
        assertEquals("CreateTask|renew the passport", out)
        val request = engine.requests.single()
        assertEquals("add renew the passport to my tasks", request.user)
        assertTrue("Now: Friday 2026-10-23 08:00 (IST)." in request.system)
        assertEquals(0.0, request.temperature)
        assertEquals(1, host.acquired)
        assertEquals(1, host.released)
    }

    @Test
    fun theHostIsReleasedEvenWhenTheEngineFails() {
        val host = FakeHost(FakeEngine(fail = true))
        assertFailsWith<IllegalStateException> { runBlocking { LlmPlanner(host).plan("a", now) } }
        assertEquals(1, host.released)
    }

    @Test
    fun anUnavailableModelIsReportedAndNothingIsReleased() {
        val host = FakeHost(FakeEngine(), unavailable = "the model is paused to save battery or heat")
        val e = assertFailsWith<ModelUnavailable> { runBlocking { LlmPlanner(host).plan("a", now) } }
        assertTrue("battery or heat" in e.message.orEmpty())
        assertEquals(0, host.released)
    }

    @Test
    fun theBackendInUseComesFromTheHostState() {
        val host = FakeHost(FakeEngine())
        assertEquals("GPU", LlmPlanner(host).backendName())
    }
}
