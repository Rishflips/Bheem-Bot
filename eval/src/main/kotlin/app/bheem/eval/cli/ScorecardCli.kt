package app.bheem.eval.cli

import app.bheem.eval.data.AdversarialCase
import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Dataset
import app.bheem.eval.data.Jsonl
import app.bheem.eval.data.NotificationCase
import app.bheem.eval.report.Thresholds
import app.bheem.eval.run.RunResult
import app.bheem.eval.scorecard.ScorecardBuilder
import app.bheem.eval.scorecard.SwapRule
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Writes a model's scorecard from saved run files and, when a primary model is named, the relative swap rule.
 * usage: ScorecardCli thresholds.json dataDir resultsDir registry.json modelId output.md [primaryModelId]
 */
object ScorecardCli {
    @Serializable
    private data class RegistryModel(val id: String, val runtime: String = "unknown", val license: String = "unknown")

    @Serializable
    private data class Registry(val models: List<RegistryModel>)

    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size in 6..7) { "usage: ScorecardCli <thresholds.json> <dataDir> <resultsDir> <registry.json> <modelId> <output.md> [primaryModelId]" }
        val thresholds = Jsonl.json.decodeFromString(Thresholds.serializer(), Files.readString(Paths.get(args[0])))
        val data = Paths.get(args[1])
        fun text(name: String) = Files.readString(data.resolve(name))
        val dataset = Dataset(
            Jsonl.readText<CommandCase>(text("commands.jsonl")) + Jsonl.readText<CommandCase>(text("assistant.jsonl")) + Jsonl.readText<CommandCase>(text("assistant-heldout.jsonl")),
            Jsonl.readText<NotificationCase>(text("notifications.jsonl")),
            Jsonl.readText<AdversarialCase>(text("adversarial.jsonl")),
        )
        val files: List<Path> = Files.list(Paths.get(args[2])).use { s -> s.filter { it.fileName.toString().endsWith(".json") }.sorted().toList() }
        val runs = files.mapNotNull { runCatching { Jsonl.json.decodeFromString(RunResult.serializer(), Files.readString(it)) }.getOrNull() }
        val registry = runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(Registry.serializer(), Files.readString(Paths.get(args[3]))).models }.getOrDefault(emptyList())
        val modelId = args[4]
        val entry = registry.firstOrNull { it.id == modelId }
        val card = ScorecardBuilder.build(modelId, runs, dataset)
        require(card.rows.isNotEmpty()) { "no saved runs for model $modelId in ${args[2]}" }
        val md = StringBuilder(
            ScorecardBuilder.markdown(
                card,
                licence = entry?.license ?: "not in the registry; read the licence text before any use",
                runtime = entry?.runtime ?: "not in the registry",
                date = LocalDate.now().toString(),
            ),
        )
        if (args.size == 7) {
            val primary = ScorecardBuilder.build(args[6], runs, dataset)
            require(primary.rows.isNotEmpty()) { "no saved runs for the primary model ${args[6]}" }
            md.appendLine().append(SwapRule.markdown(SwapRule.compare(card, primary, thresholds), modelId, args[6]))
        }
        Files.createDirectories(Paths.get(args[5]).toAbsolutePath().parent)
        Files.writeString(Paths.get(args[5]), md.toString())
        println("wrote ${args[5]} from ${card.rows.size} rows")
    }
}
