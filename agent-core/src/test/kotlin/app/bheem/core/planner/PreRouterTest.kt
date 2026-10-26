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
            "call mom later", "call mom this weekend", "call mom in an hour", "call mom in two hours", "call the doctor this afternoon", "call Ravi at night",
            "play tennis with Ravi this weekend", "play badminton after work",
            "explain to Ravi on WhatsApp that I will be late", "explain the plan to Ravi on WhatsApp tomorrow", "summarise my notes about the Chennai trip",
            "show me my reminders", "what reminders do I have", "how many reminders are left",
            "list my tasks and add buy milk", "show my notes then remind me at 5 pm to call Ravi",
            "send Meena a WhatsApp saying I paid 200 rupees", "tell Arun I paid 500 rupees to the plumber",
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

    @Test
    fun anImperativePaymentIsRefusedByCodeBeforeTheModel() {
        for (text in listOf("send 1500 rupees to Suresh", "transfer 5000 rupees to Karthik's account", "pay Ravi 840 rupees", "pay 500 rs to Arun", "please send \u20B9200 to Meena")) {
            assertEquals(PreRouter.Route.Payment, route(text), text)
        }
        assertEquals(PreRouter.Route.Model, route("I paid 380 rupees to Uber just now"))
        assertEquals(PreRouter.Route.Model, route("remind me to pay rent tomorrow at 9 am"))
    }
}
