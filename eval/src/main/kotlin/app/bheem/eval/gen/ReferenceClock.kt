package app.bheem.eval.gen

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object ReferenceClock {
    val TODAY: LocalDate = LocalDate.of(2026, 10, 23)
    val NOW: LocalDateTime = TODAY.atTime(8, 0)
    val isoMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
}
