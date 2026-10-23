package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.run.DeviceInfo
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpeechReportBuilderTest {
    private val commands = mapOf(
        "cmd-233" to CommandCase("cmd-233", "en", "pay Ravi 840 rupees", ExpectedAction("OpenUpiPayment", mapOf("payee" to "Ravi", "amount" to "840"))),
    )

    private fun case(take: String, speaker: String, condition: String, transcript: String, total: Long, action: Boolean?, args: Boolean?) =
        SpeechCaseResult(take, speaker, "cmd-233", condition, "gemma-audio-file", 2000, transcript, transcript, 400, total, null, null, action, args)

    private val result = SpeechRunResult(
        listOf("gemma-audio-file"), DeviceInfo("midrange-a", "Moto", "MT6878", 7393, 36), 5090, null, emptyList(),
        listOf(
            case("t1", "S01", "quiet", "pay Ravi 840 rupees", 1000, true, true),
            case("t2", "S02", "noisy", "pay Robi 849 rupees", 3000, true, false),
        ),
        true,
    )

    private val markdown = SpeechReportBuilder.markdown(result, commands)

    @Test
    fun theSummaryTableHasOneRowPerTranscriberWithTheKeyRates() {
        assertTrue("| gemma-audio-file | 2 | 0 | 33.3% | 50.0% | 50.0% | 50.0% | n/a | 100.0% | 50.0% |" in markdown, markdown)
    }

    @Test
    fun latencyIsShownAsMedianAndNinetyFifthPercentile() {
        assertTrue("400 / 400" in markdown)
        assertTrue("1000 / 3000" in markdown)
    }

    @Test
    fun thePerConditionAndPerSpeakerTablesAreListed() {
        assertTrue("| quiet | 0.0% |" in markdown, markdown)
        assertTrue("| noisy | 66.7% |" in markdown, markdown)
        assertTrue("| S01 | 0.0% |" in markdown)
        assertTrue("| S02 | 66.7% |" in markdown)
    }

    @Test
    fun theReportNamesTheDeviceAndSaysWhatIsNotScored() {
        assertTrue("midrange-a" in markdown)
        assertTrue("Takes with an error are counted but not scored" in markdown)
        assertTrue("synthetic" in markdown.lowercase())
    }

    @Test
    fun aFailedLoadIsReportedInsteadOfNumbers() {
        val failed = result.copy(loadMs = null, loadError = "no gpu", cases = emptyList(), complete = false)
        val text = SpeechReportBuilder.markdown(failed, commands)
        assertTrue("no gpu" in text)
        assertFalse("| gemma-audio-file |" in text)
    }

    @Test
    fun aLiveFeedTranscriberGetsAnExplanationAndNoRealTimeFactor() {
        val live = result.copy(
            transcribers = listOf("moonshine-small-streaming-en-live"),
            cases = result.cases.map { it.copy(transcriber = "moonshine-small-streaming-en-live") },
        )
        val text = SpeechReportBuilder.markdown(live, commands)
        assertTrue("live feed" in text.lowercase())
        assertTrue("from the end of the audio to the final text" in text)
        val row = text.lines().first { it.startsWith("| moonshine-small-streaming-en-live |") }
        assertTrue(row.endsWith("| n/a |"), row)
        assertFalse("from the end of the audio" in markdown)
    }

    @Test
    fun noEmDashesAppearInTheReport() {
        assertFalse('—' in markdown)
    }
}
