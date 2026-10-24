package app.bheem.eval.cli

import app.bheem.eval.data.AdversarialCase
import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Dataset
import app.bheem.eval.data.Jsonl
import app.bheem.eval.data.NotificationCase
import app.bheem.eval.gate.KnownFailure
import app.bheem.eval.gate.QualityGate
import app.bheem.eval.report.Thresholds
import app.bheem.eval.run.RunResult
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.system.exitProcess
import kotlinx.serialization.builtins.ListSerializer

object RunQualityGate {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4) { "usage: RunQualityGate <thresholds.json> <goldenDir> <dataDir> <known-failing.json>" }
        val thresholds = Jsonl.json.decodeFromString(Thresholds.serializer(), Files.readString(Paths.get(args[0])))
        val known = Jsonl.json.decodeFromString(ListSerializer(KnownFailure.serializer()), Files.readString(Paths.get(args[3])))
        val data = Paths.get(args[2])
        val dataset = Dataset(
            Jsonl.readText<CommandCase>(Files.readString(data.resolve("commands.jsonl"))) +
                Jsonl.readText<CommandCase>(Files.readString(data.resolve("assistant.jsonl"))),
            Jsonl.readText<NotificationCase>(Files.readString(data.resolve("notifications.jsonl"))),
            Jsonl.readText<AdversarialCase>(Files.readString(data.resolve("adversarial.jsonl"))),
        )
        val runs = Files.list(Paths.get(args[1])).use { s -> s.filter { it.fileName.toString().endsWith(".json") }.sorted().toList() }
            .associate { it.fileName.toString() to Jsonl.json.decodeFromString(RunResult.serializer(), Files.readString(it)) }
        require(runs.isNotEmpty()) { "no golden result files in ${args[1]}" }
        val result = QualityGate.evaluate(runs, dataset, thresholds, known)
        print(QualityGate.table(result))
        if (!result.ok) exitProcess(1)
        println("quality gate passed (${result.rows.count { !it.pass }} known failures listed)")
    }
}
