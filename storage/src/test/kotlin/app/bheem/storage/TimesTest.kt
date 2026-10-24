package app.bheem.storage

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class TimesTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    private val london = ZoneId.of("Europe/London")

    @Test
    fun aLocalTimeRoundTripsThroughEpochAndTheStoredZone() {
        val at = LocalDateTime.of(2026, 10, 24, 9, 30)
        val epoch = Times.toEpochMillis(at, kolkata)
        assertEquals(at, Times.fromEpoch(epoch, kolkata.id))
    }

    @Test
    fun theStoredZoneIsUsedNotTheCurrentOne() {
        val at = LocalDateTime.of(2026, 10, 24, 9, 30)
        val epoch = Times.toEpochMillis(at, kolkata)
        assertEquals(LocalDateTime.of(2026, 10, 24, 5, 0), Times.fromEpoch(epoch, london.id))
        assertEquals(at, Times.fromEpoch(epoch, kolkata.id))
    }

    @Test
    fun theDaylightSavingOverlapRoundTrips() {
        val at = LocalDateTime.of(2026, 10, 25, 1, 30)
        assertEquals(at, Times.fromEpoch(Times.toEpochMillis(at, london), london.id))
    }

    @Test
    fun theEpochIsInMilliseconds() {
        assertEquals(0L, Times.toEpochMillis(LocalDateTime.of(1970, 1, 1, 0, 0), ZoneId.of("UTC")))
    }
}
