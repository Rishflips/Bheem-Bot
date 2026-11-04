package app.bheem.core.memory

import app.bheem.core.assistant.Turn
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

class ExtractionTest {
    private val at = LocalDateTime.of(2026, 11, 4, 19, 42)

    @Test
    fun onlyTheOwnersOwnWordsGoToTheModel() {
        val turns = listOf(
            Turn.User("my sister Meena lives in Pune", false, at), Turn.Chat("That sounds lovely, Meena is lucky.", at),
            Turn.Answer("You have nothing on Thu 5 Nov.", emptyList(), at), Turn.Notice("note", at), Turn.User("I like filter coffee", true, at),
        )
        val block = Extraction.userBlock(turns)!!
        assertEquals("Owner: my sister Meena lives in Pune\nOwner: I like filter coffee", block)
    }

    @Test
    fun aQuietDayHasNothingToRead() {
        assertNull(Extraction.userBlock(listOf(Turn.Chat("hello", at))))
        assertNull(Extraction.userBlock(listOf(Turn.User("hi", false, at))), "a single short line is not enough")
    }

    @Test
    fun theReplyIsParsedByCodeIntoAtMostFiveCleanLines() {
        val raw = """
            fact|The owner's sister Meena lives in Pune
            fact|The owner likes filter coffee
            something else
            fact|x
            fact|The owner likes FILTER coffee
            fact|The owner works at a bank <script>
            fact|The owner prefers short answers
            fact|The owner is vegetarian
            fact|The owner has a dog called Bruno
            fact|The owner goes running at 6
        """.trimIndent()
        val facts = Extraction.parse(raw, existing = setOf("the owner's sister meena lives in pune"))
        assertEquals(
            listOf("The owner likes filter coffee", "The owner works at a bank <script>", "The owner prefers short answers", "The owner is vegetarian", "The owner has a dog called Bruno"),
            facts,
        )
    }

    @Test
    fun noneAndNoiseGiveNothing() {
        assertTrue(Extraction.parse("none", emptySet()).isEmpty())
        assertTrue(Extraction.parse("", emptySet()).isEmpty())
        assertTrue(Extraction.parse("<think>hmm</think>none", emptySet()).isEmpty())
        assertTrue(Extraction.parse("fact|see http://example.com for more", emptySet()).isEmpty(), "no links")
    }

    private class Engine(val reply: String) : LlmEngine {
        var request: GenerationRequest? = null

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            this.request = request
            return Completion(reply, 300, 2000, 12)
        }

        override suspend fun unload() = Unit
    }

    private class Host(val engine: LlmEngine) : ModelHost {
        var released = 0
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Unloaded)

        override suspend fun acquire(): LlmEngine = engine

        override fun release() {
            released++
        }

        override fun trim() = Unit

        override suspend fun shutdown() = Unit

        override fun abortLoad(reason: String) = Unit
    }

    @Test
    fun theExtractorAsksOnceWithNoToolsAndReleasesTheModel() = runBlocking<Unit> {
        val engine = Engine("fact|The owner likes filter coffee")
        val host = Host(engine)
        val turns = listOf(Turn.User("I like filter coffee and long walks", false, at), Turn.User("my sister Meena lives in Pune", false, at))
        val facts = MemoryExtractor(host).extract(turns, emptySet())
        assertEquals(listOf("The owner likes filter coffee"), facts)
        val sent = checkNotNull(engine.request)
        assertTrue(sent.maxOutputTokens <= 200)
        assertEquals(0.0, sent.temperature)
        assertTrue(sent.user.contains("Owner: I like filter coffee"))
        assertEquals(1, host.released)
    }

    @Test
    fun withNothingToReadTheModelIsNeverAsked() = runBlocking<Unit> {
        val engine = Engine("fact|invented")
        assertTrue(MemoryExtractor(Host(engine)).extract(listOf(Turn.User("hi", false, at)), emptySet()).isEmpty())
        assertNull(engine.request)
    }
}
