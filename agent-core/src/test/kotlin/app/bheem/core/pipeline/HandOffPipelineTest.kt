package app.bheem.core.pipeline

import app.bheem.core.gate.Gate
import app.bheem.core.gate.GatePolicy
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class HandOffPipelineTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)
    private var counter = 0
    private var planned = 0

    private fun pipeline(planner: String = "NoAction", phones: PhoneLookup? = PhoneLookup { name -> if (name.equals("Amma", true)) "+919876543210" else null }) =
        CommandPipeline(
            Planner { _, _ -> planned++; planner },
            Gate(GatePolicy()),
            Ledger(InMemoryLedgerStore()),
            phones = phones,
            newId = { "p${counter++}" },
        )

    @Test
    fun callingAPersonWithASavedNumberMakesACallCardWithoutTheModel() = runBlocking<Unit> {
        val outcome = assertIs<Outcome.Card>(pipeline().handle("call Amma", now))
        assertEquals("PlaceCall", outcome.action.action)
        assertEquals(mapOf("name" to "Amma", "number" to "+919876543210"), outcome.action.args)
        assertTrue(outcome.requiresTap)
        assertEquals(0, planned)
    }

    @Test
    fun callingSomeoneWithNoSavedNumberIsRefusedWithAHint() = runBlocking<Unit> {
        val outcome = assertIs<Outcome.Refused>(pipeline().handle("call Suresh", now))
        assertTrue(outcome.reason.contains("Suresh") && outcome.reason.contains("number"), outcome.reason)
    }

    @Test
    fun withoutAPhoneBookACallRequestIsStillNoAction() = runBlocking<Unit> {
        assertEquals(Outcome.Nothing, pipeline(phones = null).handle("call Amma", now))
    }

    @Test
    fun aRouteRequestMakesARouteCardWithoutTheModel() = runBlocking<Unit> {
        for (text in listOf("route to the office", "navigate to Phoenix Mall", "directions to the airport", "take me to the station")) {
            val outcome = assertIs<Outcome.Card>(pipeline().handle(text, now), text)
            assertEquals("OpenRoute", outcome.action.action, text)
            assertTrue(outcome.requiresTap)
        }
        assertEquals(0, planned)
    }

    @Test
    fun aCommandWithATimeOrAnotherTaskIsNotACallRequest() = runBlocking<Unit> {
        for (text in listOf("call Amma tomorrow at 5 pm", "remind me to call Amma", "call Amma and add buy milk")) {
            val outcome = pipeline(planner = "NoAction").handle(text, now)
            assertEquals(Outcome.Nothing, outcome, text)
        }
        assertEquals(3, planned)
    }

    @Test
    fun aCallOrRouteLineFromTheModelIsRefusedBecauseOnlyCodeMayBuildThem() = runBlocking<Unit> {
        val call = assertIs<Outcome.Refused>(pipeline(planner = "PlaceCall|Amma|+919999999999").handle("let us call Amma sometime", now))
        assertEquals("PlaceCall is not offered to the model", call.reason)
        assertEquals(1, planned)
        val route = assertIs<Outcome.Refused>(pipeline(planner = "OpenRoute|the office").handle("let us route to the office sometime", now))
        assertEquals("OpenRoute is not offered to the model", route.reason)
        assertEquals(2, planned)
    }

    @Test
    fun aCallLineFromTheModelWithAKnownNameAndNumberIsStillRefused() = runBlocking<Unit> {
        val outcome = assertIs<Outcome.Refused>(pipeline(planner = "PlaceCall|Amma|+919876543210").handle("could you ring Amma sometime please", now))
        assertEquals("PlaceCall is not offered to the model", outcome.reason)
    }
}
