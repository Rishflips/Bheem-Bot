package app.bheem.core.time

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimeResolverTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)

    private fun at(text: String): LocalDateTime? = TimeResolver.resolve(text, now)?.toDateTime(default = null)

    private fun date(text: String): LocalDate? = TimeResolver.resolve(text, now)?.date

    @Test
    fun todayTomorrowAndTheDayAfterGiveTheDate() {
        assertEquals(LocalDate.of(2026, 10, 23), date("remind me today to call Anita"))
        assertEquals(LocalDate.of(2026, 10, 24), date("remind me tomorrow to call Anita"))
        assertEquals(LocalDate.of(2026, 10, 25), date("remind me the day after tomorrow to call Anita"))
    }

    @Test
    fun aClockTimeWithAmOrPmIsRead() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 19, 0), at("remind me tomorrow at 7 PM about the recital"))
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), at("tomorrow at 9am call Ravi"))
        assertEquals(LocalDateTime.of(2026, 10, 24, 21, 30), at("tomorrow at 9:30 pm call Ravi"))
        assertEquals(LocalDateTime.of(2026, 10, 24, 6, 0), at("tomorrow at 6 AM wake Vivek"))
    }

    @Test
    fun twelveAmIsMidnightAndTwelvePmIsNoon() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 0, 0), at("tomorrow at 12 AM"))
        assertEquals(LocalDateTime.of(2026, 10, 24, 12, 0), at("tomorrow at 12 PM"))
    }

    @Test
    fun aTwentyFourHourTimeNeedsNoMarker() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 21, 30), at("tomorrow at 21:30"))
        assertEquals(LocalDateTime.of(2026, 10, 24, 8, 5), at("tomorrow at 08:05"))
    }

    @Test
    fun tonightMeansEveningAndAHourWithTonightIsPm() {
        assertEquals(LocalDateTime.of(2026, 10, 23, 21, 0), at("can you remind me to take my tablets at 9 tonight"))
        assertEquals(LocalDateTime.of(2026, 10, 23, 21, 0), at("tonight at 9 remind me to lock up"))
    }

    @Test
    fun anHourWithoutMarkerOrDayPartHasNoTimeOfDay() {
        val resolved = assertNotNull(TimeResolver.resolve("tomorrow at 9 call Ravi", now))
        assertEquals(LocalDate.of(2026, 10, 24), resolved.date)
        assertNull(resolved.time)
    }

    @Test
    fun nextWeekdayIsTheComingOneAndNeverToday() {
        assertEquals(LocalDate.of(2026, 10, 26), date("set a reminder for next Monday at 8 AM"))
        assertEquals(LocalDate.of(2026, 10, 30), date("next Friday call Arun"))
        assertEquals(LocalDateTime.of(2026, 10, 26, 11, 0), at("book a call with Suresh next Monday at 11 AM"))
    }

    @Test
    fun aBareOrOnWeekdayIsTheNextOneAndThisWeekdayMayBeToday() {
        assertEquals(LocalDate.of(2026, 10, 26), date("on Monday remind me to send the report"))
        assertEquals(LocalDate.of(2026, 10, 25), date("on Sunday call Meena"))
        assertEquals(LocalDate.of(2026, 10, 23), date("this Friday call Meena"))
        assertEquals(LocalDate.of(2026, 10, 24), date("this Saturday call Meena"))
    }

    @Test
    fun inSomeMinutesHoursOrDaysCountsFromNow() {
        assertEquals(LocalDateTime.of(2026, 10, 23, 10, 0), at("ping me in two hours to check the oven"))
        assertEquals(LocalDateTime.of(2026, 10, 23, 8, 30), at("remind me in 30 minutes to call"))
        assertEquals(LocalDateTime.of(2026, 10, 23, 9, 0), at("remind me in an hour to call"))
        assertEquals(LocalDateTime.of(2026, 10, 23, 8, 30), at("remind me in half an hour to call"))
        assertEquals(LocalDateTime.of(2026, 10, 23, 8, 25), at("remind me in twenty five minutes to call"))
        assertEquals(LocalDate.of(2026, 10, 26), date("remind me in 3 days to pay rent"))
    }

    @Test
    fun anOffsetInDaysCanCarryAClockTime() {
        assertEquals(LocalDateTime.of(2026, 10, 26, 17, 0), at("in 3 days at 5 PM submit the form"))
    }

    @Test
    fun textWithoutADateOrOffsetResolvesToNothing() {
        assertNull(TimeResolver.resolve("add buy printer paper to my tasks", now))
        assertNull(TimeResolver.resolve("what did I spend at Swiggy last month", now))
        assertNull(TimeResolver.resolve("", now))
    }

    @Test
    fun anImpossibleClockTimeIsIgnored() {
        assertNull(at("tomorrow at 25 PM call"))
        assertNull(at("tomorrow at 13 PM call"))
        assertNull(at("tomorrow at 99:99"))
    }

    @Test
    fun caseAndPunctuationDoNotMatter() {
        assertEquals(LocalDateTime.of(2026, 10, 24, 19, 0), at("REMIND ME, TOMORROW, AT 7 P.M."))
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 0), at("Tomorrow at 7 a.m. please"))
    }

    @Test
    fun aMinuteNumberWithoutAnHourWordIsNotATime() {
        assertNull(TimeResolver.resolve("pay Ravi 840 rupees", now))
        assertNull(TimeResolver.resolve("call me 5 times", now))
    }

    @Test
    fun theDefaultTimeFillsAMissingClockTime() {
        val resolved = assertNotNull(TimeResolver.resolve("remind me tomorrow to call Ravi", now))
        assertEquals(LocalDateTime.of(2026, 10, 24, 9, 0), resolved.toDateTime(default = LocalTime.of(9, 0)))
        assertNull(resolved.toDateTime(default = null))
    }

    @Test
    fun aClockTimeAloneMeansTodayIfItIsStillAheadElseTomorrow() {
        assertEquals(LocalDateTime.of(2026, 10, 23, 17, 0), at("hey could you remind me at 5 pm to charge the laptop"))
        assertEquals(LocalDateTime.of(2026, 10, 24, 7, 0), at("remind me at 7 am to take the tablet"))
        assertEquals(LocalDateTime.of(2026, 10, 24, 8, 0), at("remind me at 8:00 to take the tablet"))
    }

    @Test
    fun anOffsetInDaysOrWeeksWithoutAClockKeepsTheTimeOfDayItIsNow() {
        assertEquals(LocalDateTime.of(2026, 10, 26, 8, 0), at("remind me in 3 days to pay rent"))
        assertEquals(LocalDateTime.of(2026, 11, 6, 8, 0), at("remind me in 2 weeks to get a haircut"))
    }

    @Test
    fun mentionsDateIsTrueOnlyForARealDateWordOrOffset() {
        assertEquals(true, TimeResolver.mentionsDate("remind me tomorrow at 9"))
        assertEquals(true, TimeResolver.mentionsDate("in 3 days"))
        assertEquals(true, TimeResolver.mentionsDate("next Monday"))
        assertEquals(false, TimeResolver.mentionsDate("remind me at 9 am"))
        assertEquals(false, TimeResolver.mentionsDate("remind me next month at 9am"))
    }
}
