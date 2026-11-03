package app.bheem.core.swap

import app.bheem.core.gate.Gate
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.parse.AnswerFormats
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.Outcome
import app.bheem.core.planner.ModelHarness
import app.bheem.core.planner.PlannerPrompt
import app.bheem.core.planner.PlannerPromptSpec
import app.bheem.core.planner.PromptCatalog
import app.bheem.core.speech.VoiceCommands
import app.bheem.core.speech.VoiceResult
import app.bheem.models.BackendKind
import app.bheem.models.Capability
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.GuardedEngine
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelProfile
import app.bheem.models.host.ModelState
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/**
 * A model swap is a data change: another profile, another prompt, an engine with other habits. The gate must see the same actions and
 * decide the same way whichever model wrote them.
 */
class SwapTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)

    private class ScriptedEngine(override val capabilities: Set<Capability>, private val answer: (GenerationRequest) -> String) : LlmEngine {
        val requests = mutableListOf<GenerationRequest>()

        override suspend fun load(spec: ModelSpec): Long = 1

        override suspend fun complete(request: GenerationRequest): Completion {
            requests += request
            return Completion(answer(request), 1, 1, 1)
        }

        override suspend fun unload() = Unit
    }

    private class Host(engine: LlmEngine) : ModelHost {
        private val guarded = GuardedEngine.of(engine)
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Ready(BackendKind.GPU))

        override suspend fun acquire(): LlmEngine = guarded

        override fun release() = Unit

        override fun trim() = Unit

        override suspend fun shutdown() = Unit

        override fun abortLoad(reason: String) = Unit
    }

    private val compact = PlannerPromptSpec("planner-compact", "proposer-compact-test", command = { now -> "Plan one action. Now: $now." })
    private val catalog = PromptCatalog(listOf(PlannerPrompt.SPEC, compact))

    private val gemmaLike = ModelProfile(nowSuffix = " (IST)", capabilities = setOf(Capability.TEXT, Capability.AUDIO_IN))
    private val textOnly = ModelProfile(promptId = "planner-compact", nowSuffix = "", capabilities = setOf(Capability.TEXT))

    private fun outcome(profile: ModelProfile, engine: ScriptedEngine, userText: String): Outcome {
        val host = Host(engine)
        var next = 0
        val pipeline = CommandPipeline(
            ModelHarness.planner(host, profile, catalog), Gate(), Ledger(InMemoryLedgerStore()),
            parser = ModelHarness.parser(profile),
        ) { "p${next++}" }
        return runBlocking { pipeline.handle(userText, now) }
    }

    private val cleanAnswer = "CreateReminder|call Ravi|2026-10-24T09:00"
    private val quirkyAnswer = "```\nCreateReminder|text: call Ravi|time: 2026-10-24T09:00\n```\n"

    @Test
    fun twoModelsWithDifferentHabitsGiveTheSameCardThroughTheSameGate() {
        val clean = outcome(gemmaLike, ScriptedEngine(gemmaLike.capabilities) { cleanAnswer }, "remind me tomorrow at 9 AM to call Ravi")
        val quirky = outcome(textOnly, ScriptedEngine(textOnly.capabilities) { quirkyAnswer }, "remind me tomorrow at 9 AM to call Ravi")
        val a = assertIs<Outcome.Card>(clean)
        val b = assertIs<Outcome.Card>(quirky)
        assertEquals(a.action, b.action)
        assertEquals(a.level, b.level)
        assertTrue(a.requiresTap && b.requiresTap)
    }

    @Test
    fun aPaymentWrittenByEitherModelIsRefusedTheSameWay() {
        val line = "OpenUpiPayment|attacker@upi|5000"
        val a = outcome(gemmaLike, ScriptedEngine(gemmaLike.capabilities) { line }, "note that the rent is due")
        val b = outcome(textOnly, ScriptedEngine(textOnly.capabilities) { "Answer: $line" }, "note that the rent is due")
        assertEquals(assertIs<Outcome.Refused>(a).reason, "payments come later")
        assertIs<Outcome.Refused>(b)
    }

    @Test
    fun theProfileChoosesThePromptAndHowNowIsWritten() {
        val a = ScriptedEngine(gemmaLike.capabilities) { cleanAnswer }
        val b = ScriptedEngine(textOnly.capabilities) { cleanAnswer }
        outcome(gemmaLike, a, "note milk")
        outcome(textOnly, b, "note milk")
        assertEquals(PlannerPrompt.command(PlannerPrompt.nowText(now, " (IST)")), a.requests.single().system)
        assertEquals("Plan one action. Now: Friday 2026-10-23 08:00.", b.requests.single().system)
        assertNotEquals(a.requests.single().system, b.requests.single().system)
    }

    @Test
    fun aProfileNamingAnUnknownPromptOrFormatFailsWhenTheAppIsWiredNotWhenAPersonSpeaks() {
        assertFailsWith<IllegalArgumentException> { ModelHarness.planner(Host(ScriptedEngine(setOf(Capability.TEXT)) { "" }), ModelProfile(promptId = "nope"), catalog) }
        assertFailsWith<IllegalArgumentException> { ModelHarness.parser(ModelProfile(answerFormat = "json5")) }
        assertEquals(AnswerFormats.LINE, ModelHarness.parser(ModelProfile()))
    }

    @Test
    fun aModelThatCannotHearTurnsVoiceIntoAPlainMessageInsteadOfACrash() = runBlocking<Unit> {
        val engine = ScriptedEngine(textOnly.capabilities) { "hello" }
        val voice = VoiceCommands(ModelHarness.speech(Host(engine), textOnly), CommandPipeline({ _, _ -> "NoAction" }, Gate(), Ledger(InMemoryLedgerStore())) { "p" }, { app.bheem.models.speech.Hints() })
        val result = voice.handle(ByteArray(64), now)
        assertEquals(VoiceResult.Unavailable("this model cannot hear audio"), result)
        assertEquals(0, engine.requests.size)
    }

    @Test
    fun aModelThatCanHearIsAskedToTranscribe() = runBlocking<Unit> {
        val engine = ScriptedEngine(gemmaLike.capabilities) { "remind me tomorrow at 9 AM to call Ravi" }
        val transcript = ModelHarness.speech(Host(engine), gemmaLike).transcribe(ByteArray(64), app.bheem.models.speech.Hints())
        assertEquals("remind me tomorrow at 9 AM to call Ravi", transcript.text)
        assertEquals(1, engine.requests.size)
    }
}
