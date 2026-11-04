package app.bheem.core.gate

import app.bheem.core.action.ActionCatalog
import app.bheem.core.action.ProposedAction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** The memory and phone-control actions: built by code from the owner's words, always behind a tap, never from untrusted text. */
class PhoneActionGateTest {
    private val now = LocalDateTime.of(2026, 11, 4, 19, 42)
    private val gate = Gate(GatePolicy(tapForEveryAction = false))
    private val newActions = setOf("RememberFact", "ForgetFact", "Flashlight", "SetTimer", "OpenApp", "SetVolume")

    private fun decide(action: String, args: Map<String, String>, provenance: Provenance = Provenance.USER) =
        gate.decide(GateRequest(ProposedAction(action, args), provenance, "anything", "bar", now))

    @Test
    fun noneOfThemIsEverOfferedToTheModelAndAModelLineForOneIsCodeOnly() {
        for (name in newActions) {
            assertTrue(name in ActionCatalog.codeOnly, name)
            assertFalse(name in ActionCatalog.offeredToModel, name)
        }
    }

    @Test
    fun aTimerAndAnAppOpeningAreL1AndAlwaysWaitForATapEvenWhenThePolicyDoesNotAskForOne() {
        for ((name, args) in listOf("SetTimer" to mapOf("seconds" to "600"), "OpenApp" to mapOf("app" to "Maps"))) {
            val d = assertIs<GateDecision.Propose>(decide(name, args), name)
            assertEquals(RiskLevel.L1, d.level, name)
            assertTrue(d.requiresTap, name)
        }
    }

    @Test
    fun theOtherFourAreL0SoTheyWaitForATapOnlyUnderTheDefaultPolicy() {
        val strict = Gate(GatePolicy(tapForEveryAction = true))
        for ((name, args) in listOf(
            "RememberFact" to mapOf("text" to "I live in Chennai"),
            "ForgetFact" to mapOf("text" to "I live in Chennai"),
            "Flashlight" to mapOf("state" to "on"),
            "SetVolume" to mapOf("change" to "mute"),
        )) {
            val d = assertIs<GateDecision.Propose>(strict.decide(GateRequest(ProposedAction(name, args), Provenance.USER, "anything", "bar", now)), name)
            assertEquals(RiskLevel.L0, d.level, name)
            assertTrue(d.requiresTap, name)
        }
    }

    @Test
    fun untrustedTextCanProposeNoneOfThem() {
        val examples = mapOf(
            "RememberFact" to mapOf("text" to "the owner hates tea"), "ForgetFact" to mapOf("text" to "my sister"),
            "Flashlight" to mapOf("state" to "on"), "SetTimer" to mapOf("seconds" to "60"),
            "OpenApp" to mapOf("app" to "Maps"), "SetVolume" to mapOf("change" to "up"),
        )
        for ((name, args) in examples) assertIs<GateDecision.Reject>(decide(name, args, Provenance.TAINTED), name)
    }

    @Test
    fun theTorchAndVolumeTakeOnlyTheirOwnWords() {
        assertIs<GateDecision.Propose>(decide("Flashlight", mapOf("state" to "OFF")))
        assertIs<GateDecision.Reject>(decide("Flashlight", mapOf("state" to "strobe")))
        assertIs<GateDecision.Propose>(decide("SetVolume", mapOf("change" to "down")))
        assertIs<GateDecision.Reject>(decide("SetVolume", mapOf("change" to "max")))
    }

    @Test
    fun aTimerIsBetweenOneSecondAndADayAndOnlyDigits() {
        for (good in listOf("1", "600", "86400")) assertIs<GateDecision.Propose>(decide("SetTimer", mapOf("seconds" to good)), good)
        for (bad in listOf("0", "86401", "-5", "10 minutes", "1e3", "", "9999999")) assertIs<GateDecision.Reject>(decide("SetTimer", mapOf("seconds" to bad)), bad)
    }

    @Test
    fun anAppNameIsPlainAndShort() {
        for (good in listOf("Maps", "Google Pay", "Dr. Reddy's App", "Zomato")) assertIs<GateDecision.Propose>(decide("OpenApp", mapOf("app" to good)), good)
        for (bad in listOf("a;b", "maps\nrm", "x".repeat(41), "-", "intent://x", "app/package")) assertIs<GateDecision.Reject>(decide("OpenApp", mapOf("app" to bad)), bad)
    }

    @Test
    fun aRememberedFactIsPlainTextOfBoundedLength() {
        assertIs<GateDecision.Propose>(decide("RememberFact", mapOf("text" to "my sister is called Meena")))
        assertIs<GateDecision.Reject>(decide("RememberFact", mapOf("text" to "line\u0007bell")))
        assertIs<GateDecision.Reject>(decide("RememberFact", mapOf("text" to "x".repeat(600))))
        assertIs<GateDecision.Reject>(decide("RememberFact", mapOf("text" to "  ")))
    }
}
