package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.run.DeviceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpeechSummarizerTest {
    private val commands = mapOf(
        "cmd-233" to CommandCase("cmd-233", "en", "pay Ravi 840 rupees", ExpectedAction("OpenUpiPayment", mapOf("payee" to "Ravi", "amount" to "840"))),
    )

    private fun case(
        take: String, speaker: String, condition: String, transcript: String,
        first: Long = 400, total: Long = 1000, audio: Long? = 2000, error: String? = null, transcriber: String = "a",
        action: Boolean? = null, args: Boolean? = null,
    ) = SpeechCaseResult(take, speaker, "cmd-233", condition, transcriber, audio, transcript, transcript, first, total, error, null, action, args)

    private fun run(vararg cases: SpeechCaseResult) = SpeechRunResult(
        cases.map { it.transcriber }.distinct(), DeviceInfo("phone", "M", "S", 6144, 35), 1000, null, emptyList(), cases.toList(), true,
    )

    private val sample = run(
        case("t1", "S01", "quiet", "pay Ravi 840 rupees", first = 400, total = 1000, audio = 2000, action = true, args = true),
        case("t2", "S02", "noisy", "pay Robi 849 rupees", first = 800, total = 3000, audio = 3000, action = true, args = false),
        case("t3", "S01", "far", "", first = 0, total = 0, audio = 2000, error = "boom", action = false, args = false),
    )

    private val summary = SpeechSummarizer.summarize(sample, commands).single()

    @Test
    fun errorsAreCountedAndLeftOutOfTheWerAndEntityScores() {
        assertEquals("a", summary.transcriber)
        assertEquals(3, summary.takes)
        assertEquals(1, summary.errors)
        assertEquals(WerScore(2, 0, 0, 6), summary.wer)
        assertEquals(4, summary.entities.totalCount)
        assertEquals(2, summary.entities.correctCount)
        assertEquals(0.5, summary.entities.errorRate(EntityKind.AMOUNT))
        assertEquals(0.5, summary.entities.errorRate(EntityKind.NAME))
    }

    @Test
    fun scoresAreSplitByConditionAndBySpeaker() {
        assertEquals(WerScore(0, 0, 0, 3), summary.werByCondition.getValue("quiet"))
        assertEquals(WerScore(2, 0, 0, 3), summary.werByCondition.getValue("noisy"))
        assertEquals(null, summary.werByCondition["far"])
        assertEquals(WerScore(0, 0, 0, 3), summary.werBySpeaker.getValue("S01"))
        assertEquals(WerScore(2, 0, 0, 3), summary.werBySpeaker.getValue("S02"))
    }

    @Test
    fun latencyUsesNearestRankAndTheRealTimeFactorIsTotalOverAudio() {
        assertEquals(400L, summary.firstChunkMsP50)
        assertEquals(800L, summary.firstChunkMsP95)
        assertEquals(1000L, summary.totalMsP50)
        assertEquals(3000L, summary.totalMsP95)
        assertEquals(0.5, summary.realTimeFactorP50)
    }

    @Test
    fun endToEndRatesCountEveryJudgedTakeIncludingFailedOnes() {
        assertEquals(2.0 / 3.0, summary.commandAction)
        assertEquals(1.0 / 3.0, summary.commandArgs)
    }

    @Test
    fun withoutAPlannerThereAreNoEndToEndRates() {
        val none = SpeechSummarizer.summarize(run(case("t1", "S01", "quiet", "pay Ravi 840 rupees")), commands).single()
        assertNull(none.commandAction)
        assertNull(none.commandArgs)
    }

    @Test
    fun eachTranscriberGetsItsOwnSummary() {
        val both = run(
            case("t1", "S01", "quiet", "pay Ravi 840 rupees", transcriber = "a"),
            case("t1", "S01", "quiet", "pay Robi 840 rupees", transcriber = "b"),
        )
        val summaries = SpeechSummarizer.summarize(both, commands)
        assertEquals(listOf("a", "b"), summaries.map { it.transcriber })
        assertEquals(0, summaries[0].wer.errors)
        assertEquals(1, summaries[1].wer.errors)
    }

    @Test
    fun anActionTranscriberHasNoWordOrEntityScoresOnlyTheEndToEndRates() {
        val run = run(
            case("t1", "S01", "quiet", "", transcriber = "g-action", action = true, args = true),
            case("t2", "S02", "noisy", "", transcriber = "g-action", action = true, args = false),
        )
        val summary = SpeechSummarizer.summarize(run, commands).single()
        assertEquals(WerScore.ZERO, summary.wer)
        assertEquals(EntityScore.EMPTY, summary.entities)
        assertEquals(1.0, summary.commandAction)
        assertEquals(0.5, summary.commandArgs)
    }

    @Test
    fun takesWhoseCommandIsUnknownAreNotScored() {
        val unknown = SpeechSummarizer.summarize(run(case("t1", "S01", "quiet", "anything").copy(commandId = "cmd-999")), commands).single()
        assertEquals(WerScore.ZERO, unknown.wer)
        assertEquals(1, unknown.takes)
    }

    @Test
    fun nearestRankPercentilesPickAnActualValue() {
        assertEquals(190, (1..20).map { it * 10 }.nearestRank(0.95))
        assertEquals(100, (1..20).map { it * 10 }.nearestRank(0.5))
        assertNull(emptyList<Int>().nearestRank(0.5))
        assertEquals(7, listOf(7).nearestRank(0.95))
    }
}
