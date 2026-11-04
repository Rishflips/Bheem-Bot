package app.bheem.core.brief

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BriefTimeTest {
    @Test
    fun beforeTheBriefTimeItIsToday() {
        assertEquals(LocalDateTime.of(2026, 10, 23, 7, 30), BriefTime.next(LocalDateTime.of(2026, 10, 23, 6, 0)))
    }

    @Test
    fun atOrAfterTheBriefTimeItIsTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 30), BriefTime.next(LocalDateTime.of(2026, 10, 23, 7, 30)))
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 30), BriefTime.next(LocalDateTime.of(2026, 10, 23, 21, 0)))
    }

    @Test
    fun theBriefIsDueOnceTheDaysTimeHasPassed() {
        assertEquals(false, BriefTime.isDue(LocalDateTime.of(2026, 10, 23, 7, 29)))
        assertEquals(true, BriefTime.isDue(LocalDateTime.of(2026, 10, 23, 7, 30)))
    }
}
