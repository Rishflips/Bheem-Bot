package app.bheem.models.host

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ModelFile(val name: String, val sha256: String, val bytes: Long, val url: String? = null)

data class RegisteredModel(
    val id: String,
    val role: String,
    val runtime: String,
    val license: String,
    val minRamGb: Int,
    val files: List<ModelFile>,
)

object ModelRegistry {
    private val hex = Regex("[0-9a-f]{64}")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FileDto(val name: String, val sha256: String, val bytes: Long, val url: String? = null)

    @Serializable
    private data class ModelDto(val id: String, val role: String, val runtime: String, val license: String, val minRamGb: Int, val files: List<FileDto>)

    @Serializable
    private data class RegistryDto(val models: List<ModelDto>)

    fun parse(text: String): List<RegisteredModel> =
        json.decodeFromString(RegistryDto.serializer(), text).models.map { m ->
            require(m.files.isNotEmpty()) { "model ${m.id} lists no files" }
            for (f in m.files) {
                require(hex.matches(f.sha256)) { "model ${m.id} file ${f.name} has no valid sha256" }
                require(f.bytes > 0) { "model ${m.id} file ${f.name} has no size" }
                require(f.url == null || f.url.startsWith("https://")) { "model ${m.id} file ${f.name} has a download url that is not https" }
            }
            RegisteredModel(m.id, m.role, m.runtime, m.license, m.minRamGb, m.files.map { ModelFile(it.name, it.sha256, it.bytes, it.url) })
        }
}
