package app.bheem.downloader

import app.bheem.models.host.ModelFile
import app.bheem.models.host.ModelFileWriter
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class DownloaderTest {
    private val dir: File = Files.createTempDirectory("dl").toFile()
    private val content = ByteArray(600_000) { (it * 17 % 253).toByte() }
    private val sha = MessageDigest.getInstance("SHA-256").digest(content).joinToString("") { "%02x".format(it) }
    private val pinned = ModelFile("m.bin", sha, content.size.toLong(), "https://example.org/m.bin")
    private val target = File(dir, "models/m.bin")
    private val part = File(dir, "models/m.bin.part")
    private val server = TestServer(content)

    @AfterTest
    fun cleanUp() {
        server.close()
        dir.deleteRecursively()
    }

    private fun downloader(
        free: Long = Long.MAX_VALUE,
        guard: () -> PauseReason? = { null },
        attempts: Int = 4,
    ) = ResumableDownloader(UrlTransport(allowHttp = true), freeSpace = { free }, guard = guard, maxAttempts = attempts, spaceMargin = 1_000, guardEveryBytes = 64 * 1024, backoff = {})

    private fun run(
        downloader: ResumableDownloader = downloader(),
        url: String = server.base + "/model",
        progress: (Long, Long) -> Unit = { _, _ -> },
    ) = runBlocking { ModelFileWriter(target, pinned).use { downloader.download(url, pinned, it, progress) } }

    private fun seedPart(bytes: Int) {
        part.parentFile.mkdirs()
        part.writeBytes(content.copyOf(bytes))
    }

    private fun assertDone() {
        assertTrue(content.contentEquals(target.readBytes()))
        assertFalse(part.exists())
    }

    @Test
    fun aCleanDownloadIsVerifiedAndRenamedAndReportsProgress() {
        val seen = mutableListOf<Long>()
        run(progress = { done, total ->
            seen += done
            assertEquals(content.size.toLong(), total)
        })
        assertDone()
        assertEquals(content.size.toLong(), seen.last())
        assertEquals(listOf(TestServer.Seen("/model", null)), server.seen.toList())
    }

    @Test
    fun aConnectionCutAtFortyPercentResumesWithARangeRequest() {
        val cut = content.size * 4 / 10
        server.dropFirstResponseAfter = cut
        run()
        assertDone()
        val ranges = server.seen.map { it.range }
        assertNull(ranges.first())
        val resumedFrom = ranges[1]!!.removePrefix("bytes=").removeSuffix("-").toLong()
        assertTrue(resumedFrom in 1..cut.toLong(), "resumed from $resumedFrom")
    }

    @Test
    fun aPausedDownloadContinuesInANewRunFromThePartFile() {
        var checks = 0
        assertFailsWith<DownloadPaused> {
            run(downloader(guard = { if (++checks > 3) PauseReason.NoWifi else null }))
        }
        val held = part.length()
        assertTrue(held > 0 && held < content.size, "held $held")
        assertFalse(target.exists())
        run()
        assertDone()
        assertEquals("bytes=$held-", server.seen.last().range)
    }

    @Test
    fun aRedirectKeepsTheRangeHeaderOnEveryHop() {
        server.redirectStartTo = "/cdn"
        seedPart(100_000)
        run(url = server.base + "/start")
        assertDone()
        assertEquals(listOf(TestServer.Seen("/start", "bytes=100000-"), TestServer.Seen("/cdn", "bytes=100000-")), server.seen.toList())
    }

    @Test
    fun theTraceNamesHostPathRangeAndStatusButNeverTheQuery() {
        server.redirectStartTo = "/cdn?token=secret"
        seedPart(100_000)
        val lines = mutableListOf<String>()
        val traced = ResumableDownloader(UrlTransport(allowHttp = true, trace = { lines += it }), freeSpace = { Long.MAX_VALUE }, guard = { null }, spaceMargin = 1_000, backoff = {})
        run(traced, url = server.base + "/start")
        val host = "127.0.0.1"
        assertEquals(listOf("$host/start range=100000 -> 302", "$host/cdn range=100000 -> 206"), lines)
    }

    @Test
    fun aServerThatIgnoresTheRangeRestartsTheFileFromZero() {
        server.ignoreRange = true
        seedPart(100_000)
        run()
        assertDone()
    }

    @Test
    fun aCorruptedDownloadIsDeletedAndFetchedAgainOnce() {
        server.corruptFirstResponse = true
        run()
        assertDone()
        assertEquals(2, server.seen.size)
        assertNull(server.seen[1].range)
    }

    @Test
    fun aFileThatStaysCorruptEndsInChecksumFailedWithNothingLeftBehind() {
        server.corruptAlways = true
        assertFailsWith<ChecksumFailed> { run() }
        assertEquals(2, server.seen.size)
        assertFalse(target.exists())
        assertFalse(part.exists())
    }

    @Test
    fun tooLittleFreeSpaceStopsBeforeAnyRequest() {
        val error = assertFailsWith<NotEnoughSpace> { run(downloader(free = 1_000)) }
        assertEquals(content.size + 1_000L, error.needed)
        assertTrue(server.seen.isEmpty())
    }

    @Test
    fun theSpaceCheckCountsOnlyWhatIsStillMissing() {
        seedPart(500_000)
        run(downloader(free = 101_000))
        assertDone()
    }

    @Test
    fun aPauseReasonStopsBeforeAnyRequest() {
        val error = assertFailsWith<DownloadPaused> { run(downloader(guard = { PauseReason.BatterySaver })) }
        assertEquals(PauseReason.BatterySaver, error.reason)
        assertTrue(server.seen.isEmpty())
    }

    @Test
    fun aMissingFileFailsAtOnceWithoutRetries() {
        server.statusForAll = 404
        assertFailsWith<DownloadFailed> { run() }
        assertEquals(1, server.seen.size)
    }

    @Test
    fun aBusyServerIsRetriedThenSucceeds() {
        server.failFirst = 2
        run()
        assertDone()
        assertEquals(3, server.seen.size)
    }

    @Test
    fun aServerThatKeepsFailingGivesUpAfterTheAttemptLimit() {
        server.failFirst = 100
        assertFailsWith<DownloadFailed> { run(downloader(attempts = 3)) }
        assertEquals(3, server.seen.size)
    }

    @Test
    fun aFileOfTheWrongAdvertisedSizeIsNotAccepted() {
        server.advertisedLength = content.size + 10L
        assertFailsWith<DownloadFailed> { run() }
        assertFalse(target.exists())
    }

    @Test
    fun cancellationStopsTheTransferWithinAChunkOrTwo() {
        server.slowChunkMillis = 20
        val writer = ModelFileWriter(target, pinned)
        runBlocking {
            val job = launch(Dispatchers.Default) { downloader().download(server.base + "/model", pinned, writer) { _, _ -> } }
            withTimeout(10_000) { while (!part.exists() || part.length() == 0L) delay(5) }
            job.cancel()
            withTimeout(5_000) { job.join() }
            assertTrue(job.isCancelled)
        }
        writer.close()
        assertTrue(part.length() < content.size)
    }

    @Test
    fun theTransportRefusesPlainHttpUnlessTold() {
        assertFailsWith<IOException> { UrlTransport().open(server.base + "/model", 0) }
        assertTrue(server.seen.isEmpty())
    }

    @Test
    fun aRedirectMayNeverLeaveHttps() {
        assertTrue(UrlTransport.redirectAllowed("https://a.example/x", "https://b.example/y", allowHttp = false))
        assertFalse(UrlTransport.redirectAllowed("https://a.example/x", "http://b.example/y", allowHttp = false))
        assertFalse(UrlTransport.redirectAllowed("https://a.example/x", "ftp://b.example/y", allowHttp = false))
        assertFalse(UrlTransport.redirectAllowed("http://a.example/x", "http://b.example/y", allowHttp = false))
    }

    @Test
    fun policyPausesOnCellularBatterySaverAndNoConnection() {
        fun check(connected: Boolean = true, unmetered: Boolean = true, cellularOk: Boolean = false, saver: Boolean = false, charging: Boolean = false) =
            DownloadPolicy.check(connected, unmetered, cellularOk, saver, charging)
        assertNull(check())
        assertEquals(PauseReason.Offline, check(connected = false))
        assertEquals(PauseReason.NoWifi, check(unmetered = false))
        assertNull(check(unmetered = false, cellularOk = true))
        assertEquals(PauseReason.BatterySaver, check(saver = true))
        assertNull(check(saver = true, charging = true))
        assertEquals(PauseReason.Offline, check(connected = false, cellularOk = true))
    }
}
