package app.bheem.eval.conversation

import java.time.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** What one turn is expected to do. Paths: smalltalk, device, recall, action, plan, clarify, chat, refused, confirm, notice. */
@Serializable
data class ExpectedTurn(
    val path: String? = null,
    val action: String? = null,
    val args: Map<String, String> = emptyMap(),
    val must: List<String> = emptyList(),
    @SerialName("must_not") val mustNot: List<String> = emptyList(),
    /** True when a person must judge the model's reply (warmth, consistency); counted, never failed by a script. */
    val judged: Boolean = false,
)

/** [planner] is the line a good planner would return for this turn; without it the planner says NoAction. */
@Serializable
data class ConvTurn(val user: String, val planner: String? = null, val expect: ExpectedTurn = ExpectedTurn())

@Serializable
data class ConvCase(val id: String, val group: String, val fixture: String = "seed", val now: String? = null, val turns: List<ConvTurn>)

@Serializable
data class FixtureNote(val id: String, val text: String, val daysAgo: Int = 0)

@Serializable
data class FixtureReminder(val text: String, val at: String)

/** Invented data only. Never a real person's notes, numbers or reminders. */
@Serializable
data class Fixture(
    val now: String,
    val zone: String = "Asia/Kolkata",
    val notes: List<FixtureNote> = emptyList(),
    val reminders: List<FixtureReminder> = emptyList(),
)

object ConversationFiles {
    val json = Json { ignoreUnknownKeys = true }

    fun parseCases(jsonl: String): List<ConvCase> =
        jsonl.lines().filter { it.isNotBlank() }.map { json.decodeFromString(ConvCase.serializer(), it) }

    fun parseFixture(text: String): Fixture = json.decodeFromString(Fixture.serializer(), text)

    fun at(text: String): LocalDateTime = LocalDateTime.parse(text)
}
