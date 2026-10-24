package app.bheem.eval.cli

import app.bheem.eval.data.Jsonl
import app.bheem.eval.speech.RecordingCheck
import app.bheem.eval.speech.Take
import java.nio.file.Paths

object CheckRecordings {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 1) { "usage: CheckRecordings <recordings folder with manifest.jsonl and audio/>" }
        val folder = Paths.get(args[0])
        val takes = Jsonl.read<Take>(folder.resolve("manifest.jsonl"))
        print(RecordingCheck.report(RecordingCheck.check(takes, folder.resolve("audio"))))
    }
}
