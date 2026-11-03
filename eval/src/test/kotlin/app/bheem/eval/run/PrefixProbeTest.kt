package app.bheem.eval.run

import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PrefixProbeTest {
    /** Models an engine that reuses the prefill of the longest shared prefix with the previous request, 1 ms per fresh character. */
    private class PrefixCachingEngine : LlmEngine {
        var previous = ""
        val systems = mutableListOf<String>()

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            systems += request.system
            val full = request.system + request.user
            val shared = full.commonPrefixWith(previous).length
            previous = full
            val first = 100L + (full.length - shared)
            return Completion("ok", firstChunkMs = first, totalMs = first + 50, chunkCount = 1)
        }

        override suspend fun unload() = Unit
    }

    private val system = "You are a planner. ".repeat(20)
    private val users = listOf("remind me to call Arun", "note: buy milk", "pay the electricity bill", "tell Meena I am late", "what did I say about rent")

    @Test
    fun aSharedPrefixIsFasterThanOneThatChangesAtTheStart() = runBlocking {
        val results = PrefixProbe.run(PrefixCachingEngine(), system, users).associateBy { it.variant }
        val same = checkNotNull(results["same-system"])
        val first = checkNotNull(results["nonce-first"])
        val last = checkNotNull(results["nonce-last"])
        assertTrue(first.firstChunkP50 > same.firstChunkP50 + 200, "a change at the start should cost the whole prompt: $first vs $same")
        assertTrue(last.firstChunkP50 <= first.firstChunkP50 - 200, "a change at the end should keep most of the cache: $last vs $first")
    }

    @Test
    fun theFirstRequestOfEachVariantIsLeftOutOfTheMedian() = runBlocking {
        val results = PrefixProbe.run(PrefixCachingEngine(), system, users)
        assertTrue(results.all { it.runs == users.size - 1 })
    }

    @Test
    fun theNonceChangesEveryRequestAndSitsAtTheNamedEnd() = runBlocking {
        val engine = PrefixCachingEngine()
        PrefixProbe.run(engine, system, users)
        val nonceFirst = engine.systems.filter { it != system && it.endsWith(system) }
        val nonceLast = engine.systems.filter { it.startsWith(system) && it != system }
        assertEquals(users.size, nonceFirst.distinct().size)
        assertEquals(users.size, nonceLast.distinct().size)
    }

    @Test
    fun theMedianOfAnEvenCountIsTheLowerMiddleValue() {
        assertEquals(200L, PrefixProbe.median(listOf(100L, 400L, 200L, 300L)))
        assertEquals(300L, PrefixProbe.median(listOf(500L, 100L, 300L)))
    }
}
