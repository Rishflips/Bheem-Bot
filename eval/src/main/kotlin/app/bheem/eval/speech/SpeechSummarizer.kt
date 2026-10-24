package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import kotlin.math.ceil

fun <T : Comparable<T>> List<T>.nearestRank(p: Double): T? {
    if (isEmpty()) return null
    val sorted = sorted()
    return sorted[(ceil(p * sorted.size).toInt() - 1).coerceIn(0, sorted.size - 1)]
}

data class SpeechSummary(
    val transcriber: String,
    val takes: Int,
    val errors: Int,
    val wer: WerScore,
    val entities: EntityScore,
    val werByCondition: Map<String, WerScore>,
    val werBySpeaker: Map<String, WerScore>,
    val firstChunkMsP50: Long,
    val firstChunkMsP95: Long,
    val totalMsP50: Long,
    val totalMsP95: Long,
    val realTimeFactorP50: Double?,
    val commandAction: Double?,
    val commandArgs: Double?,
)

object SpeechSummarizer {
    fun summarize(result: SpeechRunResult, commands: Map<String, CommandCase>): List<SpeechSummary> =
        result.transcribers.map { id -> summarizeOne(id, result.cases.filter { it.transcriber == id }, commands) }

    private fun summarizeOne(id: String, cases: List<SpeechCaseResult>, commands: Map<String, CommandCase>): SpeechSummary {
        val answersWithAction = id.endsWith("-action")
        val scored = if (answersWithAction) {
            emptyList()
        } else {
            cases.filter { it.error == null }.mapNotNull { c -> commands[c.commandId]?.let { c to SpeechScorer.score(it, c.transcript) } }
        }
        val finished = cases.filter { it.error == null }
        val judged = cases.filter { it.actionCorrect != null }
        fun werOf(group: Map<String, List<Pair<SpeechCaseResult, UtteranceScore>>>) =
            group.mapValues { (_, items) -> Wer.corpus(items.map { it.second.wer }) }
        return SpeechSummary(
            transcriber = id,
            takes = cases.size,
            errors = cases.count { it.error != null },
            wer = Wer.corpus(scored.map { it.second.wer }),
            entities = EntityScorer.corpus(scored.map { it.second.entities }),
            werByCondition = werOf(scored.groupBy { it.first.condition }),
            werBySpeaker = werOf(scored.groupBy { it.first.speaker }),
            firstChunkMsP50 = finished.map { it.firstChunkMs }.nearestRank(0.5) ?: 0L,
            firstChunkMsP95 = finished.map { it.firstChunkMs }.nearestRank(0.95) ?: 0L,
            totalMsP50 = finished.map { it.totalMs }.nearestRank(0.5) ?: 0L,
            totalMsP95 = finished.map { it.totalMs }.nearestRank(0.95) ?: 0L,
            realTimeFactorP50 = finished.filter { (it.audioMs ?: 0L) > 0 }.map { it.totalMs.toDouble() / it.audioMs!! }.nearestRank(0.5),
            commandAction = if (judged.isEmpty()) null else judged.count { it.actionCorrect == true }.toDouble() / judged.size,
            commandArgs = if (judged.isEmpty()) null else judged.count { it.argsCorrect == true }.toDouble() / judged.size,
        )
    }
}
