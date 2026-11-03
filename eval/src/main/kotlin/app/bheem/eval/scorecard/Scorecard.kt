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
    val promptVersion: String = "",
    val device: String = "",
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

    /** [deviceLabel] picks one batch of runs by its exact label; two runs for one set and backend are an error, never a silent choice. */
    fun build(modelId: String, runs: List<RunResult>, dataset: Dataset, deviceLabel: String? = null): Scorecard {
        val mine = runs.filter { it.modelId == modelId && (deviceLabel == null || it.device.label == deviceLabel) }
        mine.groupBy { setOf(it) to it.backend }.forEach { (key, group) ->
            require(group.size == 1) { "${group.size} runs for ${key.first} on ${key.second} (${group.joinToString { it.device.label }}); name one batch with the device label" }
        }
        val rows = mine.map { run ->
            val set = setOf(run)
            val withCode = Summarizer.summarize(Rescorer.rescore(run, dataset, AnswerFormat.LINE))
            val raw = if (set == "english-full") null else Summarizer.summarize(Rescorer.rescore(run, dataset, AnswerFormat.LINE, throughPipeline = false)).commandAction
            ScoreRow(
                set, run.backend, withCode.commandCount, raw, withCode.commandAction, withCode.commandArgs, withCode.commandJsonValid, withCode.loadMs,
                withCode.firstChunkMsP50, withCode.firstChunkMsP95, withCode.decodeTokensPerSecP50, withCode.peakRssMb, withCode.batteryDropPct, withCode.complete, withCode.injectionLeak, run.promptVersion, run.device.label,
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
        if (card.rows.map { it.promptVersion }.distinct().size > 1) {
            appendLine("**These rows use different prompt versions (see the Prompt column): do not compare them with each other, and do not judge a swap from them.**")
            appendLine()
        }
        appendLine("| Set | Backend | Prompt | Device | Cases | Action (with code) | Action (model alone) | Args | JSON valid | Load ms | First token p50 ms | First token p95 ms | Decode tok/s p50 | Peak RSS MB | Battery drop % | Injection leak | Complete |")
        appendLine("| :-- | :-- | :-- | :-- | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | --: | :-- |")
        for (r in card.rows) {
            appendLine(
                "| ${r.set} | ${r.backend} | ${r.promptVersion} | ${r.device} | ${r.cases} | ${pct(r.action)} | ${pct(r.rawAction)} | ${pct(r.args)} | ${pct(r.jsonValid)} | ${r.loadMs ?: "n/a"} | ${r.firstChunkMsP50} | ${r.firstChunkMsP95} | " +
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
        val leakMax = thresholds.injectionLeakMax
        val ratio = requireNotNull(thresholds.swapMaxFirstChunkRatio) { "thresholds.json has no swapMaxFirstChunkRatio" }
        fun pct(v: Double) = String.format(Locale.ROOT, "%.1f%%", v * 100)
        return primary.rows.flatMap { p ->
            val c = candidate.row(p.set, p.backend)
                ?: return@flatMap listOf(SwapFinding(p.set, p.backend, "measured", "not measured", "measured", false))
            fun accuracy(name: String, cv: Double?, pv: Double?): SwapFinding? =
                if (cv == null || pv == null) null else SwapFinding(p.set, p.backend, name, pct(cv), pct(pv), cv >= pv - drop - EPSILON)
            val comparable = c.complete && p.complete && c.cases == p.cases && (c.promptVersion.isBlank() || p.promptVersion.isBlank() || c.promptVersion == p.promptVersion)
            val why = "complete ${c.complete}/${p.complete}, cases ${c.cases}/${p.cases}, prompt ${c.promptVersion.ifBlank { "?" }}/${p.promptVersion.ifBlank { "?" }}"
            listOfNotNull(
                SwapFinding(p.set, p.backend, "comparable", why, "same cases, same prompt, complete", comparable),
                accuracy("action", c.action, p.action),
                accuracy("model alone", c.rawAction, p.rawAction),
                accuracy("args", c.args, p.args),
                SwapFinding(p.set, p.backend, "JSON validity", pct(c.jsonValid), pct(p.jsonValid), c.jsonValid >= p.jsonValid - drop - EPSILON),
                c.injectionLeak?.let { SwapFinding(p.set, p.backend, "injection leak", pct(it), "<= ${pct(leakMax)}", it <= leakMax + EPSILON) },
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
