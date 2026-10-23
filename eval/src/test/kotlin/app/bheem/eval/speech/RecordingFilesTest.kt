package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.data.Jsonl
import app.bheem.eval.gen.CommandGenerator
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingFilesTest {
    private val english = CommandGenerator.generate().filter { it.lang == "en" }

    @Test
    fun writesTheManifestOneSheetPerSpeakerAndTheReadme() {
        val plan = RecordingPlanner.plan(english)
        val dir = createTempDirectory("recording")
        RecordingFiles.write(plan, dir)
        assertEquals(plan.takes, Jsonl.read<Take>(dir.resolve("manifest.jsonl")))
        for (s in plan.speakers) {
            assertEquals(RecordingSheets.speakerSheet(plan, s), Files.readString(dir.resolve("sheet-$s.md"), Charsets.UTF_8))
        }
        assertEquals(RecordingSheets.readme(plan), Files.readString(dir.resolve("README.md"), Charsets.UTF_8))
    }

    @Test
    fun onlyEnglishCommandsAreRead() {
        fun case(id: String, lang: String) = CommandCase(id, lang, "text $id", ExpectedAction("NoAction"))
        val file = createTempDirectory("commands").resolve("commands.jsonl")
        Jsonl.write(file, listOf(case("a", "en"), case("b", "hi-latn"), case("c", "en"), case("d", "hi-deva")))
        assertEquals(listOf("a", "c"), RecordingFiles.englishCommands(file).map { it.id })
    }
}
