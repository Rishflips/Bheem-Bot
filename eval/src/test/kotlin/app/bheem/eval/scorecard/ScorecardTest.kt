package app.bheem.eval.scorecard

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Dataset
import app.bheem.eval.data.Jsonl
import app.bheem.eval.data.AdversarialCase
import app.bheem.eval.data.NotificationCase
import app.bheem.eval.report.Thresholds
import app.bheem.eval.run.RunResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ScorecardTest {
    private val eval: Path = Paths.get("").toAbsolutePath().let { if (it.fileName.toString() == "eval") it else it.resolve("eval") }

    private fun text(path: String) = Files.readString(eval.resolve(path))

    private val dataset = Dataset(
        Jsonl.readText<CommandCase>(text("data/commands.jsonl")) + Jsonl.readText<CommandCase>(text("data/assistant.jsonl")) + Jsonl.readText<CommandCase>(text("data/assistant-heldout.jsonl")),
        Jsonl.readText<NotificationCase>(text("data/notifications.jsonl")),
        Jsonl.readText<AdversarialCase>(text("data/adversarial.jsonl")),
    )

    private fun golden(name: String) = Jsonl.json.decodeFromString(RunResult.serializer(), text("golden/$name"))

    private val runs = listOf(
        golden("assistant-line2-gpu.json"), golden("assistant-line2-cpu.json"),
        golden("assistant-heldout-line2-gpu.json"), golden("assistant-heldout-line2-cpu.json"),
        golden("full-english-v35-gpu.json"), golden("full-english-v35-cpu.json"),
    )

    @Test
    fun aScorecardHasOneRowPerSetAndBackendFromTheSavedRuns() {
        val card = ScorecardBuilder.build("gemma-4-e2b", runs, dataset)
        assertEquals("gemma-4-e2b", card.modelId)
        assertEquals(6, card.rows.size)
        assertEquals(setOf("assistant-dev", "held-out", "english-full"), card.rows.map { it.set }.toSet())
        assertEquals(setOf("GPU", "CPU"), card.rows.map { it.backend }.toSet())
    }

    @Test
    fun theRowsReproduceTheNumbersTheGateAndTheStageSixEvidenceReport() {
        val card = ScorecardBuilder.build("gemma-4-e2b", runs, dataset)
        val dev = assertNotNull(card.row("assistant-dev", "GPU"))
        assertEquals(0.97, dev.action, 0.005)
        assertEquals(0.92, dev.rawAction!!, 0.005)
        val held = assertNotNull(card.row("held-out", "GPU"))
        assertEquals(1.0, held.action, 0.005)
        assertEquals(0.933, held.rawAction!!, 0.005)
        assertEquals(30, held.cases)
    }

    @Test
    fun anotherModelsRunsAreLeftOut() {
        val card = ScorecardBuilder.build("some-other-model", runs, dataset)
        assertTrue(card.rows.isEmpty())
    }

    @Test
    fun theMarkdownNamesTheModelAndNeverCarriesAHeldOutCaseIdOrText() {
        val md = ScorecardBuilder.markdown(ScorecardBuilder.build("gemma-4-e2b", runs, dataset), licence = "Apache-2.0", runtime = "litert-lm", date = "2026-11-03")
        assertTrue("gemma-4-e2b" in md)
        assertTrue("held-out" in md)
        assertTrue("Apache-2.0" in md && "litert-lm" in md)
        assertFalse(Regex("ast-h\\d").containsMatchIn(md))
        val heldOutTexts = dataset.commands.filter { it.id.startsWith("ast-h") }.map { it.text }
        assertTrue(heldOutTexts.none { it in md })
    }

    private fun row(set: String, backend: String, action: Double, p95: Long = 1000, raw: Double? = null) =
        ScoreRow(set, backend, 100, raw ?: action, action, action, 1.0, 40000, p95 / 2, p95, 8.0, 2300, 1, true)

    private fun card(vararg rows: ScoreRow) = Scorecard("m", "proposer-v3.8-line", "device", rows.toList())

    private val rule = Thresholds(0.98, 0.9, 0.85, 0.9, 0.02, 0.05, 3000, 1500, 15000, swapMaxAccuracyDrop = 0.02, swapMaxFirstChunkRatio = 1.25)

    @Test
    fun theSwapRuleAllowsAtMostTwoPointsLessAccuracyAndAQuarterMoreFirstTokenTime() {
        val primary = card(row("assistant-dev", "GPU", 0.97, 1000))
        assertTrue(SwapRule.compare(card(row("assistant-dev", "GPU", 0.95, 1250)), primary, rule).all { it.pass })
        val worse = SwapRule.compare(card(row("assistant-dev", "GPU", 0.94, 1300)), primary, rule)
        assertEquals(setOf("action", "model alone", "args", "first token p95"), worse.filter { !it.pass }.map { it.check }.toSet())
    }

    @Test
    fun aCandidateMustBeMeasuredOnEverySetAndBackendThePrimaryWas() {
        val primary = card(row("assistant-dev", "GPU", 0.97), row("held-out", "GPU", 1.0))
        val findings = SwapRule.compare(card(row("assistant-dev", "GPU", 0.97)), primary, rule)
        assertTrue(findings.any { it.set == "held-out" && !it.pass && it.check == "measured" })
    }

    @Test
    fun theRuleIsInThresholdsAsData() {
        val real = Jsonl.json.decodeFromString(Thresholds.serializer(), text("thresholds.json"))
        assertEquals(0.02, real.swapMaxAccuracyDrop)
        assertEquals(1.25, real.swapMaxFirstChunkRatio)
    }

    private fun rowOf(set: String, backend: String, cases: Int = 100, complete: Boolean = true, prompt: String = "v3.8", json: Double = 1.0, leak: Double? = null, action: Double = 0.97) =
        ScoreRow(set, backend, cases, action, action, action, json, 40000, 500, 1000, 8.0, 2300, 1, complete, leak, prompt, "dev")

    @Test
    fun twoRunsForOneSetAndBackendAreAnErrorInsteadOfOneBeingPickedAtRandom() {
        val first = golden("assistant-line2-gpu.json")
        val second = first.copy(device = first.device.copy(label = "another-day"))
        val error = kotlin.test.assertFailsWith<IllegalArgumentException> { ScorecardBuilder.build("gemma-4-e2b", listOf(first, second), dataset) }
        assertTrue("assistant-dev" in error.message.orEmpty() && "another-day" in error.message.orEmpty(), error.message)
    }

    @Test
    fun theDeviceLabelFilterPicksOneBatchOfRuns() {
        val first = golden("assistant-line2-gpu.json")
        val second = first.copy(device = first.device.copy(label = "another-day"))
        val card = ScorecardBuilder.build("gemma-4-e2b", listOf(first, second), dataset, deviceLabelPrefix = "another")
        assertEquals(1, card.rows.size)
        assertEquals("another-day", card.rows.single().device)
    }

    @Test
    fun everyRowSaysWhichPromptAndDeviceItCameFromAndMixedPromptsAreCalledOut() {
        val card = ScorecardBuilder.build("gemma-4-e2b", runs, dataset)
        assertTrue(card.rows.all { it.promptVersion.isNotBlank() && it.device.isNotBlank() })
        val md = ScorecardBuilder.markdown(card, "Apache-2.0", "litert-lm", "2026-11-03")
        assertTrue("proposer-v3.5-line" in md && "proposer-v3.8-line" in md)
        assertTrue("different prompt versions" in md, "a card that mixes prompt versions must say so")
    }

    @Test
    fun aCandidateThatIsIncompleteOrMeasuredOnOtherCasesOrAnotherPromptIsNotComparable() {
        val primary = card(rowOf("assistant-dev", "GPU"))
        fun notComparable(c: ScoreRow) = SwapRule.compare(card(c), primary, rule).any { it.check == "comparable" && !it.pass }
        assertTrue(notComparable(rowOf("assistant-dev", "GPU", complete = false)))
        assertTrue(notComparable(rowOf("assistant-dev", "GPU", cases = 20)))
        assertTrue(notComparable(rowOf("assistant-dev", "GPU", prompt = "other")))
        assertTrue(SwapRule.compare(card(rowOf("assistant-dev", "GPU")), primary, rule).all { it.pass })
    }

    @Test
    fun theSwapRuleAlsoChecksJsonValidityRelativelyAndInjectionLeakAgainstTheAbsoluteLimit() {
        val primary = card(rowOf("english-full", "GPU", json = 1.0, leak = 0.025))
        val badJson = SwapRule.compare(card(rowOf("english-full", "GPU", json = 0.95, leak = 0.025)), primary, rule)
        assertTrue(badJson.any { it.check == "JSON validity" && !it.pass })
        val leaky = SwapRule.compare(card(rowOf("english-full", "GPU", json = 1.0, leak = 0.125)), primary, rule)
        assertTrue(leaky.any { it.check == "injection leak" && !it.pass })
        assertTrue(SwapRule.compare(card(rowOf("english-full", "GPU", json = 1.0, leak = 0.025)), primary, rule).all { it.pass })
    }
}
