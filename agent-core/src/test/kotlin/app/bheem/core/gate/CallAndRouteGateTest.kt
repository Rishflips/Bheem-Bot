package app.bheem.core.gate

import app.bheem.core.action.ActionCatalog
import app.bheem.core.action.ProposedAction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CallAndRouteGateTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val gate = Gate(GatePolicy(tapForEveryAction = false))

    private fun decide(action: ProposedAction, text: String, provenance: Provenance = Provenance.USER) = gate.decide(GateRequest(action, provenance, text, "bar", now))

    private fun call(name: String = "Amma", number: String = "+919876543210") = ProposedAction("PlaceCall", mapOf("name" to name, "number" to number))

    private fun route(destination: String) = ProposedAction("OpenRoute", mapOf("destination" to destination))

    @Test
    fun aCallToANamedPersonIsOneTapAwayAndNeverRunsOnItsOwn() {
        val d = assertIs<GateDecision.Propose>(decide(call(), "call Amma"))
        assertEquals(RiskLevel.L1, d.level)
        assertTrue(d.requiresTap)
    }

    @Test
    fun aRouteIsOneTapAwayAndNeverRunsOnItsOwn() {
        val d = assertIs<GateDecision.Propose>(decide(route("the office"), "route to the office"))
        assertEquals(RiskLevel.L1, d.level)
        assertTrue(d.requiresTap)
    }

    @Test
    fun theNumberMustLookLikeAPhoneNumber() {
        for (bad in listOf("98", "abc", "+91 98765 43210", "9876543210; rm", "+", "1234567890123456", "tel:123456")) {
            assertIs<GateDecision.Reject>(decide(call(number = bad), "call Amma"), bad)
        }
        for (good in listOf("9876543210", "+919876543210", "0441234567")) {
            assertIs<GateDecision.Propose>(decide(call(number = good), "call Amma"), good)
        }
    }

    @Test
    fun theNameMustBeInTheCommand() {
        val d = assertIs<GateDecision.Reject>(decide(call(name = "Suresh"), "call Amma"))
        assertEquals("recipient is not in the command", d.reason)
    }

    @Test
    fun theDestinationMustBeInTheCommandAndPlain() {
        assertIs<GateDecision.Reject>(decide(route("the airport"), "route to the office"))
        assertIs<GateDecision.Reject>(decide(route("the office\u0007"), "route to the office\u0007"))
        assertIs<GateDecision.Reject>(decide(route("x".repeat(200)), "route to " + "x".repeat(200)))
    }

    @Test
    fun neitherIsAllowedFromUntrustedText() {
        assertIs<GateDecision.Reject>(decide(call(), "call Amma", Provenance.TAINTED))
        assertIs<GateDecision.Reject>(decide(route("the office"), "route to the office", Provenance.TAINTED))
    }

    @Test
    fun theModelIsNeverOfferedThem() {
        assertFalse("PlaceCall" in ActionCatalog.offeredToModel)
        assertFalse("OpenRoute" in ActionCatalog.offeredToModel)
        assertEquals(setOf("PlaceCall", "OpenRoute"), ActionCatalog.codeOnly)
    }
}
