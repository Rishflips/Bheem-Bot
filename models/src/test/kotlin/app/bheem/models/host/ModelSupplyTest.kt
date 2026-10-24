package app.bheem.models.host

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class ModelSupplyTest {
    private val dir: File = Files.createTempDirectory("supply").toFile()
    private val content = ByteArray(50_000) { (it * 7 % 251).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val pinned = ModelFile("m.bin", sha, content.size.toLong(), "https://example.org/m.bin")
    private val target = File(dir, "private/models/m.bin")
    private val sideload = File(dir, "external/models/m.bin")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        dir.deleteRecursively()
    }

    private class FakeDownloader(val content: ByteArray, var result: FetchResult = FetchResult.Done) : ModelDownloader {
        val calls = mutableListOf<Boolean>()
        var hold: CompletableDeferred<Unit>? = null

        override suspend fun fetch(file: ModelFile, writer: ModelFileWriter, cellularAllowed: Boolean, onProgress: (Long, Long) -> Unit): FetchResult {
            calls += cellularAllowed
            hold?.await()
            if (result == FetchResult.Done) {
                writer.reset()
                writer.append(content, content.size)
                onProgress(content.size.toLong(), content.size.toLong())
                writer.finish()
            }
            return result
        }
    }

    private fun supply(downloader: ModelDownloader? = null, free: Long = Long.MAX_VALUE) =
        ModelSupply(target, sideload, pinned, downloader, scope, freeSpace = { free }, spaceMargin = 1_000)

    private suspend fun ModelSupply.await(match: (SupplyState) -> Boolean) = withTimeout(10_000) { state.first(match) }

    @Test
    fun aModelAlreadyInPrivateStorageNeedsNothing() {
        target.parentFile.mkdirs()
        target.writeBytes(content)
        assertEquals(SupplyState.Present, supply().state.value)
    }

    @Test
    fun withNoFileAndNoDownloaderTheUserIsToldWhereToPutIt() {
        val state = assertIs<SupplyState.Needed>(supply().state.value)
        assertFalse(state.canDownload)
        assertEquals(sideload.path, state.sideloadPath)
    }

    @Test
    fun aSideloadedFileIsMovedIntoPrivateStorageAfterItIsVerified() = runBlocking {
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content)
        val supply = supply()
        supply.await { it == SupplyState.Present }
        assertTrue(content.contentEquals(target.readBytes()))
        assertFalse(sideload.exists())
    }

    @Test
    fun aSideloadedFileWithTheWrongContentIsReportedAndLeftAlone() = runBlocking {
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content.copyOf().also { it[3] = 1 })
        val supply = supply()
        val failed = assertIs<SupplyState.Failed>(supply.await { it is SupplyState.Failed })
        assertTrue(failed.message.contains("checksum"))
        assertTrue(sideload.exists())
        assertFalse(target.exists())
    }

    @Test
    fun anImportThatDoesNotFitStopsBeforeCopying() = runBlocking {
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content)
        val supply = supply(free = 500)
        val failed = assertIs<SupplyState.Failed>(supply.await { it is SupplyState.Failed })
        assertTrue(failed.message.contains("space"))
        assertTrue(sideload.exists())
    }

    @Test
    fun startingADownloadEndsWithThePrivateFileAndPresent() = runBlocking {
        val downloader = FakeDownloader(content)
        val supply = supply(downloader)
        assertTrue(assertIs<SupplyState.Needed>(supply.state.value).canDownload)
        supply.start()
        supply.await { it == SupplyState.Present }
        assertEquals(listOf(false), downloader.calls)
        assertTrue(content.contentEquals(target.readBytes()))
    }

    @Test
    fun aPausedDownloadOffersMobileDataAndRetriesWithIt() = runBlocking {
        val downloader = FakeDownloader(content, FetchResult.Paused("waiting for Wi-Fi", offerCellular = true))
        val supply = supply(downloader)
        supply.start()
        val paused = assertIs<SupplyState.Paused>(supply.await { it is SupplyState.Paused })
        assertTrue(paused.canUseCellular)
        downloader.result = FetchResult.Done
        supply.useCellular()
        supply.await { it == SupplyState.Present }
        assertEquals(listOf(false, true), downloader.calls)
    }

    @Test
    fun aFailedDownloadCanBeStartedAgain() = runBlocking {
        val downloader = FakeDownloader(content, FetchResult.Failed("the server answered 404"))
        val supply = supply(downloader)
        supply.start()
        assertEquals("the server answered 404", assertIs<SupplyState.Failed>(supply.await { it is SupplyState.Failed }).message)
        downloader.result = FetchResult.Done
        supply.start()
        supply.await { it == SupplyState.Present }
    }

    @Test
    fun cancelStopsTheDownloadAndGoesBackToNeeded() = runBlocking {
        val downloader = FakeDownloader(content).also { it.hold = CompletableDeferred() }
        val supply = supply(downloader)
        supply.start()
        supply.await { it is SupplyState.Downloading }
        supply.cancel()
        assertTrue(supply.await { it is SupplyState.Needed } is SupplyState.Needed)
        assertFalse(target.exists())
    }

    @Test
    fun startingTwiceRunsOneDownload() = runBlocking {
        val downloader = FakeDownloader(content).also { it.hold = CompletableDeferred() }
        val supply = supply(downloader)
        supply.start()
        supply.await { it is SupplyState.Downloading }
        supply.start()
        downloader.hold!!.complete(Unit)
        supply.await { it == SupplyState.Present }
        assertEquals(1, downloader.calls.size)
    }

    @Test
    fun startingWithoutADownloaderDoesNothing() {
        val supply = supply()
        supply.start()
        assertNull((supply.state.value as? SupplyState.Downloading))
    }
}
