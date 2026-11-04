package app.bheem.core.brief

import java.time.LocalDateTime
import java.time.LocalTime

object BriefTime {
    val AT: LocalTime = LocalTime.of(7, 30)

    /** The next moment the daily brief is due after [now]. */
    fun next(now: LocalDateTime): LocalDateTime {
        val today = now.toLocalDate().atTime(AT)
        return if (now.isBefore(today)) today else today.plusDays(1)
    }

    fun isDue(now: LocalDateTime): Boolean = !now.toLocalTime().isBefore(AT)
}
