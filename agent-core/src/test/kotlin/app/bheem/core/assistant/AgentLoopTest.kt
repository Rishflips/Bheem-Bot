package app.bheem.core.assistant

import app.bheem.core.memory.Citation
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelState
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class AgentLoopTest {
    private val at = LocalDateTime.of(2026, 11, 5, 9, 0)

    private class ScriptedEngine(vararg outputs: String, private val partials: Map<Int, List<String>> = emptyMap()) : LlmEngine {
        private val script = outputs.toList()
        private var call = 0
        val requests = mutableListOf<GenerationRequest>()

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            requests += request
            val index = call++
            partials[index]?.forEach { request.onText?.invoke(it) }
            return Completion(script.getOrElse(index) { script.lastOrNull().orEmpty() }, 100, 200, 1)
        }

        override suspend fun unload() = Unit
    }

    private class FakeTools(var result: ToolResult) : AgentTools {
        val searches = mutableListOf<String>()
        val schedules = mutableListOf<String>()

        override fun searchMemory(query: String): ToolResult {
            searches += query
            return result
        }

        override fun schedule(whenWords: String): ToolResult {
            schedules += whenWords
            return result
        }
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
    fun aPlainAnswerIsOneEngineCallWithNoCitations() = runBlocking<Unit> {
        val engine = ScriptedEngine("Volcanoes form where magma reaches the surface.")
        val tools = FakeTools(ToolResult("nothing", emptyList()))
        val reply = AgentLoop(Host(engine), tools).reply(emptyList(), "tell me about volcanoes", null, null)

        assertEquals("Volcanoes form where magma reaches the surface.", reply.text)
        assertNull(reply.offer)
        assertTrue(reply.citations.isEmpty())
        assertEquals(1, engine.requests.size)
        assertTrue(tools.searches.isEmpty() && tools.schedules.isEmpty())
    }

    @Test
    fun aSearchToolCallsTheToolAndThenAnswersFromItsResult() = runBlocking<Unit> {
        val citation = Citation("n1", "note", at)
        val engine = ScriptedEngine("Tool|search_memory|passport", "Your passport is in the drawer.")
        val tools = FakeTools(ToolResult("You have a note: passport in the drawer.", listOf(citation)))

        val reply = AgentLoop(Host(engine), tools).reply(emptyList(), "where is my passport", null, null)

        assertEquals(listOf("passport"), tools.searches)
        assertEquals(2, engine.requests.size)
        assertTrue("You have a note: passport in the drawer." in engine.requests[1].user, engine.requests[1].user)
        assertTrue("Result of search_memory (the owner's own records):" in engine.requests[1].user, engine.requests[1].user)
        assertEquals("Your passport is in the drawer.", reply.text)
        assertEquals(listOf(citation), reply.citations)
    }

    @Test
    fun anUnknownToolNameCallsNoToolAndStripsTheLine() = runBlocking<Unit> {
        val engine = ScriptedEngine("Tool|weather|delhi\nIt is sunny in Delhi.")
        val tools = FakeTools(ToolResult("nothing", emptyList()))

        val reply = AgentLoop(Host(engine), tools).reply(emptyList(), "what is the weather in Delhi", null, null)

        assertEquals("It is sunny in Delhi.", reply.text)
        assertTrue(tools.searches.isEmpty() && tools.schedules.isEmpty())
        assertEquals(1, engine.requests.size)
        assertTrue(reply.citations.isEmpty())
    }

    @Test
    fun groundingSkipsTheToolStepAndCallsTheEngineOnce() = runBlocking<Unit> {
        val citation = Citation("r1", "reminder", at)
        val grounding = ToolResult("You have two reminders: call Arun at 9:00 AM.", listOf(citation))
        val engine = ScriptedEngine("You have two reminders, starting with Arun.")
        val tools = FakeTools(ToolResult("nothing", emptyList()))

        val reply = AgentLoop(Host(engine), tools).reply(emptyList(), "what is on tomorrow", null, null, grounding = grounding)

        assertEquals(1, engine.requests.size)
        assertTrue(grounding.text in engine.requests[0].user, engine.requests[0].user)
        assertTrue("Result of search_memory (the owner's own records):" in engine.requests[0].user, engine.requests[0].user)
        assertEquals("You have two reminders, starting with Arun.", reply.text)
        assertEquals(listOf(citation), reply.citations)
        assertTrue(tools.searches.isEmpty() && tools.schedules.isEmpty())
    }

    @Test
    fun aToolLineInTheSecondOutputIsRemoved() = runBlocking<Unit> {
        val engine = ScriptedEngine(
            "Tool|search_memory|passport",
            "Here you go.\nTool|search_memory|again\nYour passport is in the drawer.",
        )
        val tools = FakeTools(ToolResult("You have a note: passport in the drawer.", emptyList()))

        val reply = AgentLoop(Host(engine), tools).reply(emptyList(), "where is my passport", null, null)

        assertEquals("Here you go. Your passport is in the drawer.", reply.text)
    }

    @Test
    fun theScheduleToolIsCalledWithTheOwnersTimeWords() = runBlocking<Unit> {
        val engine = ScriptedEngine("Tool|schedule|tomorrow", "You have two things tomorrow.")
        val tools = FakeTools(ToolResult("You have 2 reminders tomorrow.", emptyList()))

        val reply = AgentLoop(Host(engine), tools).reply(emptyList(), "what is on tomorrow", null, null)

        assertEquals(listOf("tomorrow"), tools.schedules)
        assertEquals("You have two things tomorrow.", reply.text)
    }

    @Test
    fun streamingNeverShowsAToolLineOrAFragmentThatCouldBecomeOne() = runBlocking<Unit> {
        val engine = ScriptedEngine(
            "Tool|search_memory|passport",
            "Your passport is in the drawer.",
            partials = mapOf(0 to listOf("Tool", "Tool|sea")),
        )
        val seen = mutableListOf<String>()

        AgentLoop(Host(engine), FakeTools(ToolResult("You have a note: passport in the drawer.", emptyList())))
            .reply(emptyList(), "where is my passport", null, null) { seen += it }

        assertTrue(seen.none { "Tool|" in it }, seen.toString())
        assertTrue(seen.none { "Tool" in it }, seen.toString())
    }
}
