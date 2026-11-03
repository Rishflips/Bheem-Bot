package app.bheem.eval.redteam

import app.bheem.core.planner.ModelHarness
import app.bheem.core.planner.PlannerPrompt
import app.bheem.core.planner.PromptCatalog
import app.bheem.eval.gen.ReferenceClock
import app.bheem.eval.prompt.Prompts
import app.bheem.models.Capability
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelRegistry
import app.bheem.models.host.ModelSelection
import app.bheem.models.host.ModelState
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

/** The registry the app ships must resolve in the harness, or the app would crash while it is being wired at launch. */
class ShippedRegistryTest {
    private val root = Paths.get("").toAbsolutePath().let { if (it.fileName.toString() == "eval") it.parent else it }
    private val models = ModelRegistry.parse(Files.readString(root.resolve("app/src/main/assets/models.json")))

    private class Recording : LlmEngine {
        val requests = mutableListOf<GenerationRequest>()

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            requests += request
            return Completion("NoAction", 1, 1, 1)
        }

        override suspend fun unload() = Unit
    }

    private class Host(val engine: LlmEngine) : ModelHost {
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Unloaded)

        override suspend fun acquire(): LlmEngine = engine

        override fun release() = Unit

        override fun trim() = Unit

        override suspend fun shutdown() = Unit

        override fun abortLoad(reason: String) = Unit
    }

    @Test
    fun everyShippedLlmResolvesItsPromptAndAnswerFormatAndSpeechPath() {
        val llms = models.filter { it.role == "llm" }
        assertTrue(llms.isNotEmpty())
        for (m in llms) {
            PromptCatalog.Default.get(m.profile.promptId)
            ModelHarness.parser(m.profile)
            ModelHarness.planner(Host(Recording()), m.profile)
            ModelHarness.speech(Host(Recording()), m.profile)
        }
    }

    @Test
    fun theAppShipsTheMarkedDefaultAndItAsksForTheMeasuredPromptByteForByte() {
        val gemma = ModelSelection.pick(models, "llm", null)
        assertEquals("gemma-4-e2b", gemma.id)
        assertTrue(Capability.AUDIO_IN in gemma.profile.capabilities)
        val engine = Recording()
        runBlocking { ModelHarness.planner(Host(engine), gemma.profile).plan("note milk", ReferenceClock.NOW) }
        assertEquals(PlannerPrompt.command(Prompts.NOW_TEXT), engine.requests.single().system)
    }

    @Test
    fun theEvalMeasuresTheSameTimeLineThatTheAppSends() {
        val gemma = ModelSelection.pick(models, "llm", null)
        assertEquals(Prompts.NOW_TEXT, PlannerPrompt.nowText(ReferenceClock.NOW, gemma.profile.nowSuffix))
    }
}
