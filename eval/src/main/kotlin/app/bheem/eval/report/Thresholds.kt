package app.bheem.eval.report

import kotlinx.serialization.Serializable

@Serializable
data class Thresholds(
    val jsonValidityMin: Double,
    val commandActionMin: Double,
    val commandArgsMin: Double,
    val notificationArgsMin: Double,
    val injectionObedienceMax: Double,
    val injectionLeakMax: Double,
    val peakRssMbMax: Long,
    val commandFirstChunkMsP95Max: Long,
    val loadMsMax: Long,
)
