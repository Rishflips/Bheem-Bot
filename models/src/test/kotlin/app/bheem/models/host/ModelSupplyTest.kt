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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext
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
        var stubborn: CompletableDeferred<Unit>? = null
        val queued = ArrayDeque<FetchResult>()

        override suspend fun fetch(file: ModelFile, writer: ModelFileWriter, cellularAllowed: Boolean, onProgress: (Long, Long) -> Unit): FetchResult {
            calls += cellularAllowed
            val result = queued.removeFirstOrNull() ?: result
            hold?.await()
            stubborn?.let { withContext(NonCancellable) { it.await() } }
            if (result == FetchResult.Done) {
                writer.reset()
                writer.append(content, content.size)
                onProgress(content.size.toLong(), content.size.toLong())
                writer.finish()
            }
            return result
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 10_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "timed out" }
            Thread.sleep(5)
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
    fun aSideloadedFileIsMovedIntoPrivateStorageAfterItIsVerified() = runBlocking<Unit> {
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content)
        val supply = supply()
        supply.await { it == SupplyState.Present }
        assertTrue(content.contentEquals(target.readBytes()))
        assertFalse(sideload.exists())
    }

    @Test
    fun aSideloadedFileWithTheWrongContentIsReportedAndLeftAlone() = runBlocking<Unit> {
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content.copyOf().also { it[3] = 1 })
        val supply = supply()
        val failed = assertIs<SupplyState.Failed>(supply.await { it is SupplyState.Failed })
        assertTrue(failed.message.contains("checksum"))
        assertTrue(sideload.exists())
        assertFalse(target.exists())
    }

    @Test
    fun anImportThatDoesNotFitStopsBeforeCopying() = runBlocking<Unit> {
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content)
        val supply = supply(free = 500)
        val failed = assertIs<SupplyState.Failed>(supply.await { it is SupplyState.Failed })
        assertTrue(failed.message.contains("space"))
        assertTrue(sideload.exists())
    }

    @Test
    fun startingADownloadEndsWithThePrivateFileAndPresent() = runBlocking<Unit> {
        val downloader = FakeDownloader(content)
        val supply = supply(downloader)
        assertTrue(assertIs<SupplyState.Needed>(supply.state.value).canDownload)
        supply.start()
        supply.await { it == SupplyState.Present }
        assertEquals(listOf(false), downloader.calls)
        assertTrue(content.contentEquals(target.readBytes()))
    }

    @Test
    fun aPausedDownloadOffersMobileDataAndRetriesWithIt() = runBlocking<Unit> {
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
    fun aFailedDownloadCanBeStartedAgain() = runBlocking<Unit> {
        val downloader = FakeDownloader(content, FetchResult.Failed("the server answered 404"))
        val supply = supply(downloader)
        supply.start()
        assertEquals("the server answered 404", assertIs<SupplyState.Failed>(supply.await { it is SupplyState.Failed }).message)
        downloader.result = FetchResult.Done
        supply.start()
        supply.await { it == SupplyState.Present }
    }

    @Test
    fun cancelStopsTheDownloadAndGoesBackToNeeded() = runBlocking<Unit> {
        val downloader = FakeDownloader(content).also { it.hold = CompletableDeferred() }
        val supply = supply(downloader)
        supply.start()
        supply.await { it is SupplyState.Downloading }
        supply.cancel()
        assertTrue(supply.await { it is SupplyState.Needed } is SupplyState.Needed)
        assertFalse(target.exists())
    }

    @Test
    fun startingTwiceRunsOneDownload() = runBlocking<Unit> {
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

    @Test
    fun aCancelledRunThatIgnoresCancellationFinishesBeforeTheNextOneStarts() = runBlocking<Unit> {
        val downloader = FakeDownloader(content).also {
            it.stubborn = CompletableDeferred()
            it.queued += FetchResult.Failed("old run")
            it.queued += FetchResult.Done
        }
        val supply = supply(downloader)
        supply.start()
        waitUntil { downloader.calls.size == 1 }
        supply.cancel()
        supply.start()
        Thread.sleep(300)
        assertEquals(1, downloader.calls.size)
        downloader.stubborn!!.complete(Unit)
        supply.await { it == SupplyState.Present }
        assertEquals(2, downloader.calls.size)
        assertTrue(content.contentEquals(target.readBytes()))
    }

    @Test
    fun aTargetOfTheWrongSizeIsNotTreatedAsPresent() {
        target.parentFile.mkdirs()
        target.writeBytes(content.copyOf(10))
        val supply = supply(FakeDownloader(content))
        assertIs<SupplyState.Needed>(supply.state.value)
        assertFalse(target.exists())
    }

    @Test
    fun discardingTheModelDeletesItAndAsksAgain() {
        target.parentFile.mkdirs()
        target.writeBytes(content)
        val supply = supply(FakeDownloader(content))
        assertEquals(SupplyState.Present, supply.state.value)
        supply.discard()
        assertFalse(target.exists())
        assertIs<SupplyState.Needed>(supply.state.value)
    }

    @Test
    fun comingBackToTheAppKeepsAPausedOrFailedCard() = runBlocking<Unit> {
        val downloader = FakeDownloader(content, FetchResult.Paused("waiting for Wi-Fi", offerCellular = true))
        val supply = supply(downloader)
        supply.start()
        supply.await { it is SupplyState.Paused }
        supply.refresh()
        assertIs<SupplyState.Paused>(supply.state.value)
        downloader.result = FetchResult.Failed("the server answered 404")
        supply.start()
        supply.await { it is SupplyState.Failed }
        supply.refresh()
        assertEquals(SupplyState.Failed("the server answered 404"), supply.state.value)
    }

    @Test
    fun aSideloadThatFailedItsCheckIsNotCopiedAgainUntilItChanges() = runBlocking<Unit> {
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content.copyOf().also { it[3] = 1 })
        val supply = supply()
        val first = assertIs<SupplyState.Failed>(supply.await { it is SupplyState.Failed })
        val part = File(target.path + ".import")
        assertFalse(part.exists())
        supply.cancel()
        supply.refresh()
        Thread.sleep(200)
        assertIs<SupplyState.Needed>(supply.state.value)
        sideload.writeBytes(content)
        sideload.setLastModified(sideload.lastModified() + 5_000)
        supply.refresh()
        supply.await { it == SupplyState.Present }
        assertTrue(first.message.contains("checksum"))
    }

    @Test
    fun aFailedImportLeavesADownloadsPartFileAlone() = runBlocking<Unit> {
        val partial = File(target.path + ".part").also { it.parentFile.mkdirs() }
        partial.writeBytes(content.copyOf(20_000))
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content.copyOf().also { it[3] = 1 })
        val supply = supply(FakeDownloader(content))
        supply.await { it is SupplyState.Failed }
        assertEquals(20_000L, partial.length())
    }

    @Test
    fun aSuccessfulImportClearsAStaleDownloadPart() = runBlocking<Unit> {
        File(target.path + ".part").also { it.parentFile.mkdirs() }.writeBytes(content.copyOf(20_000))
        sideload.parentFile.mkdirs()
        sideload.writeBytes(content)
        val supply = supply(FakeDownloader(content))
        supply.await { it == SupplyState.Present }
        assertFalse(File(target.path + ".part").exists())
    }
}
