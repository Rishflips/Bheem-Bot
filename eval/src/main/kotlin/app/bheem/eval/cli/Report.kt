package app.bheem.eval.cli

import app.bheem.eval.data.Jsonl
import app.bheem.eval.report.ReportBuilder
import app.bheem.eval.report.ResultLoader
import app.bheem.eval.report.Thresholds
import java.nio.file.Files
import java.nio.file.Paths

object Report {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: Report <thresholds.json> <resultsDir> <output.md>" }
        val thresholds = Jsonl.json.decodeFromString(Thresholds.serializer(), Files.readString(Paths.get(args[0])))
        val resultFiles = Files.list(Paths.get(args[1])).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".json") }.sorted().toList()
        }
        val loaded = ResultLoader.load(resultFiles)
        loaded.unreadable.forEach { System.err.println("unreadable result file: $it") }
        Files.writeString(Paths.get(args[2]), ReportBuilder.markdown(loaded.summaries, thresholds, loaded.unreadable))
        println("wrote ${args[2]} from ${loaded.summaries.size} result files (${loaded.unreadable.size} unreadable)")
    }
}
