package app.bheem.models.host

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class ModelCorrupt(message: String) : Exception(message)

class ModelFileWriter(private val target: File, private val pinned: ModelFile) : AutoCloseable {
    private val part = File(target.path + ".part")
    private var digest = MessageDigest.getInstance("SHA-256")
    private var out: FileOutputStream? = null

    var written = 0L
        private set

    suspend fun resume(): Long = withContext(Dispatchers.IO) {
        close()
        restart()
        target.parentFile?.mkdirs()
        if (part.isFile && part.length() > pinned.bytes) part.delete()
        if (part.isFile) {
            part.inputStream().use { input ->
                val buffer = ByteArray(BUFFER)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    written += read
                }
            }
        }
        out = FileOutputStream(part, true)
        written
    }

    fun reset() {
        close()
        restart()
        target.parentFile?.mkdirs()
        out = FileOutputStream(part, false)
    }

    fun append(buffer: ByteArray, length: Int) {
        val stream = checkNotNull(out) { "resume() or reset() must come first" }
        if (written + length > pinned.bytes) {
            discard()
            throw ModelCorrupt("the download is larger than the pinned size of ${pinned.bytes} bytes")
        }
        stream.write(buffer, 0, length)
        digest.update(buffer, 0, length)
        written += length
    }

    fun finish() {
        val stream = checkNotNull(out) { "resume() or reset() must come first" }
        stream.flush()
        stream.fd.sync()
        close()
        if (written != pinned.bytes) {
            discard()
            throw ModelCorrupt("the file has $written bytes, expected ${pinned.bytes}")
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it) }
        if (hash != pinned.sha256) {
            discard()
            throw ModelCorrupt("the file failed its checksum")
        }
        try {
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override fun close() {
        out?.close()
        out = null
    }

    private fun discard() {
        close()
        part.delete()
        restart()
    }

    private fun restart() {
        digest = MessageDigest.getInstance("SHA-256")
        written = 0
    }

    private companion object {
        const val BUFFER = 1 shl 20
    }
}

object ModelImport {
    suspend fun copy(source: File, writer: ModelFileWriter, onProgress: (Long) -> Unit) = withContext(Dispatchers.IO) {
        writer.reset()
        source.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                writer.append(buffer, read)
                onProgress(writer.written)
            }
        }
        writer.finish()
    }
}
