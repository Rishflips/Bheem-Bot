package app.bheem.eval.gate

import app.bheem.core.planner.PlannerPrompt
import app.bheem.eval.data.AdversarialCase
import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Dataset
import app.bheem.eval.data.Jsonl
import app.bheem.eval.data.NotificationCase
import app.bheem.eval.report.Thresholds
import app.bheem.eval.run.RunResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

class GoldenGateTest {
    private val eval: Path = Paths.get("").toAbsolutePath().let { if (it.fileName.toString() == "eval") it else it.resolve("eval") }

    private fun text(path: String) = Files.readString(eval.resolve(path))

    @Test
    fun theCommittedGoldenOutputsPassTheGateWithOnlyTheListedKnownFailures() {
        val thresholds = Jsonl.json.decodeFromString(Thresholds.serializer(), text("thresholds.json"))
        val known = Jsonl.json.decodeFromString(ListSerializer(KnownFailure.serializer()), text("gate-known-failing.json"))
        val dataset = Dataset(
            Jsonl.readText<CommandCase>(text("data/commands.jsonl")) + Jsonl.readText<CommandCase>(text("data/assistant.jsonl")),
            Jsonl.readText<NotificationCase>(text("data/notifications.jsonl")),
            Jsonl.readText<AdversarialCase>(text("data/adversarial.jsonl")),
        )
        val runs = Files.list(eval.resolve("golden")).use { s -> s.filter { it.fileName.toString().endsWith(".json") }.toList() }
            .associate { it.fileName.toString() to Jsonl.json.decodeFromString(RunResult.serializer(), Files.readString(it)) }
        assertEquals(4, runs.size)
        val expectedPins = Jsonl.json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), text("gate-data-pins.json"))
        val actualPins = expectedPins.keys.associateWith { DataPins.hash(text("data/$it")) }
        val result = QualityGate.evaluate(runs, dataset, thresholds, known, DataPins(expectedPins, actualPins))
        assertEquals(emptyList(), result.problems)
        assertTrue(known.all { k -> k.reason.isNotBlank() })
    }

    @Test
    fun thresholdsCarryTheAssistantLimits() {
        val thresholds = Jsonl.json.decodeFromString(Thresholds.serializer(), text("thresholds.json"))
        assertEquals(0.90, thresholds.assistantActionMin)
        assertEquals(0.85, thresholds.assistantArgsMin)
    }

    @Test
    fun theAssistantGoldensWereRecordedWithTheShippedPromptVersion() {
        val versions = Files.list(eval.resolve("golden")).use { st -> st.filter { it.fileName.toString().startsWith("assistant-") && it.fileName.toString().endsWith(".json") }.toList() }
            .map { Jsonl.json.decodeFromString(RunResult.serializer(), Files.readString(it)).promptVersion.removeSuffix("+rescored") }
        assertEquals(2, versions.size)
        assertTrue(versions.all { it == PlannerPrompt.VERSION }, "goldens recorded with $versions but the shipped prompt is ${PlannerPrompt.VERSION}: re-run scripts/bench-release.ps1")
    }
}
