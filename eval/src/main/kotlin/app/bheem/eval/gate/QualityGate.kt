package app.bheem.eval.gate

import app.bheem.eval.data.Dataset
import app.bheem.eval.parse.AnswerFormat
import app.bheem.eval.report.ReportBuilder
import app.bheem.eval.report.Thresholds
import app.bheem.eval.run.Rescorer
import app.bheem.eval.run.RunResult
import app.bheem.eval.run.Summarizer
import java.security.MessageDigest
import java.util.Locale
import kotlinx.serialization.Serializable

@Serializable
data class KnownFailure(val run: String, val check: String, val measured: String, val reason: String)

data class DataPins(val expected: Map<String, String>, val actual: Map<String, String>) {
    companion object {
        val REQUIRED_FILES = setOf("commands.jsonl", "assistant.jsonl", "assistant-heldout.jsonl", "notifications.jsonl", "adversarial.jsonl")

        fun hash(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.replace(CRLF, LF).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        private const val CRLF = "\r\n"
        private const val LF = "\n"
    }
}

data class GateRow(val run: String, val check: String, val value: String, val limit: String, val pass: Boolean, val known: Boolean)

data class GateResult(val rows: List<GateRow>, val problems: List<String>) {
    val ok: Boolean get() = problems.isEmpty()
}

object QualityGate {
    private const val ASSISTANT_PREFIX = "ast-"
    private const val HELD_OUT_PREFIX = "ast-h"

    private fun pct(v: Double) = String.format(Locale.ROOT, "%.1f%%", v * 100)

    fun evaluate(runs: Map<String, RunResult>, dataset: Dataset, thresholds: Thresholds, known: List<KnownFailure>, pins: DataPins? = null): GateResult {
        val rows = runs.toSortedMap().flatMap { (name, saved) ->
            val run = Rescorer.rescore(saved, dataset, AnswerFormat.LINE)
            val summary = Summarizer.summarize(run)
            ReportBuilder.checks(summary, thresholds).map { c -> GateRow(name, c.name, c.value, c.limit, c.pass, known.any { it.run == name && it.check == c.name }) }
        }
        val problems = mutableListOf<String>()
        for ((name, saved) in runs.toSortedMap()) problems += coverageProblems(name, saved, dataset)
        pins?.let { problems += pinProblems(it) }
        for (r in rows) if (!r.pass && !r.known) problems += "${r.run}: ${r.check} failed (${r.value}, limit ${r.limit})"
        for (k in known) {
            val row = rows.firstOrNull { it.run == k.run && it.check == k.check }
            when {
                row == null -> problems += "known failure ${k.run} / ${k.check} matches no check"
                row.pass -> problems += "known failure ${k.run} / ${k.check} now passes: remove it from the list"
                else -> drift(row, k.measured)?.let { problems += "known failure ${k.run} / ${k.check} $it" }
            }
        }
        return GateResult(rows, problems)
    }

    private fun number(text: String): Double? = text.trim().removeSuffix("%").toDoubleOrNull()

    private fun drift(row: GateRow, measured: String): String? {
        val now = number(row.value)
        val before = number(measured)
        if (now == null || before == null) return "is not comparable: recorded \"$measured\", now \"${row.value}\""
        if (now == before) return null
        val worse = if (row.limit.startsWith(">=")) now < before else now > before
        return if (worse) "got worse: $measured before, ${row.value} now" else "improved: update measured from $measured to ${row.value} in the same change"
    }

    private fun coverageProblems(name: String, run: RunResult, dataset: Dataset): List<String> {
        val assistant = run.cases.any { it.id.startsWith(ASSISTANT_PREFIX) }
        val heldOut = run.cases.isNotEmpty() && run.cases.all { it.id.startsWith(HELD_OUT_PREFIX) }
        val scope: Map<String, String> = if (heldOut) {
            dataset.commands.filter { it.id.startsWith(HELD_OUT_PREFIX) }.associate { it.id to "command" }
        } else if (assistant) {
            dataset.commands.filter { it.id.startsWith(ASSISTANT_PREFIX) && !it.id.startsWith(HELD_OUT_PREFIX) }.associate { it.id to "command" }
        } else {
            dataset.commands.filter { it.lang == "en" && !it.id.startsWith(ASSISTANT_PREFIX) }.associate { it.id to "command" } +
                dataset.notifications.associate { it.id to "notification" } +
                dataset.adversarial.filter { it.lang == "en" }.associate { it.id to "adversarial" }
        }
        val ids = run.cases.map { it.id }
        val inRun = ids.toSet()
        return listOfNotNull(
            ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.sorted().takeIf { it.isNotEmpty() }?.let { "$name: duplicate case ids: ${it.take(5)}" },
            run.cases.filter { it.id in scope && it.suite != scope[it.id] }.map { it.id }.takeIf { it.isNotEmpty() }
                ?.let { "$name: cases with the wrong suite: ${it.take(5)}" },
            (inRun - scope.keys).sorted().takeIf { it.isNotEmpty() }?.let { "$name: case ids not in scope for this run: ${it.take(5)}" },
            (scope.keys - inRun).sorted().takeIf { it.isNotEmpty() }?.let { "$name: dataset cases missing from this run: ${it.take(5)}" },
        )
    }

    private fun pinProblems(pins: DataPins): List<String> =
        listOfNotNull(
            "eval/gate-data-pins.json must list exactly ${DataPins.REQUIRED_FILES.sorted()}".takeIf { pins.expected.keys != DataPins.REQUIRED_FILES },
        ) + pins.expected.toSortedMap().mapNotNull { (file, hash) ->
            val now = pins.actual[file]
            if (now != hash) "$file changed since the goldens were recorded: re-run on the phone or update eval/gate-data-pins.json in the same change" else null
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
