package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import java.util.Locale

object SpeechReportBuilder {
    private const val LIVE = "-live"
    private const val ACTION = "-action"

    private fun pct(v: Double?) = if (v == null) "n/a" else String.format(Locale.ROOT, "%.1f%%", v * 100)

    fun markdown(result: SpeechRunResult, commands: Map<String, CommandCase>): String = buildString {
        appendLine("# Speech-to-text results")
        appendLine()
        val device = result.device
        appendLine("Device: ${device.label} (${device.model}, ${device.soc}, ${device.totalRamMb} MB RAM, SDK ${device.sdk}).")
        if (result.loadError != null) {
            appendLine()
            appendLine("The model failed to load: ${result.loadError.lineSequence().first().take(200)}")
            return@buildString
        }
        appendLine("Model load: ${result.loadMs} ms. Run complete: ${if (result.complete) "yes" else "no (partial run)"}.")
        appendLine()
        appendLine("The sentences are synthetic commands read aloud. Takes with an error are counted but not scored.")
        appendLine()
        val summaries = SpeechSummarizer.summarize(result, commands)
        if (summaries.any { it.transcriber.endsWith(LIVE) }) {
            appendLine(
                "Transcribers whose names end in $LIVE use a live feed: the audio is given in small chunks at the speed of speech. " +
                    "First chunk is the time to process the last chunk and finish; total is the time from the end of the audio to the final text. " +
                    "The real-time factor does not apply to them.",
            )
            appendLine()
        }
        if (summaries.any { it.transcriber.endsWith(ACTION) }) {
            appendLine(
                "Transcribers whose names end in $ACTION answer with the action directly, in one call, so there is no transcript: " +
                    "the word and entity columns do not apply and only the end-to-end columns count.",
            )
            appendLine()
        }
        appendLine("| Transcriber | Takes | Errors | WER | Entity error | Amount | Name | Time | Action (end to end) | Details (end to end) | First chunk p50 / p95 (ms) | Total p50 / p95 (ms) | Real-time factor p50 |")
        appendLine("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |")
        for (s in summaries) {
            val rtf = if (s.transcriber.endsWith(LIVE)) "n/a" else s.realTimeFactorP50?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "n/a"
            appendLine(
                "| ${s.transcriber} | ${s.takes} | ${s.errors} | ${pct(s.wer.rate)} | ${pct(s.entities.errorRate())} | " +
                    "${pct(s.entities.errorRate(EntityKind.AMOUNT))} | ${pct(s.entities.errorRate(EntityKind.NAME))} | ${pct(s.entities.errorRate(EntityKind.TIME))} | " +
                    "${pct(s.commandAction)} | ${pct(s.commandArgs)} | ${s.firstChunkMsP50} / ${s.firstChunkMsP95} | ${s.totalMsP50} / ${s.totalMsP95} | $rtf |",
            )
        }
        for (s in summaries) {
            appendLine()
            appendLine("## ${s.transcriber}")
            appendLine()
            table("Condition", s.werByCondition)
            appendLine()
            table("Speaker", s.werBySpeaker)
        }
    }

    private fun StringBuilder.table(title: String, groups: Map<String, WerScore>) {
        appendLine("| $title | WER | Reference words |")
        appendLine("| --- | --- | --- |")
        for ((name, score) in groups) appendLine("| $name | ${pct(score.rate)} | ${score.referenceWords} |")
    }
}
