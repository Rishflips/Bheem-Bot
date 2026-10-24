package app.bheem.eval.gate

import app.bheem.eval.data.Dataset
import app.bheem.eval.parse.AnswerFormat
import app.bheem.eval.report.Check
import app.bheem.eval.report.ReportBuilder
import app.bheem.eval.report.Thresholds
import app.bheem.eval.run.Rescorer
import app.bheem.eval.run.RunResult
import app.bheem.eval.run.Summarizer
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class KnownFailure(val run: String, val check: String, val measured: String, val reason: String)

data class GateRow(val run: String, val check: String, val value: String, val limit: String, val pass: Boolean, val known: Boolean)

data class GateResult(val rows: List<GateRow>, val problems: List<String>) {
    val ok: Boolean get() = problems.isEmpty()
}

object QualityGate {
    private const val ASSISTANT_PREFIX = "ast-"

    private fun pct(v: Double) = String.format(Locale.ROOT, "%.1f%%", v * 100)

    fun evaluate(runs: Map<String, RunResult>, dataset: Dataset, thresholds: Thresholds, known: List<KnownFailure>): GateResult {
        val rows = runs.toSortedMap().flatMap { (name, saved) ->
            val run = Rescorer.rescore(saved, dataset, AnswerFormat.LINE)
            val summary = Summarizer.summarize(run)
            val checks = if (run.cases.any { it.id.startsWith(ASSISTANT_PREFIX) }) assistantChecks(summary, thresholds) else ReportBuilder.checks(summary, thresholds)
            checks.map { c -> GateRow(name, c.name, c.value, c.limit, c.pass, known.any { it.run == name && it.check == c.name }) }
        }
        val problems = mutableListOf<String>()
        for (r in rows) if (!r.pass && !r.known) problems += "${r.run}: ${r.check} failed (${r.value}, limit ${r.limit})"
        for (k in known) {
            val row = rows.firstOrNull { it.run == k.run && it.check == k.check }
            when {
                row == null -> problems += "known failure ${k.run} / ${k.check} matches no check"
                row.pass -> problems += "known failure ${k.run} / ${k.check} now passes: remove it from the list"
                gotWorse(row, k.measured) -> problems += "known failure ${k.run} / ${k.check} got worse: ${k.measured} before, ${row.value} now"
            }
        }
        return GateResult(rows, problems)
    }

    private fun number(text: String): Double? = text.trim().removeSuffix("%").toDoubleOrNull()

    private fun gotWorse(row: GateRow, measured: String): Boolean {
        val now = number(row.value) ?: return false
        val before = number(measured) ?: return false
        return if (row.limit.startsWith(">=")) now < before else now > before
    }

    private fun assistantChecks(s: app.bheem.eval.run.Summary, t: Thresholds): List<Check> {
        val action = t.assistantActionMin ?: error("thresholds.json has no assistantActionMin")
        val args = t.assistantArgsMin ?: error("thresholds.json has no assistantArgsMin")
        return listOf(
            Check("JSON validity", pct(s.commandJsonValid), ">= ${pct(t.jsonValidityMin)}", s.commandJsonValid >= t.jsonValidityMin),
            Check("assistant action", pct(s.commandAction), ">= ${pct(action)}", s.commandAction >= action),
            Check("assistant args", pct(s.commandArgs), ">= ${pct(args)}", s.commandArgs >= args),
            Check("run complete", if (s.complete) "yes" else "no (partial run)", "yes", s.complete),
        )
    }

    fun table(result: GateResult): String = buildString {
        appendLine("| Run | Check | Value | Limit | Result |")
        appendLine("| :-- | :-- | :-- | :-- | :-- |")
        for (r in result.rows) appendLine("| ${r.run} | ${r.check} | ${r.value} | ${r.limit} | ${if (r.pass) "pass" else if (r.known) "known failure" else "FAIL"} |")
        if (result.problems.isNotEmpty()) {
            appendLine()
            for (p in result.problems) appendLine("PROBLEM: $p")
        }
    }
}
