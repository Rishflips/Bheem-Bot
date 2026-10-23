package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.data.Jsonl
import app.bheem.eval.run.DeviceInfo
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpeechReportFilesTest {
    @Test
    fun readsTheResultAndTheCommandsAndWritesTheMarkdownReport() {
        val dir = createTempDirectory("speech-report")
        val commands = listOf(CommandCase("cmd-233", "en", "pay Ravi 840 rupees", ExpectedAction("OpenUpiPayment", mapOf("payee" to "Ravi", "amount" to "840"))))
        val result = SpeechRunResult(
            listOf("gemma-audio-file"), DeviceInfo("midrange-a", "Moto", "MT6878", 7393, 36), 5090, null, emptyList(),
            listOf(SpeechCaseResult("t1", "S01", "cmd-233", "quiet", "gemma-audio-file", 2000, "pay Ravi 840 rupees", "pay Ravi 840 rupees", 400, 1000, null)),
            true,
        )
        Files.writeString(dir.resolve("result.json"), Jsonl.json.encodeToString(SpeechRunResult.serializer(), result), Charsets.UTF_8)
        Jsonl.write(dir.resolve("commands.jsonl"), commands)
        SpeechReportFiles.write(dir.resolve("result.json"), dir.resolve("commands.jsonl"), dir.resolve("report.md"))
        val report = Files.readString(dir.resolve("report.md"), Charsets.UTF_8)
        assertEquals(SpeechReportBuilder.markdown(result, commands.associateBy { it.id }), report)
        assertTrue("| gemma-audio-file | 1 | 0 | 0.0% |" in report)
    }
}
