package app.bheem.eval.redteam

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Wiring rules in `:app` that a unit test of one class cannot see. */
class AppInvariantsTest {
    private val root: Path = Paths.get("").toAbsolutePath().let { if (it.fileName.toString() == "eval") it.parent else it }

    private fun appSource(): String =
        Files.walk(root.resolve("app/src/main")).use { s -> s.filter { it.toString().endsWith(".kt") }.toList() }.joinToString("\n") { Files.readString(it) }

    @Test
    fun theAppBuildsOneGateAndTheSameInstanceDecidesForTheBarTheVoiceAndTheApprovals() {
        val source = appSource()
        assertEquals(1, Regex("""\bGate\(""").findAll(source).count(), "the app must build exactly one Gate")
        assertTrue(Regex("""CommandPipeline\(\s*planner,\s*gate,""").containsMatchIn(source), "the pipeline must use the shared gate")
        assertTrue(Regex("""ProposalCoordinator\([^)]*\bgate\b""").containsMatchIn(source), "the coordinator must use the shared gate")
    }

    @Test
    fun theGateInTheAppKeepsTapForEveryActionAtItsDefault() {
        val source = appSource()
        assertTrue(Regex("""GatePolicy\(\)""").containsMatchIn(source), "the app should use the default policy")
        assertTrue(!source.contains("tapForEveryAction = false"), "the app must not turn the tap off")
    }
}
