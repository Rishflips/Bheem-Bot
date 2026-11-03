package app.bheem.models

enum class BackendKind { CPU, GPU }

enum class ThinkingMode { OFF, DEFAULT }

/** What an engine can do. A request names what it needs and an engine that lacks it is refused before any native call. */
enum class Capability { TEXT, AUDIO_IN }

class UnsupportedCapability(val missing: Capability) : Exception(
    when (missing) {
        Capability.AUDIO_IN -> "this model cannot hear audio"
        Capability.TEXT -> "this model cannot read text"
    },
)

data class ModelSpec(
    val id: String,
    val path: String,
    val backend: BackendKind = BackendKind.CPU,
    val thinking: ThinkingMode = ThinkingMode.OFF,
)

enum class AudioForm { FILE, WAV_BYTES, PCM_BYTES }

class AudioClip(val path: String, val form: AudioForm = AudioForm.FILE, val beforeText: Boolean = true, val bytes: ByteArray? = null) {
    override fun equals(other: Any?): Boolean =
        other is AudioClip && path == other.path && form == other.form && beforeText == other.beforeText && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = ((path.hashCode() * 31 + form.hashCode()) * 31 + beforeText.hashCode()) * 31 + bytes.contentHashCode()

    override fun toString(): String = "AudioClip(path=$path, form=$form, beforeText=$beforeText, bytes=${bytes?.size ?: "none"})"
}

data class GenerationRequest(
    val system: String,
    val user: String,
    val maxOutputTokens: Int = 256,
    val temperature: Double = 0.0,
    val audio: AudioClip? = null,
)

fun GenerationRequest.requires(): Set<Capability> = if (audio != null) setOf(Capability.TEXT, Capability.AUDIO_IN) else setOf(Capability.TEXT)

data class Completion(
    val text: String,
    val firstChunkMs: Long,
    val totalMs: Long,
    val chunkCount: Int,
    val outputTokens: Int? = null,
    val decodeTokensPerSec: Double? = null,
    val contextTokens: Int? = null,
)

interface LlmEngine {
    val capabilities: Set<Capability> get() = setOf(Capability.TEXT)

    suspend fun load(spec: ModelSpec): Long
    suspend fun complete(request: GenerationRequest): Completion
    suspend fun unload()
}

/** Refuses a request that needs something the engine does not declare, then delegates. */
class GuardedEngine(private val inner: LlmEngine) : LlmEngine {
    override val capabilities: Set<Capability> get() = inner.capabilities

    override suspend fun load(spec: ModelSpec): Long = inner.load(spec)

    override suspend fun complete(request: GenerationRequest): Completion {
        request.requires().firstOrNull { it !in inner.capabilities }?.let { throw UnsupportedCapability(it) }
        return inner.complete(request)
    }

    override suspend fun unload() = inner.unload()

    companion object {
        fun of(engine: LlmEngine): LlmEngine = if (engine is GuardedEngine) engine else GuardedEngine(engine)
    }
}
