package app.bheem.eval.io

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AtomicFileTest {
    @Test
    fun writesNewContentAndReplacesOldContent() {
        val dir = createTempDirectory()
        val target = dir.resolve("run.json")
        AtomicFile.writeText(target, "first")
        assertEquals("first", Files.readString(target))
        AtomicFile.writeText(target, "второй: क")
        assertEquals("второй: क", Files.readString(target, Charsets.UTF_8))
    }

    @Test
    fun leavesNoTemporaryFileBehind() {
        val dir = createTempDirectory()
        val target = dir.resolve("run.json")
        AtomicFile.writeText(target, "x")
        assertEquals(listOf("run.json"), Files.list(dir).use { s -> s.map { it.fileName.toString() }.toList() })
        assertFalse(Files.exists(dir.resolve("run.json.tmp")))
    }
}
