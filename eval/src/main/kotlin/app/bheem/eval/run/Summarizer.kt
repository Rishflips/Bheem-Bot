package app.bheem.eval.run

import kotlin.math.ceil
import kotlinx.serialization.Serializable

@Serializable
data class Summary(
    val modelId: String,
    val backend: String,
    val thinking: String,
    val deviceLabel: String,
    val deviceRamMb: Long,
    val loadMs: Long?,
    val loadError: String?,
    val commandCount: Int,
    val commandJsonValid: Double,
    val commandAction: Double,
    val commandArgs: Double,
    val notificationCount: Int,
    val notificationJsonValid: Double,
    val notificationAction: Double,
    val notificationArgs: Double,
    val adversarialCount: Int,
    val injectionObedience: Double,
    val firstChunkMsP50: Long,
    val firstChunkMsP95: Long,
    val charsPerSecP50: Double,
    val peakRssMb: Long,
    val maxThermalStatus: Int,
    val batteryDropPct: Int,
    val errors: Int,
)

object Summarizer {
    fun summarize(run: RunResult): Summary {
        val commands = run.cases.filter { it.suite == "command" }
        val notifications = run.cases.filter { it.suite == "notification" }
        val adversarial = run.cases.filter { it.suite == "adversarial" }
        val timed = run.cases.filter { it.error == null }

        fun rate(list: List<CaseResult>, test: (CaseResult) -> Boolean) =
            if (list.isEmpty()) 0.0 else list.count(test).toDouble() / list.size

        fun <T : Comparable<T>> percentile(values: List<T>, p: Double): T? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            return sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
        }

        val speeds = timed.filter { it.totalMs > 0 }.map { it.outChars / (it.totalMs / 1000.0) }
        val batteryStart = run.samples.firstOrNull()?.batteryPct ?: -1
        val batteryEnd = run.samples.lastOrNull()?.batteryPct ?: -1

        return Summary(
            modelId = run.modelId, backend = run.backend, thinking = run.thinking,
            deviceLabel = run.device.label, deviceRamMb = run.device.totalRamMb,
            loadMs = run.loadMs, loadError = run.loadError,
            commandCount = commands.size,
            commandJsonValid = rate(commands) { it.jsonValid },
            commandAction = rate(commands) { it.actionCorrect == true },
            commandArgs = rate(commands) { it.argsCorrect == true },
            notificationCount = notifications.size,
            notificationJsonValid = rate(notifications) { it.jsonValid },
            notificationAction = rate(notifications) { it.actionCorrect == true },
            notificationArgs = rate(notifications) { it.argsCorrect == true },
            adversarialCount = adversarial.size,
            injectionObedience = rate(adversarial) { it.obeyed == true },
            firstChunkMsP50 = percentile(timed.map { it.firstChunkMs }, 0.50) ?: 0L,
            firstChunkMsP95 = percentile(timed.map { it.firstChunkMs }, 0.95) ?: 0L,
            charsPerSecP50 = percentile(speeds, 0.50) ?: 0.0,
            peakRssMb = run.samples.maxOfOrNull { it.peakRssMb } ?: 0,
            maxThermalStatus = run.samples.maxOfOrNull { it.thermalStatus } ?: 0,
            batteryDropPct = if (batteryStart >= 0 && batteryEnd >= 0) batteryStart - batteryEnd else 0,
            errors = run.cases.count { it.error != null },
        )
    }
}
