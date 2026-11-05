package app.bheem.models.host

import app.bheem.models.Capability
import app.bheem.models.ThinkingMode
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

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
    val isDefault: Boolean = false,
)

data class ModelChoice(val model: RegisteredModel, val fellBack: Boolean = false)

object ModelSelection {
    /**
     * The named model if one is given (it must exist), otherwise the model marked default for the role, or the only one of that role.
     * It never guesses: two models of a role with no default is an error, so adding a candidate cannot change what the app ships.
     */
    fun pick(models: List<RegisteredModel>, role: String, preferredId: String?): RegisteredModel {
        val ofRole = models.filter { it.role == role }
        require(ofRole.isNotEmpty()) { "the registry has no model for the role $role" }
        if (preferredId != null) return ofRole.firstOrNull { it.id == preferredId } ?: throw IllegalArgumentException("the registry has no $role model named $preferredId")
        val marked = ofRole.filter { it.isDefault }
        require(marked.size <= 1) { "more than one $role model is marked default" }
        return marked.singleOrNull() ?: ofRole.singleOrNull() ?: throw IllegalArgumentException("the registry has several $role models and none is marked default")
    }

    /** A stored choice that the registry does not know is ignored and reported, so the caller can clear it instead of failing to start. */
    fun choose(models: List<RegisteredModel>, role: String, preferredId: String?): ModelChoice {
        val found = preferredId?.let { id -> models.firstOrNull { it.role == role && it.id == id } }
        if (found != null) return ModelChoice(found)
        return ModelChoice(pick(models, role, null), fellBack = preferredId != null)
    }

    fun other(models: List<RegisteredModel>, role: String, selectedId: String): RegisteredModel? =
        models.filter { it.role == role && it.id != selectedId }.singleOrNull()
}

object ModelFiles {
    fun inModelsFolder(root: File, model: RegisteredModel): File = File(File(root, "models"), model.files.first().name)

    fun otherFile(root: File, models: List<RegisteredModel>, role: String, selectedId: String): File? =
        ModelSelection.other(models, role, selectedId)?.let { inModelsFolder(root, it) }

    fun removeOther(root: File, models: List<RegisteredModel>, role: String, selectedId: String): Boolean {
        val file = otherFile(root, models, role, selectedId) ?: return false
        return !file.exists() || file.delete()
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
    private data class ModelDto(val id: String, val role: String, val runtime: String, val license: String, val minRamGb: Int, val files: List<FileDto>, val profile: JsonObject? = null, val default: Boolean = false)

    @Serializable
    private data class RegistryDto(val models: List<ModelDto>)

    private val roles = setOf("llm", "stt")
    private val profileKeys = setOf("answerFormat", "promptId", "nowSuffix", "capabilities", "thinking")

    fun parse(text: String): List<RegisteredModel> {
        val dto = json.decodeFromString(RegistryDto.serializer(), text)
        require(dto.models.map { it.id }.toSet().size == dto.models.size) { "the registry lists a model id twice" }
        return dto.models.map { m ->
            require(m.role in roles) { "model ${m.id} has an unknown role ${m.role}" }
            require(m.role != "llm" || m.profile != null) { "model ${m.id} is an llm and must carry its own profile" }
            require(m.files.isNotEmpty()) { "model ${m.id} lists no files" }
            for (f in m.files) {
                require(hex.matches(f.sha256)) { "model ${m.id} file ${f.name} has no valid sha256" }
                require(f.bytes > 0) { "model ${m.id} file ${f.name} has no size" }
                require(f.url == null || f.url.startsWith("https://")) { "model ${m.id} file ${f.name} has a download url that is not https" }
            }
            RegisteredModel(m.id, m.role, m.runtime, m.license, m.minRamGb, m.files.map { ModelFile(it.name, it.sha256, it.bytes, it.url) }, profile(m.id, m.profile), m.default)
        }
    }

    private fun profile(id: String, obj: JsonObject?): ModelProfile {
        if (obj != null) {
            val unknown = obj.keys - profileKeys
            require(unknown.isEmpty()) { "model $id has unknown profile keys $unknown; known: ${profileKeys.sorted()}" }
        }
        val p = if (obj == null) ProfileDto() else json.decodeFromJsonElement(ProfileDto.serializer(), obj)
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
