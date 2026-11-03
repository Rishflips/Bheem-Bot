package app.bheem.eval.scorecard

import app.bheem.eval.data.Dataset
import app.bheem.eval.parse.AnswerFormat
import app.bheem.eval.report.Thresholds
import app.bheem.eval.run.Rescorer
import app.bheem.eval.run.RunResult
import app.bheem.eval.run.Summarizer
import java.util.Locale

/** One measured line of a model's scorecard: one set of cases on one backend. Held-out sets appear only as these aggregates. */
data class ScoreRow(
    val set: String,
    val backend: String,
    val cases: Int,
    val rawAction: Double?,
    val action: Double,
    val args: Double,
    val jsonValid: Double,
    val loadMs: Long?,
    val firstChunkMsP50: Long,
    val firstChunkMsP95: Long,
    val decodeTokensPerSecP50: Double,
    val peakRssMb: Long,
    val batteryDropPct: Int,
    val complete: Boolean,
    val injectionLeak: Double? = null,
)

data class Scorecard(val modelId: String, val promptVersion: String, val device: String, val rows: List<ScoreRow>) {
    fun row(set: String, backend: String): ScoreRow? = rows.firstOrNull { it.set == set && it.backend == backend }
}

object ScorecardBuilder {
    private const val ASSISTANT = "ast-"
    private const val HELD_OUT = "ast-h"

    fun setOf(run: RunResult): String = when {
        run.cases.isNotEmpty() && run.cases.all { it.id.startsWith(HELD_OUT) } -> "held-out"
        run.cases.any { it.id.startsWith(ASSISTANT) } -> "assistant-dev"
        else -> "english-full"
    }

    fun build(modelId: String, runs: List<RunResult>, dataset: Dataset): Scorecard {
        val mine = runs.filter { it.modelId == modelId }
        val rows = mine.map { run ->
            val set = setOf(run)
            val withCode = Summarizer.summarize(Rescorer.rescore(run, dataset, AnswerFormat.LINE))
            val raw = if (set == "english-full") null else Summarizer.summarize(Rescorer.rescore(run, dataset, AnswerFormat.LINE, throughPipeline = false)).commandAction
            ScoreRow(
                set, run.backend, withCode.commandCount, raw, withCode.commandAction, withCode.commandArgs, withCode.commandJsonValid, withCode.loadMs,
                withCode.firstChunkMsP50, withCode.firstChunkMsP95, withCode.decodeTokensPerSecP50, withCode.peakRssMb, withCode.batteryDropPct, withCode.complete, withCode.injectionLeak,
            )
        }.sortedWith(compareBy({ it.set }, { it.backend }))
        return Scorecard(modelId, mine.map { it.promptVersion }.distinct().joinToString(", "), mine.map { it.device.label }.distinct().joinToString(", "), rows)
    }

    private fun pct(v: Double?) = v?.let { String.format(Locale.ROOT, "%.1f%%", it * 100) } ?: "n/a"

    fun markdown(card: Scorecard, licence: String, runtime: String, date: String): String = buildString {
        appendLine("# Scorecard: ${card.modelId}")
        appendLine()
        appendLine("Date $date. Device label: ${card.device}. Prompt version: ${card.promptVersion}. Runtime: $runtime. Licence (from the registry): $licence.")
        appendLine("Every number here is measured from saved run files on the device above. Held-out rows are aggregates only; no held-out case is listed.")
        appendLine()
        appendLine("| Set | Backend | Cases | Action (with code) | Action (model alone) | Args | JSON valid | Load ms | First token p50 ms | First token p95 ms | Decode tok/s p50 | Peak RSS MB | Battery drop % | Injection leak | Complete |")
        appendLine("| :-- | :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | :-- |")
        for (r in card.rows) {
            appendLine(
                "| ${r.set} | ${r.backend} | ${r.cases} | ${pct(r.action)} | ${pct(r.rawAction)} | ${pct(r.args)} | ${pct(r.jsonValid)} | ${r.loadMs ?: "n/a"} | ${r.firstChunkMsP50} | ${r.firstChunkMsP95} | " +
                    "${String.format(Locale.ROOT, "%.1f", r.decodeTokensPerSecP50)} | ${r.peakRssMb} | ${r.batteryDropPct} | ${pct(r.injectionLeak)} | ${if (r.complete) "yes" else "no"} |",
            )
        }
    }
}

data class SwapFinding(val set: String, val backend: String, val check: String, val candidate: String, val primary: String, val pass: Boolean)

/** A candidate may be at most a few points worse than the current primary on any accuracy row, and not much slower to the first token. */
object SwapRule {
    private const val EPSILON = 1e-9

    fun compare(candidate: Scorecard, primary: Scorecard, thresholds: Thresholds): List<SwapFinding> {
        val drop = requireNotNull(thresholds.swapMaxAccuracyDrop) { "thresholds.json has no swapMaxAccuracyDrop" }
        val ratio = requireNotNull(thresholds.swapMaxFirstChunkRatio) { "thresholds.json has no swapMaxFirstChunkRatio" }
        fun pct(v: Double) = String.format(Locale.ROOT, "%.1f%%", v * 100)
        return primary.rows.flatMap { p ->
            val c = candidate.row(p.set, p.backend)
                ?: return@flatMap listOf(SwapFinding(p.set, p.backend, "measured", "not measured", "measured", false))
            fun accuracy(name: String, cv: Double?, pv: Double?): SwapFinding? =
                if (cv == null || pv == null) null else SwapFinding(p.set, p.backend, name, pct(cv), pct(pv), cv >= pv - drop - EPSILON)
            listOfNotNull(
                accuracy("action", c.action, p.action),
                accuracy("model alone", c.rawAction, p.rawAction),
                accuracy("args", c.args, p.args),
                SwapFinding(p.set, p.backend, "first token p95", "${c.firstChunkMsP95} ms", "${p.firstChunkMsP95} ms", c.firstChunkMsP95 <= p.firstChunkMsP95 * ratio + EPSILON),
            )
        }
    }

    fun markdown(findings: List<SwapFinding>, candidateId: String, primaryId: String): String = buildString {
        appendLine("## Swap rule: $candidateId against $primaryId")
        appendLine()
        appendLine("| Set | Backend | Check | Candidate | Primary | Result |")
        appendLine("| :-- | :-- | :-- | --: | --: | :-- |")
        for (f in findings) appendLine("| ${f.set} | ${f.backend} | ${f.check} | ${f.candidate} | ${f.primary} | ${if (f.pass) "pass" else "FAIL"} |")
        appendLine()
        appendLine(if (findings.all { it.pass }) "The candidate meets the relative rule." else "The candidate does not meet the relative rule on the rows marked FAIL. A go needs a written reason naming each row it loses.")
    }
}
