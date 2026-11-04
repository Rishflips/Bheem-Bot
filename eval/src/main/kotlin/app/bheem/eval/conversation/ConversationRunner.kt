package app.bheem.eval.conversation

import app.bheem.core.assistant.ChatPrompt
import app.bheem.core.assistant.ChatText
import app.bheem.core.assistant.Clarifier
import app.bheem.core.assistant.FollowUp
import app.bheem.core.assistant.SmallTalk
import app.bheem.core.assistant.Turn
import app.bheem.core.device.DeviceQuestions
import app.bheem.core.device.DeviceSnapshot
import app.bheem.core.device.Network
import app.bheem.core.gate.Gate
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.memory.InMemoryMemoryStore
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.PhoneBook
import app.bheem.core.memory.Recall
import app.bheem.core.memory.RecordKind
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.Outcome
import app.bheem.core.pipeline.Planner
import app.bheem.core.reminder.InMemoryReminderStore
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking

/** What the app did with one sentence. [prompt] is the exact text the model would receive when the path is chat. */
data class TurnResult(
    val path: String,
    val text: String,
    val action: String? = null,
    val args: Map<String, String> = emptyMap(),
    val citations: List<String> = emptyList(),
    val prompt: String? = null,
    val promptTokensEstimate: Int? = null,
)

/**
 * Plays a conversation through the real pipeline, gate, recall and small talk, in the order `AppController` uses
 * (small talk, a waiting clarification, "yes", then the pipeline). No model runs: the planner answers with the line the case
 * scripts, or NoAction, and a chat turn only records the prompt the model would have been given.
 */
class ConversationRunner(private val fixture: Fixture) {
    fun play(case: ConvCase): List<TurnResult> {
        val zone = ZoneId.of(fixture.zone)
        val now = ConversationFiles.at(case.now ?: fixture.now)
        val memory = InMemoryMemoryStore()
        val reminders = InMemoryReminderStore()
        fixture.notes.forEach { memory.add(MemoryRecord(it.id, RecordKind.NOTE, it.text, now.minusDays(it.daysAgo.toLong()))) }
        fixture.reminders.forEachIndexed { i, r ->
            reminders.add("seed-$i", r.text, ConversationFiles.at(r.at).atZone(zone).toInstant().toEpochMilli(), zone.id)
        }
        val recall = Recall(memory, reminders, zone)
        var scripted: String? = null
        var ids = 0
        val pipeline = CommandPipeline(
            Planner { _, _ -> scripted ?: "NoAction" }, Gate(), Ledger(InMemoryLedgerStore()),
            phones = PhoneBook(memory.all()), newId = { "p${ids++}" },
        )

        val device = fixture.device
        val phone = DeviceSnapshot(
            now = now, zone = zone,
            batteryPercent = device?.batteryPercent, charging = device?.charging,
            network = device?.let { runCatching { Network.valueOf(it.network) }.getOrDefault(Network.UNKNOWN) } ?: Network.UNKNOWN,
            doNotDisturb = device?.doNotDisturb, storageFreeGb = device?.storageFreeGb, storageTotalGb = device?.storageTotalGb,
            nextAlarm = device?.nextAlarm?.let { ConversationFiles.at(it) },
        )
        val history = mutableListOf<Turn>()
        var pendingClarify: String? = null
        var offered = false
        val results = ArrayList<TurnResult>()
        for (turn in case.turns) {
            val result = route(turn, now, phone, pipeline, recall, history, pendingClarify, offered) { scripted = it }
            pendingClarify = if (result.path == "clarify") (result.args["original"] ?: turn.user) else null
            offered = result.path == "action" || result.path == "plan"
            history += Turn.User(turn.user, spoken = false, now)
            history += if (result.path == "chat") Turn.Chat("(the model's reply)", now) else Turn.Answer(result.text, emptyList(), now)
            results += result
        }
        return results
    }

    private fun route(
        turn: ConvTurn,
        now: LocalDateTime,
        phone: DeviceSnapshot,
        pipeline: CommandPipeline,
        recall: Recall,
        history: List<Turn>,
        pendingClarify: String?,
        offered: Boolean,
        script: (String?) -> Unit,
    ): TurnResult {
        val typed = turn.user.trim()
        SmallTalk.reply(typed)?.let { return TurnResult("smalltalk", it) }
        DeviceQuestions.answer(typed, phone)?.let { return TurnResult("device", it) }
        val combined = pendingClarify?.let { Clarifier.combine(it, typed) }
        if (combined == null && FollowUp.isConfirmation(typed)) {
            return if (offered) TurnResult("confirm", "approves what the last answer offered") else TurnResult("notice", "Tap the button on the card you mean.")
        }
        val sentence = combined ?: typed
        script(turn.planner)
        return when (val outcome = runBlocking { pipeline.handle(sentence, now) }) {
            is Outcome.Card -> TurnResult("action", outcome.action.action, outcome.action.action, outcome.action.args)
            is Outcome.Plan -> TurnResult("plan", outcome.cards.joinToString("; ") { it.action.action }, args = mapOf("steps" to outcome.cards.size.toString()))
            is Outcome.Clarify -> TurnResult("clarify", outcome.question, args = mapOf("original" to outcome.original))
            is Outcome.Question -> recall.answer(outcome.query, now).let { TurnResult("recall", it.text, citations = it.citations.map { c -> c.recordId }) }
            is Outcome.Refused -> TurnResult("refused", outcome.reason)
            is Outcome.ModelUnavailable -> TurnResult("refused", outcome.reason)
            Outcome.Nothing -> {
                val message = ChatText.message(history, sentence, phone.nowLine())
                val prompt = ChatPrompt.SYSTEM + "\n\n" + message
                TurnResult("chat", "", prompt = prompt, promptTokensEstimate = (prompt.length + 3) / 4)
            }
        }
    }
}

data class TurnScore(val passed: Boolean, val needsModel: Boolean, val reasons: List<String>)

object ConversationScorer {
    fun score(expect: ExpectedTurn, result: TurnResult): TurnScore {
        val reasons = ArrayList<String>()
        if (expect.path != null && expect.path != result.path) reasons += "path was ${result.path}, expected ${expect.path}"
        if (expect.action != null) {
            if (result.action != expect.action) reasons += "action was ${result.action}, expected ${expect.action}"
            for ((key, value) in expect.args) if (result.args[key] != value) reasons += "argument $key was ${result.args[key]}, expected $value"
        }
        val pathOk = expect.path == null || expect.path == result.path
        val needsModel = pathOk && result.path == "chat" && (expect.must.isNotEmpty() || expect.mustNot.isNotEmpty() || expect.judged)
        if (result.path != "chat") {
            for (m in expect.must) if (!result.text.contains(m, ignoreCase = true)) reasons += "reply lacks \"$m\""
            for (m in expect.mustNot) if (result.text.contains(m, ignoreCase = true)) reasons += "reply contains \"$m\""
        }
        return TurnScore(reasons.isEmpty(), needsModel, reasons)
    }
}

data class GroupCount(val turns: Int, val passed: Int, val needsModel: Int)

class ConversationReport(val cases: Int, val byGroup: Map<String, GroupCount>) {
    val total: GroupCount get() = GroupCount(byGroup.values.sumOf { it.turns }, byGroup.values.sumOf { it.passed }, byGroup.values.sumOf { it.needsModel })

    fun group(name: String): GroupCount = byGroup[name] ?: GroupCount(0, 0, 0)

    companion object {
        fun of(cases: List<ConvCase>, runner: ConversationRunner): ConversationReport {
            val groups = LinkedHashMap<String, GroupCount>()
            for (case in cases) {
                val results = runner.play(case)
                case.turns.zip(results).forEach { (turn, result) ->
                    val score = ConversationScorer.score(turn.expect, result)
                    val old = groups[case.group] ?: GroupCount(0, 0, 0)
                    groups[case.group] = GroupCount(old.turns + 1, old.passed + if (score.passed) 1 else 0, old.needsModel + if (score.needsModel) 1 else 0)
                }
            }
            return ConversationReport(cases.size, groups)
        }
    }
}
