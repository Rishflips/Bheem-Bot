package app.bheem.core.gate

import app.bheem.core.action.ProposedAction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class FactReplaceGateTest {
    private val now = LocalDateTime.of(2026, 11, 5, 9, 0)
    private val old = "I live in Chennai"

    private fun gate(known: Collection<String> = listOf(old)) = Gate(knownFacts = { known })

    private fun decide(action: Map<String, String>, known: Collection<String> = listOf(old)) =
        gate(known).decide(GateRequest(ProposedAction("RememberFact", action), Provenance.USER, "I live in Bengaluru", "bar", now))

    private fun proposed(d: GateDecision) = assertIs<GateDecision.Propose>(d)

    private fun rejected(d: GateDecision): String = assertIs<GateDecision.Reject>(d).reason

    @Test
    fun replacesIsAcceptedOnlyWhenItExactlyMatchesAConfirmedFact() {
        val p = proposed(decide(mapOf("text" to "I live in Bengaluru", "replaces" to old)))
        assertEquals(old, p.action.args["replaces"])
        assertEquals("I live in Bengaluru", p.action.args["text"])

        assertTrue("replaces" in rejected(decide(mapOf("text" to "I live in Bengaluru", "replaces" to "I live in Pune"))))
        assertTrue("replaces" in rejected(decide(mapOf("text" to "I live in Bengaluru", "replaces" to "i live in chennai"))))
        assertTrue("replaces" in rejected(decide(mapOf("text" to "I live in Bengaluru", "replaces" to old), known = emptyList())))
        assertTrue("replaces" in rejected(decide(mapOf("text" to "I live in Bengaluru", "replaces" to "  "))))
    }

    @Test
    fun aRememberFactWithoutReplacesStillWorks() {
        val p = proposed(decide(mapOf("text" to "I like filter coffee")))
        assertTrue("replaces" !in p.action.args)
    }

    @Test
    fun anyOtherNewArgumentOnRememberFactIsRejected() {
        val d = decide(mapOf("text" to "I live in Bengaluru", "old" to old))
        assertTrue("unknown arg" in rejected(d))
    }
}
