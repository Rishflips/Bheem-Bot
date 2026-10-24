package app.bheem.models.host

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking

class ModelFileWriterTest {
    private val dir: File = Files.createTempDirectory("writer").toFile()
    private val content = ByteArray(10_000) { (it * 31 % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val pinned = ModelFile("m.bin", sha, content.size.toLong())
    private val target = File(dir, "m.bin")

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun writer(file: ModelFile = pinned) = ModelFileWriter(target, file)

    @Test
    fun aCompleteVerifiedFileAppearsOnlyAfterTheChecksPass() = runBlocking<Unit> {
        writer().use { w ->
            assertEquals(0L, w.resume())
            w.append(content, 4_000)
            assertFalse(target.exists())
            w.append(content.copyOfRange(4_000, content.size), content.size - 4_000)
            w.finish()
        }
        assertTrue(content.contentEquals(target.readBytes()))
        assertFalse(File(target.path + ".part").exists())
    }

    @Test
    fun aWrongHashDeletesThePartAndLeavesNoTarget() = runBlocking<Unit> {
        val bad = content.copyOf().also { it[5] = (it[5] + 1).toByte() }
        writer().use { w ->
            w.resume()
            w.append(bad, bad.size)
            assertFailsWith<ModelCorrupt> { w.finish() }
        }
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }

    @Test
    fun aShortFileIsRefusedAtTheEnd() = runBlocking<Unit> {
        writer().use { w ->
            w.resume()
            w.append(content, 9_000)
            assertFailsWith<ModelCorrupt> { w.finish() }
        }
        assertFalse(target.exists())
    }

    @Test
    fun moreBytesThanThePinnedSizeAreRefusedAtOnce() = runBlocking<Unit> {
        writer().use { w ->
            w.resume()
            w.append(content, content.size)
            assertFailsWith<ModelCorrupt> { w.append(byteArrayOf(1), 1) }
        }
    }

    @Test
    fun aPartFromAnEarlierRunIsHashedAndContinued() = runBlocking<Unit> {
        writer().use { w ->
            w.resume()
            w.append(content, 6_000)
        }
        assertEquals(6_000L, File(target.path + ".part").length())
        writer().use { w ->
            assertEquals(6_000L, w.resume())
            w.append(content.copyOfRange(6_000, content.size), content.size - 6_000)
            w.finish()
        }
        assertTrue(content.contentEquals(target.readBytes()))
    }

    @Test
    fun aPartLongerThanThePinnedSizeIsThrownAway() = runBlocking<Unit> {
        File(target.path + ".part").writeBytes(ByteArray(content.size + 5))
        writer().use { w -> assertEquals(0L, w.resume()) }
        assertFalse(File(target.path + ".part").exists() && File(target.path + ".part").length() > 0)
    }

    @Test
    fun finishingReplacesAnOlderFile() = runBlocking<Unit> {
        target.writeText("old")
        writer().use { w ->
            w.resume()
            w.append(content, content.size)
            w.finish()
        }
        assertTrue(content.contentEquals(target.readBytes()))
    }

    @Test
    fun importingCopiesAndVerifiesAFileFromElsewhere() = runBlocking<Unit> {
        val source = File(dir, "elsewhere.bin").also { it.writeBytes(content) }
        val seen = mutableListOf<Long>()
        writer().use { w -> ModelImport.copy(source, w) { seen += it } }
        assertTrue(content.contentEquals(target.readBytes()))
        assertEquals(content.size.toLong(), seen.last())
        assertTrue(source.exists())
    }

    @Test
    fun importingAWrongFileLeavesNothingBehind() = runBlocking<Unit> {
        val source = File(dir, "elsewhere.bin").also { it.writeBytes(content.copyOf().also { b -> b[0] = 9 }) }
        writer().use { w -> assertFailsWith<ModelCorrupt> { ModelImport.copy(source, w) {} } }
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }

    @Test
    fun importingStopsWhenCancelled() {
        val source = File(dir, "elsewhere.bin").also { it.writeBytes(ByteArray(2_000_000) { 1 }) }
        val big = ModelFile("m.bin", "0".repeat(64), 2_000_000)
        assertFailsWith<CancellationException> {
            runBlocking {
                ModelFileWriter(target, big).use { w ->
                    ModelImport.copy(source, w) { throw CancellationException("stop") }
                }
            }
        }
    }

    @Test
    fun bytesAddedToThePartBehindTheWritersBackAreCaughtAtTheEnd() = runBlocking<Unit> {
        writer().use { w ->
            w.resume()
            w.append(content, content.size - 100)
            File(target.path + ".part").appendBytes(ByteArray(64))
            w.append(content.copyOfRange(content.size - 100, content.size), 100)
            assertFailsWith<ModelCorrupt> { w.finish() }
        }
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }

    @Test
    fun aWriterWithItsOwnSuffixLeavesTheDownloadPartAlone() = runBlocking<Unit> {
        File(target.path + ".part").also { it.parentFile.mkdirs() }.writeBytes(ByteArray(7))
        ModelFileWriter(target, pinned, partSuffix = ".import").use { w ->
            w.reset()
            w.append(content, content.size)
            w.finish()
        }
        assertTrue(content.contentEquals(target.readBytes()))
        assertEquals(7L, File(target.path + ".part").length())
    }
}
