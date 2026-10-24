package app.bheem.core.planner

import app.bheem.models.BackendKind
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LlmPlannerTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val gpu = ModelSpec("m", "/models/m.litertlm", BackendKind.GPU)
    private val cpu = ModelSpec("m", "/models/m.litertlm", BackendKind.CPU)

    private class FakeEngine(private val failOn: Set<BackendKind> = emptySet(), private val answer: String = "NoAction") : LlmEngine {
        val loads = mutableListOf<BackendKind>()
        val requests = mutableListOf<GenerationRequest>()

        override suspend fun load(spec: ModelSpec): Long {
            loads += spec.backend
            if (spec.backend in failOn) error("cannot load on ${spec.backend}")
            return 5
        }

        override suspend fun complete(request: GenerationRequest): Completion {
            requests += request
            return Completion(answer, 10, 20, 1)
        }

        override suspend fun unload() = Unit
    }

    @Test
    fun theUserTextAndTheDatedPromptAreSentAndTheAnswerIsReturned() {
        val engine = FakeEngine(answer = "CreateTask|renew the passport")
        val out = runBlocking { LlmPlanner(engine, listOf(gpu, cpu)).plan("add renew the passport to my tasks", now) }
        assertEquals("CreateTask|renew the passport", out)
        val request = engine.requests.single()
        assertEquals("add renew the passport to my tasks", request.user)
        assertTrue("Now: Friday 2026-10-23 08:00 (IST)." in request.system)
        assertEquals(0.0, request.temperature)
    }

    @Test
    fun theModelLoadsOnceOnFirstUse() {
        val engine = FakeEngine()
        val planner = LlmPlanner(engine, listOf(gpu, cpu))
        assertEquals(emptyList(), engine.loads)
        runBlocking {
            planner.plan("a", now)
            planner.plan("b", now)
        }
        assertEquals(listOf(BackendKind.GPU), engine.loads)
    }

    @Test
    fun aBackendThatFailsToLoadFallsBackToTheNext() {
        val engine = FakeEngine(failOn = setOf(BackendKind.GPU))
        runBlocking { LlmPlanner(engine, listOf(gpu, cpu)).plan("a", now) }
        assertEquals(listOf(BackendKind.GPU, BackendKind.CPU), engine.loads)
    }

    @Test
    fun whenNoBackendLoadsThePlannerSaysSoAndTriesAgainNextTime() {
        val engine = FakeEngine(failOn = setOf(BackendKind.GPU, BackendKind.CPU))
        val planner = LlmPlanner(engine, listOf(gpu, cpu))
        val e = assertFailsWith<IllegalStateException> { runBlocking { planner.plan("a", now) } }
        assertTrue("model is not available" in e.message.orEmpty())
        assertFailsWith<IllegalStateException> { runBlocking { planner.plan("a", now) } }
        assertEquals(4, engine.loads.size)
    }
}
