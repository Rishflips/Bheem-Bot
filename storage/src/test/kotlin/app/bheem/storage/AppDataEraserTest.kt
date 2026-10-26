package app.bheem.storage

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppDataEraserTest {
    private val base: File = Files.createTempDirectory("eraser").toFile()
    private val files = File(base, "files")
    private val noBackup = File(base, "no_backup")
    private val cache = File(base, "cache")
    private val prefs = File(base, "shared_prefs")

    @AfterTest
    fun cleanUp() {
        base.deleteRecursively()
    }

    private fun write(dir: File, path: String, text: String = "x"): File = File(dir, path).also {
        it.parentFile.mkdirs()
        it.writeText(text)
    }

    private fun populate(): File {
        write(files, "notes.txt")
        write(files, "results/run.json")
        write(noBackup, "model-host.json")
        write(noBackup, "key.bin")
        write(cache, "litert/graph.bin")
        write(prefs, "bheem.xml")
        return write(files, "models/gemma.litertlm", "big")
    }

    private fun left(): List<String> = base.walkTopDown().filter { it.isFile }.map { it.relativeTo(base).invariantSeparatorsPath }.sorted().toList()

    @Test
    fun everythingUnderTheRootsIsDeletedButTheRootsStay() {
        populate()
        val failed = AppDataEraser(listOf(files, noBackup, cache, prefs)).erase()
        assertTrue(failed.isEmpty())
        assertEquals(emptyList(), left())
        assertTrue(files.isDirectory && noBackup.isDirectory)
    }

    @Test
    fun theModelFileCanBeKeptWhileEverythingElseGoes() {
        val model = populate()
        AppDataEraser(listOf(files, noBackup, cache, prefs)).erase(keep = { it == model })
        assertEquals(listOf("files/models/gemma.litertlm"), left())
    }

    @Test
    fun aRootThatDoesNotExistIsFine() {
        val failed = AppDataEraser(listOf(File(base, "missing"))).erase()
        assertTrue(failed.isEmpty())
    }

    @Test
    fun aFileThatCannotBeDeletedIsReportedNotHidden() {
        write(files, "a.txt")
        val stuck = write(files, "locked/b.txt")
        val eraser = AppDataEraser(listOf(files)) { f -> f != stuck && f.delete() }
        val failed = eraser.erase()
        assertEquals(listOf(stuck), failed)
        assertEquals(listOf("files/locked/b.txt"), left())
    }
}
