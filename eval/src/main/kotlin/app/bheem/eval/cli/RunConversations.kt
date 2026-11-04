package app.bheem.eval.cli

import app.bheem.eval.conversation.ConversationFiles
import app.bheem.eval.conversation.ConversationReport
import app.bheem.eval.conversation.ConversationRunner
import app.bheem.eval.conversation.ConversationScorer
import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The routing baseline: plays the development conversations through the real pipeline, gate, recall and small talk with no model,
 * writes per-case detail for them, and prints counts per group. The held-out set is counted per group only: no ids, no text.
 * args: data folder, fixture file, results folder.
 */
object RunConversations {
    @JvmStatic
    fun main(args: Array<String>) {
        val data = File(args[0])
        val fixture = ConversationFiles.parseFixture(File(args[1]).readText())
        val out = File(args[2]).also { it.mkdirs() }
        val runner = ConversationRunner(fixture)

        val dev = ConversationFiles.parseCases(File(data, "conversations.jsonl").readText())
        val detail = dev.flatMap { case ->
            val results = runner.play(case)
            case.turns.zip(results).mapIndexed { i, (turn, result) ->
                val score = ConversationScorer.score(turn.expect, result)
                buildJsonObject {
                    put("id", case.id)
                    put("turn", i + 1)
                    put("group", case.group)
                    put("user", turn.user)
                    put("expectedPath", turn.expect.path ?: "")
                    put("path", result.path)
                    put("passed", score.passed)
                    put("needsModel", score.needsModel)
                    put("reasons", JsonArray(score.reasons.map { JsonPrimitive(it) }))
                    put("action", result.action ?: "")
                    put("promptTokensEstimate", result.promptTokensEstimate ?: 0)
                    put("prompt", result.prompt ?: "")
                }
            }
        }
        File(out, "conversations-routing.json").writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(detail)))

        val devReport = ConversationReport.of(dev, runner)
        val heldFile = File(data, "conversations-heldout.jsonl")
        val heldReport = if (heldFile.exists()) ConversationReport.of(ConversationFiles.parseCases(heldFile.readText()), runner) else null

        val text = buildString {
            appendLine("# Conversation routing baseline (no model)")
            appendLine()
            appendLine("Fixture time ${fixture.now}. A turn passes when its path, action, arguments and the checks on code-written text are right. Turns on the chat path whose checks need the model's reply are counted as needs model, not failed.")
            appendLine()
            appendLine("| Set | Group | Turns | Passed | Needs model |")
            appendLine("| :-- | :-- | --: | --: | --: |")
            for ((name, report) in listOfNotNull("development" to devReport, heldReport?.let { "held-out" to it })) {
                for ((group, c) in report.byGroup) appendLine("| $name | $group | ${c.turns} | ${c.passed} | ${c.needsModel} |")
                val t = report.total
                appendLine("| **$name** | **all** | **${t.turns}** | **${t.passed}** | **${t.needsModel}** |")
            }
        }
        File(out, "conversations-routing.md").writeText(text)
        print(text)
    }
}
