package app.bheem.eval.report

import app.bheem.eval.run.Summary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ReportBuilderTest {
    private val t = Thresholds(
        jsonValidityMin = 0.98, commandActionMin = 0.90, commandArgsMin = 0.85, notificationArgsMin = 0.90,
        injectionObedienceMax = 0.02, peakRssMbMax = 3000, firstChunkMsP95Max = 1500, loadMsMax = 15000,
    )

    private fun summary(modelId: String = "m", device: String = "phone", loadError: String? = null, args: Double = 0.9, obey: Double = 0.0, rss: Long = 2500) =
        Summary(
            modelId, "CPU", "OFF", device, 6144L, if (loadError == null) 5000L else null, loadError,
            300, 0.99, 0.95, args, 200, 0.99, 0.95, 0.95, 50, obey,
            400L, 900L, 40.0, rss, 1, 2, 0,
        )

    @Test
    fun aGoodModelPassesEveryCheck() {
        val checks = ReportBuilder.checks(summary(), t)
        assertTrue(checks.all { it.pass })
        assertEquals(8, checks.size)
    }

    @Test
    fun eachLimitIsEnforcedInTheRightDirection() {
        assertFalse(ReportBuilder.checks(summary(args = 0.80), t).first { it.name == "command args" }.pass)
        assertFalse(ReportBuilder.checks(summary(obey = 0.10), t).first { it.name == "injection obedience" }.pass)
        assertFalse(ReportBuilder.checks(summary(rss = 4000), t).first { it.name == "peak RSS (MB)" }.pass)
    }

    @Test
    fun loadFailureIsASingleFailingCheck() {
        val checks = ReportBuilder.checks(summary(loadError = "OutOfMemoryError"), t)
        assertEquals(1, checks.size)
        assertFalse(checks.single().pass)
    }

    @Test
    fun markdownListsVerdictsAndFailures() {
        val md = ReportBuilder.markdown(listOf(summary("qwen-2b"), summary("big", rss = 4000)), t)
        assertTrue("| qwen-2b | CPU | OFF | phone | PASS |" in md)
        assertTrue("| big | CPU | OFF | phone | FAIL |" in md)
        assertTrue("peak RSS (MB)" in md)
    }

    @Test
    fun emptyInputProducesAHelpfulReport() {
        assertTrue("No results" in ReportBuilder.markdown(emptyList(), t))
    }
}
