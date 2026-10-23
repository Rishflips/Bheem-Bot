package app.bheem.eval.cli

import app.bheem.eval.data.AdversarialCase
import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Dataset
import app.bheem.eval.data.Jsonl
import app.bheem.eval.data.NotificationCase
import app.bheem.eval.io.AtomicFile
import app.bheem.eval.prompt.Prompts
import app.bheem.eval.run.Rescorer
import app.bheem.eval.run.RunResult
import java.nio.file.Files
import java.nio.file.Paths

object Rescore {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 4) { "usage: Rescore <result.json> <dataDir> <promptSet> <output.json>" }
        val run = Jsonl.json.decodeFromString(RunResult.serializer(), Files.readString(Paths.get(args[0])))
        val dir = Paths.get(args[1])
        val dataset = Dataset(
            Jsonl.readText<CommandCase>(Files.readString(dir.resolve("commands.jsonl"))),
            Jsonl.readText<NotificationCase>(Files.readString(dir.resolve("notifications.jsonl"))),
            Jsonl.readText<AdversarialCase>(Files.readString(dir.resolve("adversarial.jsonl"))),
        )
        val rescored = Rescorer.rescore(run, dataset, Prompts.named(args[2]).format)
        AtomicFile.writeText(Paths.get(args[3]), Jsonl.json.encodeToString(RunResult.serializer(), rescored))
        println("rescored ${rescored.cases.size} cases from ${args[0]} into ${args[3]}")
    }
}
