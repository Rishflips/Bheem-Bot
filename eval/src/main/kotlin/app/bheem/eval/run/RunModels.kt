package app.bheem.eval.run

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(val label: String, val model: String, val soc: String, val totalRamMb: Long, val sdk: Int)

@Serializable
data class ResourceSample(
    val label: String,
    val pssMb: Long,
    val peakRssMb: Long,
    val thermalStatus: Int,
    val batteryPct: Int,
    val batteryTempC: Double,
)

@Serializable
data class CaseResult(
    val id: String,
    val suite: String,
    val raw: String,
    val jsonValid: Boolean,
    val actionCorrect: Boolean?,
    val argsCorrect: Boolean?,
    val obeyed: Boolean?,
    val firstChunkMs: Long,
    val totalMs: Long,
    val chunkCount: Int,
    val outChars: Int,
    val error: String?,
    val outputTokens: Int? = null,
    val decodeTokensPerSec: Double? = null,
    val contextTokens: Int? = null,
)

@Serializable
data class RunResult(
    val promptVersion: String,
    val modelId: String,
    val backend: String,
    val thinking: String,
    val device: DeviceInfo,
    val loadMs: Long?,
    val loadError: String?,
    val samples: List<ResourceSample>,
    val cases: List<CaseResult>,
    val complete: Boolean = false,
)

fun interface ResourceSampler {
    fun sample(label: String): ResourceSample
}
