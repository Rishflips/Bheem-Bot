package app.bheem.core.planner

import app.bheem.core.gate.Gate
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.Outcome
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class PreRouterTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)

    private fun route(text: String) = PreRouter.route(text)

    @Test
    fun generalKnowledgeTranslationEntertainmentAndCallsGoStraightToNoAction() {
        for (text in listOf(
            "explain how photosynthesis works",
            "Explain quantum computing to me",
            "translate good morning into Tamil",
            "what's the weather like in Chennai",
            "what is the weather tomorrow",
            "play some music",
            "please play the new Rahman song",
            "call my mother",
            "Call Suresh",
            "tell me a joke",
            "define serendipity",
        )) {
            assertEquals(PreRouter.Route.NoAction, route(text), text)
        }
    }

    @Test
    fun memoryQuestionsGoStraightToTheMemoryRoute() {
        for (text in listOf(
            "where did I keep the scooter key",
            "Where did I put my passport",
            "what did I save about Anita",
            "what notes did I save about Anita",
            "which tasks did I add this week",
            "show me my tasks",
            "list my notes",
            "how many tasks are still pending",
            "what did I spend at Swiggy last month",
        )) {
            assertEquals(PreRouter.Route.Memory, route(text), text)
        }
    }

    @Test
    fun realCommandsAreLeftToTheModel() {
        for (text in listOf(
            "remind me to call my mother at 5 pm",
            "call Suresh tomorrow at 11 AM for the review",
            "put call the plumber on my list",
            "tell Arun on WhatsApp I'll be late",
            "tell me when it is 6 pm",
            "add buy milk to my tasks",
            "note down that the locker number is 27",
            "book a call with Suresh next Monday at 11 AM",
            "I paid 380 rupees to Uber just now",
            "play cricket with Ravi on Saturday at 5 PM",
            "send 1500 rupees to Suresh",
        )) {
            assertEquals(PreRouter.Route.Model, route(text), text)
        }
    }

    @Test
    fun theWholeSentenceDecidesNotAWordInTheMiddle() {
        assertEquals(PreRouter.Route.Model, route("remind me to explain the budget to Arun tomorrow at 9 AM"))
        assertEquals(PreRouter.Route.Model, route("save a note: where did I keep the key is a question to ask Ravi"))
    }

    private val store = InMemoryLedgerStore()
    private fun pipeline(onPlan: () -> Unit = {}) =
        CommandPipeline({ _, _ -> onPlan(); "SaveNote|should not run" }, Gate(), Ledger(store)) { "p1" }

    @Test
    fun theNoActionRouteNeverCallsTheModelAndLeavesNoLedgerRow() = runBlocking<Unit> {
        var called = false
        assertEquals(Outcome.Nothing, pipeline { called = true }.handle("explain how photosynthesis works", now))
        assertEquals(false, called)
        assertEquals(emptyList(), store.all())
    }

    @Test
    fun theMemoryRouteAnswersFromMemoryWithoutTheModelAndIsLedgeredAsARead() = runBlocking<Unit> {
        var called = false
        val outcome = assertIs<Outcome.Question>(pipeline { called = true }.handle("where did I keep the scooter key", now))
        assertEquals("where did I keep the scooter key", outcome.query)
        assertEquals(false, called)
        assertEquals(listOf(LedgerEvent.READ), store.all().map { it.event })
    }

    @Test
    fun anythingElseStillGoesToTheModel() = runBlocking<Unit> {
        var called = false
        pipeline { called = true }.handle("add buy milk to my tasks", now)
        assertEquals(true, called)
    }
}
