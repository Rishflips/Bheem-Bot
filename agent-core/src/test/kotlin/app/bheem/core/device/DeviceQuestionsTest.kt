package app.bheem.core.device

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceQuestionsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDateTime.of(2026, 11, 4, 19, 42)
    private val phone = DeviceSnapshot(
        now = now, zone = zone, batteryPercent = 63, charging = true, network = Network.WIFI, doNotDisturb = false,
        storageFreeGb = 41, storageTotalGb = 128, nextAlarm = LocalDateTime.of(2026, 11, 5, 6, 30),
    )

    private fun ask(text: String, snapshot: DeviceSnapshot = phone) = DeviceQuestions.answer(text, snapshot)

    private fun assertAnswer(text: String, vararg parts: String, snapshot: DeviceSnapshot = phone) {
        val answer = ask(text, snapshot)
        assertTrue(answer != null, "no answer for \"$text\"")
        for (p in parts) assertTrue(answer.contains(p), "\"$text\" gave \"$answer\", expected \"$p\"")
    }

    @Test
    fun theTimeComesFromTheClockOfTheSnapshot() {
        assertAnswer("what time is it", "7:42 PM")
        assertAnswer("What's the time?", "7:42 PM")
        assertAnswer("tell me the time", "7:42 PM")
    }

    @Test
    fun theDateAndTheDayComeFromTheSnapshot() {
        assertAnswer("what day is it today", "Wednesday", "4 November")
        assertAnswer("what is today's date", "4 November 2026")
        assertAnswer("what date is it", "4 November 2026")
        assertAnswer("what day is it tomorrow", "Thursday", "5 November")
        assertAnswer("what day was yesterday", "Tuesday", "3 November")
    }

    @Test
    fun theDateChangesWhenTheClockDoes() {
        val later = phone.copy(now = LocalDateTime.of(2026, 12, 31, 23, 59))
        assertAnswer("what day is it today", "Thursday", "31 December", snapshot = later)
        assertAnswer("what day is it tomorrow", "Friday", "1 January 2027", snapshot = later)
    }

    @Test
    fun daysUntilADateCountsToTheNextTimeItComesRound() {
        assertAnswer("how many days until 15 December", "41 days", "15 December")
        assertAnswer("days until 25 dec", "51 days")
        assertAnswer("how many days until 4 November", "today")
        assertAnswer("how many days until 3 November", "364 days", "2027")
        assertAnswer("how many days until december 15", "41 days")
    }

    @Test
    fun batteryAndChargingAreReadFromTheSnapshot() {
        assertAnswer("how much battery do I have", "63%")
        assertAnswer("is my phone charging", "charging", "63")
        assertAnswer("is my phone charging", "Yes")
        assertAnswer("is my phone charging", "No", snapshot = phone.copy(charging = false))
        assertAnswer("how much battery", "cannot read", snapshot = phone.copy(batteryPercent = null))
    }

    @Test
    fun networkStateIsSaidWithoutClaimingTheInternetWorks() {
        assertAnswer("am I online", "Wi-Fi")
        assertAnswer("do I have internet", "Wi-Fi")
        assertAnswer("am I online", "mobile data", snapshot = phone.copy(network = Network.MOBILE))
        assertAnswer("am I online", "offline", snapshot = phone.copy(network = Network.OFFLINE))
    }

    @Test
    fun doNotDisturbStorageAndTheNextAlarm() {
        assertAnswer("is do not disturb on", "off")
        assertAnswer("is do not disturb on", "on", snapshot = phone.copy(doNotDisturb = true))
        assertAnswer("how much space is left on this phone", "41 GB", "128 GB")
        assertAnswer("what is my next alarm", "6:30 AM", "Thursday")
        assertAnswer("what is my next alarm", "no alarm", snapshot = phone.copy(nextAlarm = null))
    }

    @Test
    fun arithmeticIsExactAndCountedByCode() {
        assertAnswer("what is 15 percent of 2400", "360")
        assertAnswer("15% of 2400", "360")
        assertAnswer("what is 12 times 7", "84")
        assertAnswer("what is 100 divided by 8", "12.5")
        assertAnswer("what is 45 plus 37", "82")
        assertAnswer("what is 100 minus 58.5", "41.5")
        assertAnswer("12 x 12", "144")
        assertAnswer("what is 10 divided by 0", "zero")
    }

    @Test
    fun anythingElseIsLeftToTheNormalPath() {
        assertNull(ask("tell me something about volcanoes"))
        assertNull(ask("remind me tomorrow at 9 to call Arun"))
        assertNull(ask("what is on tomorrow"))
        assertNull(ask("hi"))
        assertNull(ask("what is the time zone of Japan"))
    }

    @Test
    fun theNowLineCarriesTheDateTheClockAndThePhoneState() {
        val line = phone.nowLine()
        assertTrue(line.startsWith("Now: "), line)
        for (part in listOf("Wed 4 Nov 2026", "7:42 PM", "IST", "63%", "charging", "Wi-Fi")) assertTrue(line.contains(part), "$line lacks $part")
        assertEquals("Now: Wed 4 Nov 2026, 7:42 PM IST.", DeviceSnapshot(now, zone).nowLine())
    }
}
