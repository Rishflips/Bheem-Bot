package app.bheem.eval.report

import app.bheem.eval.data.Jsonl
import app.bheem.eval.run.RunResult
import app.bheem.eval.run.Summarizer
import app.bheem.eval.run.Summary
import java.nio.file.Files
import java.nio.file.Path

data class LoadedResults(val summaries: List<Summary>, val unreadable: List<String>)

object ResultLoader {
    fun load(files: List<Path>): LoadedResults {
        val summaries = mutableListOf<Summary>()
        val unreadable = mutableListOf<String>()
        for (file in files) {
            try {
                val run = Jsonl.json.decodeFromString(RunResult.serializer(), Files.readString(file, Charsets.UTF_8))
                summaries += Summarizer.summarize(run)
            } catch (e: Exception) {
                val reason = e.message?.lineSequence()?.firstOrNull().orEmpty().take(120)
                unreadable += "${file.fileName}: $reason"
            }
        }
        return LoadedResults(summaries, unreadable)
    }
}
