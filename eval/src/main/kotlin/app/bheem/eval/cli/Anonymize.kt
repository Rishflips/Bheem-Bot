package app.bheem.eval.cli

import app.bheem.eval.data.Jsonl
import app.bheem.eval.privacy.Anonymizer
import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.serialization.Serializable

@Serializable
data class UnlabeledNotification(val id: String, val source: String, val text: String)

object Anonymize {
    @JvmStatic
    fun main(args: Array<String>) {
        require(args.size == 3) { "usage: Anonymize <input.txt> <source package> <output.jsonl>" }
        val lines = Files.readAllLines(Paths.get(args[0]), Charsets.UTF_8).filter { it.isNotBlank() }
        val kept = lines.mapNotNull { Anonymizer.scrub(it) }
        val items = kept.mapIndexed { i, text -> UnlabeledNotification("real-%03d".format(i + 1), args[1], text) }
        Jsonl.write(Paths.get(args[2]), items)
        println("kept ${kept.size} of ${lines.size} messages (OTP messages are dropped). Read the output before sharing it.")
    }
}
