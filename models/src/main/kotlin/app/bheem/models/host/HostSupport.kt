package app.bheem.models.host

import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class Capability { Full, CpuOnly, RulesOnly }

object Governor {
    fun capability(batterySaver: Boolean, thermalStatus: Int, charging: Boolean): Capability = when {
        thermalStatus >= 3 -> Capability.RulesOnly
        batterySaver && !charging -> Capability.RulesOnly
        thermalStatus == 2 || batterySaver -> Capability.CpuOnly
        else -> Capability.Full
    }

    fun shouldUnload(thermalStatus: Int): Boolean = thermalStatus >= 4
}

interface FileFacts {
    fun exists(path: String): Boolean

    fun size(path: String): Long

    fun lastModified(path: String): Long
}

object RealFileFacts : FileFacts {
    override fun exists(path: String) = File(path).isFile

    override fun size(path: String) = File(path).length()

    override fun lastModified(path: String) = File(path).lastModified()
}

interface FileVerifier {
    suspend fun sha256(path: String): String
}

object Sha256FileVerifier : FileVerifier {
    override suspend fun sha256(path: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        File(path).inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                coroutineContext.ensureActive()
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private const val BUFFER = 1 shl 20
}

interface LoadHooks {
    fun loadStarted()

    fun loadFinished()

    object None : LoadHooks {
        override fun loadStarted() = Unit

        override fun loadFinished() = Unit
    }
}

@Serializable
data class VerifiedFile(val path: String, val size: Long, val modified: Long, val sha256: String, val appVersion: Int = 0)

interface HostStateStore {
    fun verified(): VerifiedFile?

    fun saveVerified(file: VerifiedFile)

    fun gpuFailedVersion(): String?

    fun saveGpuFailed(version: String?)

    fun lastLoadMillis(): Long?

    fun saveLastLoadMillis(millis: Long)
}

class InMemoryHostStateStore : HostStateStore {
    private var verified: VerifiedFile? = null
    private var gpuFailed: String? = null
    private var lastLoad: Long? = null

    override fun verified() = verified

    override fun saveVerified(file: VerifiedFile) {
        verified = file
    }

    override fun gpuFailedVersion() = gpuFailed

    override fun saveGpuFailed(version: String?) {
        gpuFailed = version
    }

    override fun lastLoadMillis() = lastLoad

    override fun saveLastLoadMillis(millis: Long) {
        lastLoad = millis
    }
}

class JsonFileHostStateStore(private val file: File) : HostStateStore {
    @Serializable
    private data class Saved(val verified: VerifiedFile? = null, val gpuFailedVersion: String? = null, val lastLoadMillis: Long? = null)

    private val json = Json { ignoreUnknownKeys = true }

    @Synchronized
    private fun read(): Saved = try {
        if (file.exists()) json.decodeFromString(Saved.serializer(), file.readText()) else Saved()
    } catch (_: Exception) {
        Saved()
    }

    @Synchronized
    private fun write(saved: Saved) {
        val temp = File(file.path + ".part")
        temp.writeText(json.encodeToString(Saved.serializer(), saved))
        check(temp.renameTo(file) || (file.delete() && temp.renameTo(file))) { "could not save the model state" }
    }

    override fun verified() = read().verified

    override fun saveVerified(file: VerifiedFile) = write(read().copy(verified = file))

    override fun gpuFailedVersion() = read().gpuFailedVersion

    override fun saveGpuFailed(version: String?) = write(read().copy(gpuFailedVersion = version))

    override fun lastLoadMillis() = read().lastLoadMillis

    override fun saveLastLoadMillis(millis: Long) = write(read().copy(lastLoadMillis = millis))
}
