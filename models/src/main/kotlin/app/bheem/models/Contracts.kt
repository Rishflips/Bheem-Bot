package app.bheem.models

enum class BackendKind { CPU, GPU }

enum class ThinkingMode { OFF, DEFAULT }

data class ModelSpec(
    val id: String,
    val path: String,
    val backend: BackendKind = BackendKind.CPU,
    val thinking: ThinkingMode = ThinkingMode.OFF,
)

enum class AudioForm { FILE, WAV_BYTES, PCM_BYTES }

class AudioClip(val path: String, val form: AudioForm = AudioForm.FILE, val beforeText: Boolean = true, val bytes: ByteArray? = null)

data class GenerationRequest(
    val system: String,
    val user: String,
    val maxOutputTokens: Int = 256,
    val temperature: Double = 0.0,
    val audio: AudioClip? = null,
)

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
    suspend fun load(spec: ModelSpec): Long
    suspend fun complete(request: GenerationRequest): Completion
    suspend fun unload()
}
