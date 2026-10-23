package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Jsonl
import java.nio.file.Files
import java.nio.file.Path

object SpeechReportFiles {
    fun write(resultJson: Path, commandsJsonl: Path, out: Path) {
        val result = Jsonl.json.decodeFromString(SpeechRunResult.serializer(), Files.readString(resultJson, Charsets.UTF_8))
        val commands = Jsonl.read<CommandCase>(commandsJsonl).associateBy { it.id }
        out.parent?.let { Files.createDirectories(it) }
        Files.writeString(out, SpeechReportBuilder.markdown(result, commands), Charsets.UTF_8)
    }
}
