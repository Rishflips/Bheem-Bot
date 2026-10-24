package app.bheem.eval.cli

import app.bheem.eval.speech.RecordingFiles
import app.bheem.eval.speech.RecordingPlanner
import java.nio.file.Paths

object GenerateRecordingSheets {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size >= 2) { "usage: GenerateRecordingSheets <commands.jsonl> <outDir> [speakers] [readersPerCommand] [seed]" }
        val commands = RecordingFiles.englishCommands(Paths.get(args[0]))
        val plan = RecordingPlanner.plan(
            commands,
            speakers = args.getOrNull(2)?.toInt() ?: 6,
            readersPerCommand = args.getOrNull(3)?.toInt() ?: 2,
            seed = args.getOrNull(4)?.toLong() ?: 2026,
        )
        RecordingFiles.write(plan, Paths.get(args[1]))
        println("wrote sheets for ${commands.size} English commands to ${args[1]}")
        print(plan.summary())
    }
}
