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

    @Test
    fun noModelNameAppearsInTheHarnessOrTheAppCode() {
        val names = Regex("gemma|qwen|lfm|llama|minicpm|moonshine|litert", RegexOption.IGNORE_CASE)
        val allowedFolders = listOf("models-litertlm")
        val problems = listOf("agent-core/src/main", "models/src/main", "app/src/main/kotlin").flatMap { dir ->
            Files.walk(root.resolve(dir)).use { stream -> stream.filter { it.toString().endsWith(".kt") }.toList() }
        }.filter { path -> allowedFolders.none { path.toString().contains(it) } }.flatMap { path ->
            Files.readAllLines(path).mapIndexedNotNull { i, line ->
                // AppGraph may name the runtime library it builds; model names belong in models.json and its profile.
                if (names.containsMatchIn(line) && !(path.fileName.toString() == "AppGraph.kt" && line.contains("LiteRtLmEngine")) && !line.contains("LITERT_LM_VERSION") && !line.contains("import app.bheem.models.litertlm")) "${path.fileName}:${i + 1}: $line" else null
            }
        }
        assertEquals(emptyList(), problems)
    }
}
