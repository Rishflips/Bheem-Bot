package app.bheem.eval.run

import kotlin.test.Test
import kotlin.test.assertEquals

class SummarizerTest {
    private val device = DeviceInfo("phone", "M", "S", 6144, 35)

    private fun case(id: String, suite: String, valid: Boolean = true, action: Boolean? = true, args: Boolean? = true, obeyed: Boolean? = null,
                     first: Long = 100, total: Long = 1000, chars: Int = 50, error: String? = null) =
        CaseResult(id, suite, "", valid, action, args, obeyed, first, total, 10, chars, error)

    private fun run(cases: List<CaseResult>, samples: List<ResourceSample> = listOf(
        ResourceSample("start", 10, 10, 0, 90, 30.0), ResourceSample("end", 20, 2048, 2, 87, 38.0))) =
        RunResult("proposer-v1", "m", "CPU", "OFF", device, 5000, null, samples, cases)

    @Test
    fun ratesPerSuiteAndErrorsCountAsFailures() {
        val s = Summarizer.summarize(run(listOf(
            case("c1", "command"),
            case("c2", "command", valid = false, action = false, args = false),
            case("c3", "command", valid = false, action = false, args = false, error = "boom"),
            case("c4", "command", args = false),
            case("n1", "notification"),
            case("a1", "adversarial", action = null, args = null, obeyed = true),
            case("a2", "adversarial", action = null, args = null, obeyed = false),
        )))
        assertEquals(4, s.commandCount)
        assertEquals(0.5, s.commandJsonValid)
        assertEquals(0.5, s.commandAction)
        assertEquals(0.25, s.commandArgs)
        assertEquals(1.0, s.notificationArgs)
        assertEquals(0.5, s.injectionObedience)
        assertEquals(1, s.errors)
    }

    @Test
    fun emptySuitesYieldZeroNotNaN() {
        val s = Summarizer.summarize(run(emptyList()))
        assertEquals(0.0, s.commandArgs)
        assertEquals(0.0, s.injectionObedience)
        assertEquals(0L, s.firstChunkMsP95)
    }

    @Test
    fun percentilesUseNearestRankAndSpeedIsCharsPerSecond() {
        val cases = (1..20).map { case("c$it", "command", first = it * 10L, total = 2000, chars = 100) }
        val s = Summarizer.summarize(run(cases))
        assertEquals(100L, s.firstChunkMsP50)
        assertEquals(190L, s.firstChunkMsP95)
        assertEquals(50.0, s.charsPerSecP50)
    }

    @Test
    fun decodeTokensPerSecondIsTheMedianOverCasesThatReportIt() {
        val withTokens = listOf(10.0, 20.0, 30.0).mapIndexed { i, v -> case("t$i", "command").copy(outputTokens = 40, decodeTokensPerSec = v) }
        val without = case("none", "command")
        assertEquals(20.0, Summarizer.summarize(run(withTokens + without)).decodeTokensPerSecP50)
        assertEquals(0.0, Summarizer.summarize(run(listOf(without))).decodeTokensPerSecP50)
    }

    @Test
    fun completeFlagComesFromTheRun() {
        assertEquals(false, Summarizer.summarize(run(listOf(case("c1", "command")))).complete)
        assertEquals(true, Summarizer.summarize(run(listOf(case("c1", "command"))).copy(complete = true)).complete)
    }

    @Test
    fun resourceFieldsComeFromSamples() {
        val s = Summarizer.summarize(run(listOf(case("c1", "command"))))
        assertEquals(2048L, s.peakRssMb)
        assertEquals(2, s.maxThermalStatus)
        assertEquals(3, s.batteryDropPct)
        assertEquals("phone", s.deviceLabel)
        assertEquals(5000L, s.loadMs)
    }
}
