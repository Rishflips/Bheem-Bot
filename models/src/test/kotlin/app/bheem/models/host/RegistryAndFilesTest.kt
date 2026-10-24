package app.bheem.models.host

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class RegistryAndFilesTest {
    @Test
    fun theRegistryParsesModelsWithPinnedFiles() {
        val json = """{"models":[{"id":"m","role":"llm","runtime":"litert-lm","license":"Apache-2.0","minRamGb":6,
            "files":[{"name":"m.litertlm","sha256":"${"c".repeat(64)}","bytes":42}]}]}"""
        val model = ModelRegistry.parse(json).single()
        assertEquals("m", model.id)
        assertEquals(42L, model.files.single().bytes)
        assertEquals(6, model.minRamGb)
    }

    @Test
    fun aFileMayCarryAnHttpsDownloadUrlAndNothingElseIsAccepted() {
        fun registry(url: String) = """{"models":[{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,
            "files":[{"name":"f","sha256":"${"c".repeat(64)}","bytes":1,"url":"$url"}]}]}"""
        assertEquals("https://example.org/f", ModelRegistry.parse(registry("https://example.org/f")).single().files.single().url)
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry("http://example.org/f")) }
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(registry("file:///sdcard/f")) }
    }

    @Test
    fun aFileWithoutAUrlHasNone() {
        val json = """{"models":[{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,
            "files":[{"name":"f","sha256":"${"c".repeat(64)}","bytes":1}]}]}"""
        assertNull(ModelRegistry.parse(json).single().files.single().url)
    }

    @Test
    fun aMalformedHashOrAnEmptyFileListIsRejected() {
        val bad = """{"models":[{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,"files":[{"name":"f","sha256":"abc","bytes":1}]}]}"""
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(bad) }
        val none = """{"models":[{"id":"m","role":"llm","runtime":"x","license":"y","minRamGb":1,"files":[]}]}"""
        assertFailsWith<IllegalArgumentException> { ModelRegistry.parse(none) }
    }

    @Test
    fun theShippedRegistryPinsTheGemmaFileUsedForEveryPhase0Run() {
        val asset = File("../app/src/main/assets/models.json")
        val gemma = ModelRegistry.parse(asset.readText()).single { it.id == "gemma-4-e2b" }
        val file = gemma.files.single()
        assertEquals("181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c", file.sha256)
        assertEquals(2588147712L, file.bytes)
        assertEquals("gemma-4-E2B-it.litertlm", file.name)
        assertEquals("https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm", file.url)
        assertTrue(gemma.license.isNotBlank())
    }

    @Test
    fun theStreamingHashMatchesAKnownDigest() = runBlocking {
        val file = Files.createTempFile("hash", ".bin").toFile().apply { writeText("abc") }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256FileVerifier.sha256(file.path))
        file.delete()
    }

    @Test
    fun theStateStoreKeepsWhatWasVerifiedAndWhichBackendFailed() {
        val file = Files.createTempFile("state", ".json").toFile().apply { delete() }
        val store = JsonFileHostStateStore(file)
        assertNull(store.verified())
        store.saveVerified(VerifiedFile("/m", 100, 5, "a".repeat(64), 7))
        store.saveGpuFailed("0.18.1")
        store.saveLastLoadMillis(777)
        val reloaded = JsonFileHostStateStore(file)
        assertEquals(VerifiedFile("/m", 100, 5, "a".repeat(64), 7), reloaded.verified())
        assertEquals("0.18.1", reloaded.gpuFailedVersion())
        assertEquals(777L, reloaded.lastLoadMillis())
        reloaded.saveGpuFailed(null)
        assertNull(JsonFileHostStateStore(file).gpuFailedVersion())
        file.delete()
    }

    @Test
    fun aCorruptStateFileIsTreatedAsEmptyNotAsACrash() {
        val file = Files.createTempFile("state", ".json").toFile().apply { writeText("{not json") }
        val store = JsonFileHostStateStore(file)
        assertNull(store.verified())
        assertNull(store.gpuFailedVersion())
        file.delete()
    }
}
