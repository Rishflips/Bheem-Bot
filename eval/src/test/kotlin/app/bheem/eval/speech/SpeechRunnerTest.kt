package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.data.Jsonl
import app.bheem.eval.run.DeviceInfo
import app.bheem.eval.run.ResourceSample
import app.bheem.eval.run.ResourceSampler
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SpeechRunnerTest {
    private val device = DeviceInfo("test-phone", "Model X", "SoC", 6144, 35)
    private val sampler = ResourceSampler { ResourceSample(it, 100, 200, 0, 90, 30.0) }

    private class Lifecycle(private val failure: Throwable? = null) {
        var unloaded = false
        val value = ModelLifecycle(
            load = { failure?.let { throw it }; 1234L },
            unload = { unloaded = true },
        )
    }

    private class FakeTranscriber(
        override val id: String,
        private val failOn: Map<String, Throwable> = emptyMap(),
        private val reply: (String) -> String = { "pay ravi 840 rupees" },
    ) : Transcriber {
        val seen = mutableListOf<String>()

        override suspend fun transcribe(audioPath: String): Transcription {
            seen += audioPath
            failOn.entries.firstOrNull { it.key in audioPath }?.let { throw it.value }
            return Transcription(reply(audioPath), "raw:" + reply(audioPath), 300, 900)
        }
    }

    private fun take(index: Int, condition: RecordingCondition = RecordingCondition.QUIET) =
        Take("S01-cmd-%03d-${condition.code}".format(index), "S01", "cmd-%03d".format(index), "pay Ravi 840 rupees", condition, index)

    private fun audioDir(takes: List<Take>, seconds: Double = 1.0): Path {
        val dir = createTempDirectory("audio")
        for (t in takes) Files.write(dir.resolve("${t.id}.wav"), WavFixtures.wav(seconds = seconds))
        return dir
    }

    private val payRavi = ExpectedAction("OpenUpiPayment", mapOf("payee" to "Ravi", "amount" to "840"))

    @Test
    fun transcribesEveryTakeWithEveryTranscriber() = runBlocking {
        val takes = listOf(take(1), take(2, RecordingCondition.NOISY))
        val lifecycle = Lifecycle()
        val a = FakeTranscriber("a")
        val b = FakeTranscriber("b") { "pay robi 840" }
        val result = SpeechRunner(listOf(a, b), sampler, lifecycle.value).run(device, takes, audioDir(takes, seconds = 2.0))
        assertEquals(1234L, result.loadMs)
        assertEquals(listOf("a", "b"), result.transcribers)
        assertEquals(4, result.cases.size)
        val first = result.cases.first { it.takeId == takes[1].id && it.transcriber == "b" }
        assertEquals("S01", first.speaker)
        assertEquals("cmd-002", first.commandId)
        assertEquals("noisy", first.condition)
        assertEquals(2_000L, first.audioMs)
        assertEquals("pay robi 840", first.transcript)
        assertEquals("raw:pay robi 840", first.raw)
        assertEquals(300L, first.firstChunkMs)
        assertEquals(900L, first.totalMs)
        assertNull(first.error)
        assertTrue(result.complete)
        assertTrue(lifecycle.unloaded)
        assertEquals(listOf("start", "after_load", "end"), result.samples.map { it.label })
    }

    @Test
    fun aLimitPicksEvenlySpacedTakes() = runBlocking {
        val takes = (1..10).map { take(it) }
        val result = SpeechRunner(listOf(FakeTranscriber("a")), sampler, Lifecycle().value).run(device, takes, audioDir(takes), limit = 3)
        assertEquals(listOf("cmd-001", "cmd-004", "cmd-007"), result.cases.map { it.commandId })
    }

    @Test
    fun aMissingAudioFileIsAnErrorAndTheTranscriberIsNotCalled() = runBlocking {
        val takes = listOf(take(1))
        val fake = FakeTranscriber("a")
        val result = SpeechRunner(listOf(fake), sampler, Lifecycle().value).run(device, takes, createTempDirectory("empty"))
        assertContains(result.cases.single().error.orEmpty(), "audio file not found")
        assertNull(result.cases.single().audioMs)
        assertTrue(fake.seen.isEmpty())
    }

    @Test
    fun audioThatIsNotSixteenKilohertzMonoSixteenBitIsAnErrorAndTheTranscriberIsNotCalled() = runBlocking {
        val takes = listOf(take(1))
        val dir = createTempDirectory("audio")
        Files.write(dir.resolve("${takes[0].id}.wav"), WavFixtures.wav(sampleRate = 44_100, channels = 2))
        val fake = FakeTranscriber("a")
        val result = SpeechRunner(listOf(fake), sampler, Lifecycle().value).run(device, takes, dir)
        assertContains(result.cases.single().error.orEmpty(), "16 kHz mono")
        assertTrue(fake.seen.isEmpty())
    }

    @Test
    fun aFailingTranscriberIsRecordedAndTheRunContinues() = runBlocking {
        val takes = listOf(take(1), take(2))
        val fake = FakeTranscriber("a", failOn = mapOf("cmd-001" to IllegalStateException("decoder crashed")))
        val result = SpeechRunner(listOf(fake), sampler, Lifecycle().value).run(device, takes, audioDir(takes))
        assertContains(result.cases[0].error.orEmpty(), "decoder crashed")
        assertEquals("", result.cases[0].transcript)
        assertNull(result.cases[1].error)
        assertTrue(result.complete)
    }

    @Test
    fun cancellationIsNotSwallowedAndTheModelIsStillUnloaded() {
        val takes = listOf(take(1))
        val lifecycle = Lifecycle()
        val fake = FakeTranscriber("a", failOn = mapOf("cmd-001" to CancellationException("stop")))
        assertFailsWith<CancellationException> {
            runBlocking { SpeechRunner(listOf(fake), sampler, lifecycle.value).run(device, takes, audioDir(takes)) }
        }
        assertTrue(lifecycle.unloaded)
    }

    @Test
    fun aLoadFailureIsRecordedWithoutRunningAnything() = runBlocking {
        val takes = listOf(take(1))
        val lifecycle = Lifecycle(failure = IllegalStateException("no gpu"))
        val fake = FakeTranscriber("a")
        val result = SpeechRunner(listOf(fake), sampler, lifecycle.value).run(device, takes, audioDir(takes))
        assertContains(result.loadError.orEmpty(), "no gpu")
        assertNull(result.loadMs)
        assertTrue(result.cases.isEmpty())
        assertTrue(fake.seen.isEmpty())
    }

    @Test
    fun eachTranscriptIsPlannedAndJudgedAgainstTheExpectedAction() = runBlocking {
        val takes = listOf(take(1), take(2), take(3))
        val commands = takes.associate { it.commandId to CommandCase(it.commandId, "en", it.text, payRavi) }
        val transcripts = mapOf("cmd-001" to "pay ravi 840", "cmd-002" to "pay ravi 849", "cmd-003" to "")
        val planned = mutableListOf<String>()
        val planner: suspend (String) -> String = { transcript ->
            planned += transcript
            if ("849" in transcript) "OpenUpiPayment|Ravi|849" else "OpenUpiPayment|Ravi|840"
        }
        val fake = FakeTranscriber("a") { path -> transcripts.entries.first { it.key in path }.value }
        val result = SpeechRunner(listOf(fake), sampler, Lifecycle().value, planner).run(device, takes, audioDir(takes), commands)
        assertEquals(listOf("pay ravi 840", "pay ravi 849"), planned)
        val byCommand = result.cases.associateBy { it.commandId }
        assertEquals(true, byCommand.getValue("cmd-001").actionCorrect)
        assertEquals(true, byCommand.getValue("cmd-001").argsCorrect)
        assertEquals(true, byCommand.getValue("cmd-002").actionCorrect)
        assertEquals(false, byCommand.getValue("cmd-002").argsCorrect)
        assertEquals("OpenUpiPayment|Ravi|849", byCommand.getValue("cmd-002").plannerRaw)
        assertEquals(false, byCommand.getValue("cmd-003").actionCorrect)
        assertNull(byCommand.getValue("cmd-003").plannerRaw)
    }

    private class FakeActionTranscriber(override val id: String, private val line: String) : Transcriber {
        override val producesAction = true

        override suspend fun transcribe(audioPath: String) = Transcription(line, line, 700, 2_400)
    }

    private fun payRaviCommands() = mapOf("cmd-001" to CommandCase("cmd-001", "en", "pay Ravi 840 rupees", payRavi))

    @Test
    fun anActionTranscriberIsJudgedDirectlyAndThePlannerIsNotCalled() = runBlocking {
        val takes = listOf(take(1))
        val planner: suspend (String) -> String = { error("the planner must not be called") }
        val result = SpeechRunner(listOf(FakeActionTranscriber("g-action", "OpenUpiPayment|Ravi|840")), sampler, Lifecycle().value, planner)
            .run(device, takes, audioDir(takes), payRaviCommands())
        val case = result.cases.single()
        assertEquals("", case.transcript)
        assertEquals("OpenUpiPayment|Ravi|840", case.plannerRaw)
        assertEquals(true, case.actionCorrect)
        assertEquals(true, case.argsCorrect)
        assertEquals(2_400L, case.totalMs)
        assertNull(case.error)
    }

    @Test
    fun aWrongOrEmptyActionAnswerIsScoredWrong() = runBlocking {
        val takes = listOf(take(1))
        val wrong = SpeechRunner(listOf(FakeActionTranscriber("g-action", "SaveNote|Ravi payment")), sampler, Lifecycle().value)
            .run(device, takes, audioDir(takes), payRaviCommands()).cases.single()
        assertEquals(false, wrong.actionCorrect)
        assertEquals(false, wrong.argsCorrect)
        val empty = SpeechRunner(listOf(FakeActionTranscriber("g-action", "")), sampler, Lifecycle().value)
            .run(device, takes, audioDir(takes), payRaviCommands()).cases.single()
        assertEquals(false, empty.actionCorrect)
    }

    @Test
    fun anActionAnswerForAnUnknownCommandIsNotJudged() = runBlocking {
        val takes = listOf(take(1))
        val case = SpeechRunner(listOf(FakeActionTranscriber("g-action", "NoAction")), sampler, Lifecycle().value)
            .run(device, takes, audioDir(takes)).cases.single()
        assertNull(case.actionCorrect)
        assertEquals("NoAction", case.plannerRaw)
    }

    @Test
    fun withoutAPlannerNothingIsJudged() = runBlocking {
        val takes = listOf(take(1))
        val commands = mapOf("cmd-001" to CommandCase("cmd-001", "en", "pay Ravi 840 rupees", payRavi))
        val result = SpeechRunner(listOf(FakeTranscriber("a")), sampler, Lifecycle().value).run(device, takes, audioDir(takes), commands)
        assertNull(result.cases.single().actionCorrect)
        assertNull(result.cases.single().plannerRaw)
    }

    @Test
    fun aFailingPlannerIsRecordedAsAnError() = runBlocking {
        val takes = listOf(take(1))
        val commands = mapOf("cmd-001" to CommandCase("cmd-001", "en", "pay Ravi 840 rupees", payRavi))
        val planner: suspend (String) -> String = { throw IllegalStateException("engine busy") }
        val result = SpeechRunner(listOf(FakeTranscriber("a")), sampler, Lifecycle().value, planner).run(device, takes, audioDir(takes), commands)
        assertContains(result.cases.single().error.orEmpty(), "planner failed")
        assertEquals(false, result.cases.single().actionCorrect)
    }

    @Test
    fun progressIsPublishedEveryFewCasesAndAtTheEnd() = runBlocking {
        val takes = (1..5).map { take(it) }
        val seen = mutableListOf<Pair<Int, Boolean>>()
        SpeechRunner(listOf(FakeTranscriber("a")), sampler, Lifecycle().value) { seen += it.cases.size to it.complete }
            .run(device, takes, audioDir(takes), progressEvery = 2)
        assertEquals(listOf(2 to false, 4 to false, 5 to true), seen)
    }

    @Test
    fun theResultSurvivesAJsonRoundTrip() = runBlocking {
        val takes = listOf(take(1))
        val result = SpeechRunner(listOf(FakeTranscriber("a")), sampler, Lifecycle().value).run(device, takes, audioDir(takes))
        val text = Jsonl.json.encodeToString(SpeechRunResult.serializer(), result)
        assertEquals(result, Jsonl.json.decodeFromString(SpeechRunResult.serializer(), text))
        assertNotNull(result.cases.single().audioMs)
    }
}
