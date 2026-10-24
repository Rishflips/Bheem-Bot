package app.bheem.core.time

import java.time.DateTimeException
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

enum class TimeSource { CODE, MODEL, NONE }

data class ReconciledTime(val value: LocalDateTime?, val source: TimeSource, val corrected: Boolean)

object TimeReconciler {
    fun reconcile(userText: String, modelTime: String?, now: LocalDateTime): ReconciledTime {
        val model = parse(modelTime)
        val resolved = TimeResolver.resolve(userText, now)
            ?: return if (model != null) ReconciledTime(model, TimeSource.MODEL, false) else ReconciledTime(null, TimeSource.NONE, false)
        val value = resolved.toDateTime(default = model?.toLocalTime()) ?: return ReconciledTime(null, TimeSource.NONE, false)
        return ReconciledTime(value, TimeSource.CODE, model != null && model != value)
    }

    private fun parse(s: String?): LocalDateTime? =
        try {
            s?.trim()?.takeIf { it.isNotEmpty() }?.let { LocalDateTime.parse(it).truncatedTo(ChronoUnit.MINUTES) }
        } catch (_: DateTimeException) {
            null
        }
}
