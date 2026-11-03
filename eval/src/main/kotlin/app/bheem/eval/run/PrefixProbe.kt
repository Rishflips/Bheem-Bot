package app.bheem.eval.run

import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import kotlinx.serialization.Serializable

@Serializable
data class PrefixProbeResult(val variant: String, val runs: Int, val firstChunkP50: Long, val totalP50: Long)

@Serializable
data class PrefixProbeRun(val modelId: String, val backend: String, val loadMs: Long, val systemChars: Int, val results: List<PrefixProbeResult>)

/**
 * Sends the same prompt three ways and times the first chunk of each reply: unchanged, with a changing line at the start of
 * the system prompt, and with a changing line at its end. A large gap between the first and the second shows the engine
 * reuses work on a shared prefix; no gap shows it does not. The first request of each variant is left out of the median.
 */
object PrefixProbe {
    suspend fun run(engine: LlmEngine, system: String, users: List<String>): List<PrefixProbeResult> = listOf(
        measure(engine, "same-system", users) { _ -> system },
        measure(engine, "nonce-first", users) { i -> "Request ${i + 1}.\n$system" },
        measure(engine, "nonce-last", users) { i -> "$system\nRequest ${i + 1}." },
    )

    fun median(values: List<Long>): Long = values.sorted()[(values.size - 1) / 2]

    private suspend fun measure(engine: LlmEngine, variant: String, users: List<String>, system: (Int) -> String): PrefixProbeResult {
        val replies = users.mapIndexed { i, user -> engine.complete(GenerationRequest(system(i), user, maxOutputTokens = MAX_OUTPUT_TOKENS)) }.drop(1)
        return PrefixProbeResult(variant, replies.size, median(replies.map { it.firstChunkMs }), median(replies.map { it.totalMs }))
    }

    private const val MAX_OUTPUT_TOKENS = 64
}
