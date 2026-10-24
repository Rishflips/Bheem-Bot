package app.bheem.downloader

import app.bheem.models.host.ModelCorrupt
import app.bheem.models.host.ModelFile
import app.bheem.models.host.ModelFileWriter
import java.io.IOException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

enum class PauseReason { Offline, NoWifi, BatterySaver }

object DownloadPolicy {
    fun check(connected: Boolean, unmetered: Boolean, cellularAllowed: Boolean, batterySaver: Boolean, charging: Boolean): PauseReason? = when {
        !connected -> PauseReason.Offline
        !unmetered && !cellularAllowed -> PauseReason.NoWifi
        batterySaver && !charging -> PauseReason.BatterySaver
        else -> null
    }
}

class DownloadPaused(val reason: PauseReason) : Exception("the download is paused: $reason")

class NotEnoughSpace(val needed: Long, val free: Long) : Exception("not enough free space: need $needed bytes, have $free")

open class DownloadFailed(message: String) : Exception(message)

class ChecksumFailed(message: String) : DownloadFailed("the download failed its checksum twice ($message)")

private class Retryable(message: String) : IOException(message)

class ResumableDownloader(
    private val transport: HttpTransport,
    private val freeSpace: () -> Long,
    private val guard: () -> PauseReason?,
    private val maxAttempts: Int = 4,
    private val spaceMargin: Long = 512L shl 20,
    private val guardEveryBytes: Int = 1 shl 20,
    private val backoff: suspend (failures: Int) -> Unit = { delay(2_000L * it) },
) {
    suspend fun download(url: String, pinned: ModelFile, writer: ModelFileWriter, onProgress: (done: Long, total: Long) -> Unit) {
        withContext(Dispatchers.IO) {
            writer.resume()
            var failures = 0
            var restartedAfterCorrupt = false
            while (true) {
                guard()?.let { throw DownloadPaused(it) }
                val needed = pinned.bytes - writer.written + spaceMargin
                val free = freeSpace()
                if (free < needed) throw NotEnoughSpace(needed, free)
                try {
                    transfer(url, pinned, writer, onProgress)
                    if (writer.written < pinned.bytes) throw Retryable("the connection ended after ${writer.written} of ${pinned.bytes} bytes")
                    writer.finish()
                    return@withContext
                } catch (e: ModelCorrupt) {
                    if (restartedAfterCorrupt) throw ChecksumFailed(e.message.orEmpty())
                    restartedAfterCorrupt = true
                    writer.reset()
                } catch (e: IOException) {
                    failures++
                    if (failures >= maxAttempts) throw DownloadFailed("the download kept failing (${e.message})")
                    backoff(failures)
                }
            }
        }
    }

    private suspend fun transfer(url: String, pinned: ModelFile, writer: ModelFileWriter, onProgress: (Long, Long) -> Unit) {
        transport.open(url, writer.written).use { reply ->
            when (reply.status) {
                206 -> checkRange(reply, writer.written, pinned.bytes)
                200 -> {
                    if (writer.written > 0) writer.reset()
                    reply.header("Content-Length")?.toLongOrNull()?.let {
                        if (it != pinned.bytes) throw DownloadFailed("the server file is $it bytes, not the pinned ${pinned.bytes}")
                    }
                }
                416 -> {
                    if (writer.written == pinned.bytes) return
                    writer.reset()
                    throw Retryable("the server refused the range")
                }
                408, 429, in 500..599 -> throw Retryable("the server answered ${reply.status}")
                else -> throw DownloadFailed("the server answered ${reply.status}")
            }
            val buffer = ByteArray(CHUNK)
            var sinceGuard = 0
            while (true) {
                coroutineContext.ensureActive()
                val read = reply.body.read(buffer)
                if (read < 0) break
                writer.append(buffer, read)
                onProgress(writer.written, pinned.bytes)
                sinceGuard += read
                if (sinceGuard >= guardEveryBytes) {
                    sinceGuard = 0
                    guard()?.let { throw DownloadPaused(it) }
                }
            }
        }
    }

    private fun checkRange(reply: HttpReply, held: Long, total: Long) {
        val match = contentRange.matchEntire(reply.header("Content-Range").orEmpty().trim())
            ?: throw DownloadFailed("the server answered a range without a Content-Range header")
        val start = match.groupValues[1].toLong()
        val size = match.groupValues[3].toLongOrNull()
        if (start != held) throw DownloadFailed("the server resumed at byte $start instead of $held")
        if (size != null && size != total) throw DownloadFailed("the server file is $size bytes, not the pinned $total")
    }

    private companion object {
        const val CHUNK = 64 * 1024
        val contentRange = Regex("""bytes (\d+)-(\d+)/(\d+|\*)""")
    }
}
