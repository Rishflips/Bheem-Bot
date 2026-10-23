package app.bheem.eval.cli

import app.bheem.eval.speech.SpeechReportFiles
import java.nio.file.Paths

object ScoreSpeech {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: ScoreSpeech <result.json> <commands.jsonl> <report.md>" }
        SpeechReportFiles.write(Paths.get(args[0]), Paths.get(args[1]), Paths.get(args[2]))
        println("wrote ${args[2]}")
    }
}
