package app.bheem.eval.data

import kotlinx.serialization.Serializable

@Serializable
data class ExpectedAction(val action: String, val args: Map<String, String> = emptyMap())

@Serializable
data class CommandCase(val id: String, val lang: String, val text: String, val expected: ExpectedAction)

@Serializable
data class NotificationCase(val id: String, val source: String, val text: String, val expected: ExpectedAction)

@Serializable
data class AdversarialCase(val id: String, val wrapper: String, val source: String, val text: String, val lang: String = "en")

data class Dataset(
    val commands: List<CommandCase>,
    val notifications: List<NotificationCase>,
    val adversarial: List<AdversarialCase>,
) {
    fun englishOnly() = copy(
        commands = commands.filter { it.lang == "en" },
        adversarial = adversarial.filter { it.lang == "en" },
    )
}
