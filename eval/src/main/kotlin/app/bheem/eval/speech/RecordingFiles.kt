package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Jsonl
import java.nio.file.Files
import java.nio.file.Path

object RecordingFiles {
    fun englishCommands(commandsJsonl: Path): List<CommandCase> =
        Jsonl.read<CommandCase>(commandsJsonl).filter { it.lang == "en" }

    fun write(plan: RecordingPlan, dir: Path) {
        Files.createDirectories(dir)
        Jsonl.write(dir.resolve("manifest.jsonl"), plan.takes)
        for (speaker in plan.speakers) Files.writeString(dir.resolve("sheet-$speaker.md"), RecordingSheets.speakerSheet(plan, speaker), Charsets.UTF_8)
        Files.writeString(dir.resolve("README.md"), RecordingSheets.readme(plan), Charsets.UTF_8)
    }
}
