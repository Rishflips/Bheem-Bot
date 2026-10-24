package app.bheem.eval.report

import app.bheem.eval.run.Summary
import java.util.Locale

data class Check(val name: String, val value: String, val limit: String, val pass: Boolean)

object ReportBuilder {
    private fun pct(v: Double) = String.format(Locale.ROOT, "%.1f%%", v * 100)

    fun checks(s: Summary, t: Thresholds): List<Check> {
        if (s.loadError != null) return listOf(Check("loads", s.loadError.lineSequence().first().take(160), "loads", false))
        if (s.assistant) return assistantChecks(s, t)
        val jsonValid = minOf(s.commandJsonValid, s.notificationJsonValid)
        return listOf(
            Check("JSON validity", pct(jsonValid), ">= ${pct(t.jsonValidityMin)}", jsonValid >= t.jsonValidityMin),
            Check("command action", pct(s.commandAction), ">= ${pct(t.commandActionMin)}", s.commandAction >= t.commandActionMin),
            Check("command args", pct(s.commandArgs), ">= ${pct(t.commandArgsMin)}", s.commandArgs >= t.commandArgsMin),
            Check("notification args", pct(s.notificationArgs), ">= ${pct(t.notificationArgsMin)}", s.notificationArgs >= t.notificationArgsMin),
            Check("injection obedience", pct(s.injectionObedience), "<= ${pct(t.injectionObedienceMax)}", s.injectionObedience <= t.injectionObedienceMax),
            Check(
                "injection leak", s.injectionLeak?.let(::pct) ?: "not measured", "<= ${pct(t.injectionLeakMax)}",
                s.injectionLeak?.let { it <= t.injectionLeakMax } ?: false,
            ),
            Check("peak RSS (MB)", "${s.peakRssMb}", "<= ${t.peakRssMbMax}", s.peakRssMb <= t.peakRssMbMax),
            Check(
                "command first chunk p95 (ms)", "${s.commandFirstChunkMsP95}", "<= ${t.commandFirstChunkMsP95Max}",
                s.commandFirstChunkMsP95 <= t.commandFirstChunkMsP95Max,
            ),
            Check("load time (ms)", "${s.loadMs}", "<= ${t.loadMsMax}", (s.loadMs ?: Long.MAX_VALUE) <= t.loadMsMax),
            Check("run complete", if (s.complete) "yes" else "no (partial run)", "yes", s.complete),
        )
    }

    private fun assistantChecks(s: Summary, t: Thresholds): List<Check> {
        val action = t.assistantActionMin ?: error("thresholds.json has no assistantActionMin")
        val args = t.assistantArgsMin ?: error("thresholds.json has no assistantArgsMin")
        return listOf(
            Check("JSON validity", pct(s.commandJsonValid), ">= ${pct(t.jsonValidityMin)}", s.commandJsonValid >= t.jsonValidityMin),
            Check("assistant action", pct(s.commandAction), ">= ${pct(action)}", s.commandAction >= action),
            Check("assistant args", pct(s.commandArgs), ">= ${pct(args)}", s.commandArgs >= args),
            Check("run complete", if (s.complete) "yes" else "no (partial run)", "yes", s.complete),
        )
    }

    fun markdown(summaries: List<Summary>, t: Thresholds, unreadable: List<String> = emptyList()): String {
        if (summaries.isEmpty() && unreadable.isEmpty()) return "# Phase 0 results\n\nNo results found.\n"
        val sb = StringBuilder("# Phase 0 results\n\n")
        if (unreadable.isNotEmpty()) {
            sb.append("## Unreadable result files\n\n")
            for (u in unreadable) sb.append("- $u\n")
            sb.append("\n")
        }
        sb.append("| Model | Backend | Thinking | Device | Verdict |\n| :--- | :--- | :--- | :--- | :--- |\n")
        val checked = summaries.map { it to checks(it, t) }
        for ((s, c) in checked) {
            val verdict = if (c.all { it.pass }) "PASS" else "FAIL"
            sb.append("| ${s.modelId} | ${s.backend} | ${s.thinking} | ${s.deviceLabel} | $verdict |\n")
        }
        sb.append("\n## Details\n")
        for ((s, c) in checked) {
            sb.append("\n### ${s.modelId} / ${s.backend} / ${s.thinking} / ${s.deviceLabel} (${s.deviceRamMb} MB RAM)\n\n")
            sb.append("| Check | Value | Limit | Result |\n| :--- | :--- | :--- | :--- |\n")
            for (k in c) sb.append("| ${k.name} | ${k.value} | ${k.limit} | ${if (k.pass) "pass" else "FAIL"} |\n")
            if (s.loadError == null) {
                sb.append("\nCases: ${s.commandCount} commands, ${s.notificationCount} notifications, ${s.adversarialCount} adversarial.\n")
                sb.append("Notification first chunk p95: ${s.notificationFirstChunkMsP95} ms (recorded, no limit set).\n")
                sb.append("Speed: ${String.format(Locale.ROOT, "%.1f", s.decodeTokensPerSecP50)} tokens/s median decode, ")
                sb.append("${String.format(Locale.ROOT, "%.1f", s.charsPerSecP50)} chars/s, first chunk p50 ${s.firstChunkMsP50} ms. ")
                sb.append("Thermal max status ${s.maxThermalStatus}, battery drop ${s.batteryDropPct}%, errors ${s.errors}.\n")
            }
        }
        return sb.toString()
    }
}
