package app.bheem.core.planner

import app.bheem.core.pipeline.Planner
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class LlmPlanner(private val engine: LlmEngine, private val specs: List<ModelSpec>) : Planner {
    private val lock = Mutex()
    private var loaded = false

    @Volatile
    var loadedBackend: app.bheem.models.BackendKind? = null
        private set

    override suspend fun plan(userText: String, now: LocalDateTime): String {
        ensureLoaded()
        val system = PlannerPrompt.command(PlannerPrompt.nowText(now))
        return engine.complete(GenerationRequest(system, userText, maxOutputTokens = MAX_OUTPUT_TOKENS, temperature = 0.0)).text
    }

    private suspend fun ensureLoaded() = lock.withLock {
        if (loaded) return@withLock
        val failures = mutableListOf<String>()
        for (spec in specs) {
            try {
                engine.load(spec)
                loaded = true
                loadedBackend = spec.backend
                return@withLock
            } catch (e: Exception) {
                failures += "${spec.backend}: ${e.message}"
            }
        }
        throw IllegalStateException("the model is not available (${failures.joinToString("; ")})")
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 128
    }
}
