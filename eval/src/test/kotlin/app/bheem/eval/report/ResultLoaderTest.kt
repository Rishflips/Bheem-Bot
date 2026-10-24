package app.bheem.eval.report

import app.bheem.eval.data.Jsonl
import app.bheem.eval.run.CaseResult
import app.bheem.eval.run.DeviceInfo
import app.bheem.eval.run.RunResult
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ResultLoaderTest {
    private val run = RunResult(
        "proposer-v1", "m", "CPU", "OFF", DeviceInfo("phone", "M", "S", 6144, 35), 5000, null, emptyList(),
        listOf(CaseResult("c1", "command", "", true, true, true, null, 10, 100, 5, 20, null)),
        complete = true,
    )

    @Test
    fun aTruncatedFileIsReportedAndTheGoodFileStillLoads() {
        val dir = createTempDirectory()
        val good = dir.resolve("good.json")
        Files.writeString(good, Jsonl.json.encodeToString(RunResult.serializer(), run))
        val bad = dir.resolve("bad.json")
        Files.writeString(bad, Jsonl.json.encodeToString(RunResult.serializer(), run).take(40))

        val loaded = ResultLoader.load(listOf(good, bad))

        assertEquals(1, loaded.summaries.size)
        assertEquals("m", loaded.summaries.single().modelId)
        assertEquals(1, loaded.unreadable.size)
        assertTrue(loaded.unreadable.single().startsWith("bad.json"))
    }

    @Test
    fun noFilesYieldsNothing() {
        val loaded = ResultLoader.load(emptyList())
        assertTrue(loaded.summaries.isEmpty() && loaded.unreadable.isEmpty())
    }
}
