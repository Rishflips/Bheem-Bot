package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import kotlin.random.Random
import kotlinx.serialization.Serializable

enum class RecordingCondition(val code: String, val instruction: String) {
    QUIET("q", "Quiet room. Hold the phone about 20 cm from your mouth."),
    NOISY("n", "Background noise on (traffic or kitchen sounds). Hold the phone about 20 cm from your mouth."),
    FAR("f", "Quiet room. Put the phone on a table about 1 metre away."),
}

@Serializable
data class Take(
    val id: String,
    val speaker: String,
    val commandId: String,
    val text: String,
    val condition: RecordingCondition,
    val position: Int,
)

data class RecordingPlan(val speakers: List<String>, val readersPerCommand: Int, val takes: List<Take>) {
    fun forSpeaker(speaker: String): List<Take> = takes.filter { it.speaker == speaker }.sortedBy { it.position }

    fun summary(): String = buildString {
        appendLine("${takes.size} takes, ${speakers.size} speakers, $readersPerCommand readers per command")
        appendLine("Takes per speaker: " + speakers.joinToString(", ") { s -> "$s ${takes.count { it.speaker == s }}" })
        appendLine(
            "Takes per condition: " + RecordingCondition.entries.joinToString(", ") { c ->
                "${c.name.lowercase()} ${takes.count { it.condition == c }}"
            },
        )
    }
}

object RecordingPlanner {
    fun plan(commands: List<CommandCase>, speakers: Int = 6, readersPerCommand: Int = 2, seed: Long = 2026): RecordingPlan {
        require(commands.isNotEmpty()) { "no commands to record" }
        require(readersPerCommand >= 1) { "at least one reader per command" }
        require(speakers >= readersPerCommand) { "$readersPerCommand readers per command needs at least that many speakers" }
        val conditions = RecordingCondition.entries
        val ordered = commands.sortedWith(compareBy({ it.expected.action }, { it.id }))
        val perSpeaker = List(speakers) { mutableListOf<Pair<CommandCase, RecordingCondition>>() }
        var slot = 0
        for (command in ordered) {
            repeat(readersPerCommand) {
                val speaker = slot % speakers
                val turn = perSpeaker[speaker].size
                perSpeaker[speaker] += command to conditions[(turn + speaker) % conditions.size]
                slot++
            }
        }
        val ids = List(speakers) { "S%02d".format(it + 1) }
        return RecordingPlan(ids, readersPerCommand, perSpeaker.withIndex().flatMap { (index, items) -> sheet(ids[index], index, items, seed) })
    }

    private fun sheet(speaker: String, index: Int, items: List<Pair<CommandCase, RecordingCondition>>, seed: Long): List<Take> {
        val conditions = RecordingCondition.entries
        var position = 0
        return conditions.indices.flatMap { block ->
            val condition = conditions[(block + index) % conditions.size]
            items.filter { it.second == condition }
                .shuffled(Random(seed * 1000 + index * 10 + block))
                .map { (command, _) ->
                    Take("$speaker-${command.id}-${condition.code}", speaker, command.id, command.text, condition, ++position)
                }
        }
    }
}
