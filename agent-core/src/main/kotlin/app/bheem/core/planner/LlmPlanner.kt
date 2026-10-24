package app.bheem.core.planner

import app.bheem.core.pipeline.Planner
import app.bheem.models.GenerationRequest
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelState
import java.time.LocalDateTime

class LlmPlanner(private val host: ModelHost) : Planner {
    override suspend fun plan(userText: String, now: LocalDateTime): String {
        val engine = host.acquire()
        try {
            val system = PlannerPrompt.command(PlannerPrompt.nowText(now))
            return engine.complete(GenerationRequest(system, userText, maxOutputTokens = MAX_OUTPUT_TOKENS, temperature = 0.0)).text
        } finally {
            host.release()
        }
    }

    fun backendName(): String = (host.state.value as? ModelState.Ready)?.backend?.name ?: "unknown"

    private companion object {
        const val MAX_OUTPUT_TOKENS = 128
    }
}
