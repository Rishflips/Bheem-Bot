package app.bheem.models.host

import app.bheem.models.Capability
import app.bheem.models.ThinkingMode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class ModelFile(val name: String, val sha256: String, val bytes: Long, val url: String? = null)

/** How the harness talks to one model, as data: the prompt, the answer format and what the model can do. */
data class ModelProfile(
    val answerFormat: String = "line",
    val promptId: String = "planner-line-v3.8",
    val nowSuffix: String = "",
    val capabilities: Set<Capability> = setOf(Capability.TEXT),
    val thinking: ThinkingMode = ThinkingMode.OFF,
)

data class RegisteredModel(
    val id: String,
    val role: String,
    val runtime: String,
    val license: String,
    val minRamGb: Int,
    val files: List<ModelFile>,
    val profile: ModelProfile = ModelProfile(),
)

object ModelSelection {
    /** The preferred model if it exists for the role, otherwise the first model of that role. */
    fun pick(models: List<RegisteredModel>, role: String, preferredId: String?): RegisteredModel {
        val ofRole = models.filter { it.role == role }
        require(ofRole.isNotEmpty()) { "the registry has no model for the role $role" }
        return ofRole.firstOrNull { it.id == preferredId } ?: ofRole.first()
    }
}

object ModelRegistry {
    private val hex = Regex("[0-9a-f]{64}")
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class FileDto(val name: String, val sha256: String, val bytes: Long, val url: String? = null)

    @Serializable
    private data class ProfileDto(
        val answerFormat: String = "line",
        val promptId: String = "planner-line-v3.8",
        val nowSuffix: String = "",
        val capabilities: List<String> = listOf("TEXT"),
        val thinking: String = "OFF",
    )

    @Serializable
    private data class ModelDto(val id: String, val role: String, val runtime: String, val license: String, val minRamGb: Int, val files: List<FileDto>, val profile: ProfileDto = ProfileDto())

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
            RegisteredModel(m.id, m.role, m.runtime, m.license, m.minRamGb, m.files.map { ModelFile(it.name, it.sha256, it.bytes, it.url) }, profile(m.id, m.profile))
        }

    private fun profile(id: String, p: ProfileDto): ModelProfile {
        require(p.answerFormat.isNotBlank()) { "model $id has an empty answerFormat" }
        require(p.promptId.isNotBlank()) { "model $id has an empty promptId" }
        val capabilities = p.capabilities.map { name ->
            Capability.entries.firstOrNull { it.name == name } ?: throw IllegalArgumentException("model $id lists an unknown capability $name")
        }.toSet()
        require(Capability.TEXT in capabilities) { "model $id must list the TEXT capability" }
        val thinking = ThinkingMode.entries.firstOrNull { it.name == p.thinking } ?: throw IllegalArgumentException("model $id has an unknown thinking mode ${p.thinking}")
        return ModelProfile(p.answerFormat, p.promptId, p.nowSuffix, capabilities, thinking)
    }
}
