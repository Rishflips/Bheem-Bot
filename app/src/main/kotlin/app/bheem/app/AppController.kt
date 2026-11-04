package app.bheem.app

import app.bheem.core.feed.Feed
import app.bheem.core.feed.FeedSections
import app.bheem.core.feed.todayRows
import app.bheem.core.privacy.Diagnostics
import app.bheem.core.feed.LedgerLine
import app.bheem.core.feed.LedgerLines
import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.assistant.Clarifier
import app.bheem.core.assistant.FollowUp
import app.bheem.core.assistant.PlanRunner
import app.bheem.core.assistant.Thread
import app.bheem.core.brief.BriefTime
import app.bheem.core.memory.RecordKind
import app.bheem.core.assistant.Turn
import app.bheem.core.pipeline.Outcome
import app.bheem.core.speech.TalkSession
import app.bheem.core.speech.VoiceResult
import app.bheem.core.speech.VoiceState
import app.bheem.core.proposal.ApprovalResult
import android.os.SystemClock
import android.util.Log
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineExceptionHandler
import app.bheem.models.host.ModelState
import app.bheem.models.host.SupplyState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

const val LEDGER_LIMIT = 300

class AppController(private val graph: AppGraph) {
    private val failures = CoroutineExceptionHandler { _, error ->
        _message.value = "Something went wrong: ${error.message ?: error::class.java.simpleName}."
        _busy.value = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + failures)
    private val _sections = MutableStateFlow(FeedSections(emptyList(), emptyList(), emptyList()))
    private val _ledger = MutableStateFlow<List<LedgerLine>?>(null)
    private val _diagnostics = MutableStateFlow<String?>(null)
    private val _message = MutableStateFlow<String?>(null)
    private val _busy = MutableStateFlow(false)
    private val thread = Thread(store = graph.storage.turns)
    private val _turns = MutableStateFlow<List<Turn>>(thread.turns())
    private val _doneTasks = MutableStateFlow<Set<String>>(emptySet())

    val turns: StateFlow<List<Turn>> = _turns
    val doneTasks: StateFlow<Set<String>> = _doneTasks
    val sections: StateFlow<FeedSections> = _sections
    val ledger: StateFlow<List<LedgerLine>?> = _ledger
    val diagnostics: StateFlow<String?> = _diagnostics
    val message: StateFlow<String?> = _message
    val busy: StateFlow<Boolean> = _busy
    val model: StateFlow<ModelState> = graph.modelHost.state
    val supply: StateFlow<SupplyState> = graph.supply.state
    val reminderServices: ReminderServices = graph.reminderServices
    private val _lastLoadSeconds = MutableStateFlow<Long?>(null)
    val lastLoadSeconds: StateFlow<Long?> = _lastLoadSeconds

    private val _draft = MutableStateFlow<String?>(null)
    private val talk = TalkSession(
        VoiceRecorder(), graph.voice, scope, _busy, { LocalDateTime.now() }, { _message.value = it }, ::voiceMessage, SystemClock::elapsedRealtime,
    ) { millis, audioMillis, result ->
        val speechMillis = (result as? VoiceResult.Heard)?.speechMillis ?: -1L
        Log.i("BheemVoice", "release to result: $millis ms, $audioMillis ms of audio, speech step $speechMillis ms")
    }

    val voice: StateFlow<VoiceState> = talk.state
    val draft: StateFlow<String?> = _draft

    private var submitJob: Job? = null

    init {
        scope.launch { _lastLoadSeconds.value = withContext(Dispatchers.IO) { graph.lastLoadSeconds } }
        scope.launch {
            withContext(Dispatchers.IO) {
                BriefAlarm.schedule(graph.appContext)
                if (BriefTime.isDue(LocalDateTime.now())) BriefService.composeAndStore(graph.storage, LocalDateTime.now())
            }
            graph.storage.changes.onStart { emit(Unit) }.collectLatest {
                thread.reload()
                _turns.value = thread.turns()
                _doneTasks.value = withContext(Dispatchers.IO) { graph.memory.ofKind(RecordKind.TASK).filter { it.doneAt != null }.map { it.id }.toSet() }
                showFeed()
            }
        }
        scope.launch {
            graph.modelHost.state.collect { state ->
                if (state is ModelState.Ready) _lastLoadSeconds.value = withContext(Dispatchers.IO) { graph.lastLoadSeconds }
            }
        }
    }

    private suspend fun showFeed() {
        while (true) {
            val now = LocalDateTime.now()
            val items = withContext(Dispatchers.Default) { Feed.items(graph.feed.todayRows(FEED_ROWS), now, AppGraph.UNDO_WINDOW_SECONDS) }
            _sections.value = FeedSections.of(items, now)
            if (items.none { it.canUndo }) return
            delay((AppGraph.UNDO_WINDOW_SECONDS + 1) * 1000)
        }
    }

    fun submit(text: String) {
        if (text.isBlank() || _busy.value) return
        submitJob = scope.launch {
            _busy.value = true
            _message.value = null
            try {
                val now = LocalDateTime.now()
                val typed = text.trim()
                add(Turn.User(typed, spoken = false, now))
                val waiting = pendingClarify
                pendingClarify = null
                when {
                    FollowUp.isConfirmation(typed) -> confirm(now)
                    waiting != null && Clarifier.combine(waiting, typed) != null -> record(graph.pipeline.handle(Clarifier.combine(waiting, typed)!!, now), now)
                    else -> record(graph.pipeline.handle(typed, now), now)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    private fun add(turn: Turn) {
        thread.add(turn)
        _turns.value = thread.turns()
    }

    private var pendingClarify: String? = null

    /** "yes" and its relatives approve what is waiting: the last plan, or the one card on the screen. Anything unclear is left to a tap. */
    private suspend fun confirm(now: LocalDateTime) {
        val last = _turns.value.dropLast(1).lastOrNull { it !is Turn.User }
        val waiting = _sections.value.needsYou.filter { it.status == FeedStatus.PENDING }
        when {
            last is Turn.Plan && last.steps.any { id -> waiting.any { it.id == id } } -> runPlan(last.steps.filter { id -> waiting.any { it.id == id } }, now)
            waiting.size == 1 -> act { graph.coordinator.approve(waiting.single().id, now) }
            waiting.isEmpty() -> add(Turn.Notice("There is nothing waiting for your OK.", now))
            else -> add(Turn.Notice("More than one card is waiting. Tap OK on the one you mean.", now))
        }
    }

    fun doAll(steps: List<String>) {
        scope.launch { runPlan(steps, LocalDateTime.now()) }
    }

    private suspend fun runPlan(steps: List<String>, now: LocalDateTime) {
        val runner = PlanRunner(
            approve = { id -> graph.coordinator.approve(id, LocalDateTime.now()) },
            isHandOff = { id -> graph.proposals.get(id)?.action?.action in HAND_OFFS },
        )
        val result = runner.approveAll(steps)
        val left = steps.size - result.done.size
        val text = when {
            result.stoppedAt == null -> "Done: ${result.done.size} of ${steps.size}."
            else -> "Done ${result.done.size} of ${steps.size}. $left still wait for your OK (${result.reason})."
        }
        add(Turn.Notice(text, now))
    }

    private fun record(outcome: Outcome, now: LocalDateTime) {
        when (outcome) {
            is Outcome.Plan -> add(Turn.Plan(PLAN_TITLE, outcome.cards.map { it.proposalId }, outcome.skipped, now))
            is Outcome.Clarify -> {
                pendingClarify = outcome.original
                add(Turn.Notice(outcome.question, now))
            }
            is Outcome.Card -> Unit
            is Outcome.Question -> graph.recall.answer(outcome.query, now).let { add(Turn.Answer(it.text, it.citations, now)) }
            Outcome.Nothing -> add(Turn.Notice("I can't help with that yet. I can set reminders, tasks, notes, calendar events and draft messages.", now))
            is Outcome.Refused -> add(Turn.Notice("I could not do that: ${sentence(outcome.reason)}", now))
            is Outcome.ModelUnavailable ->
                add(Turn.Notice("${outcome.reason.replaceFirstChar { it.uppercase() }}. Until the model is ready I can still do: remind me to ... at ..., note ..., task ...", now))
        }
    }

    private fun voiceMessage(result: VoiceResult): String? = when (result) {
        is VoiceResult.Heard -> {
            val now = LocalDateTime.now()
            add(Turn.User(result.transcript, spoken = true, now))
            record(result.outcome, now)
            null
        }
        VoiceResult.NothingHeard -> "I did not catch that. Try again."
        is VoiceResult.Unavailable -> "Voice needs the language model: ${sentence(result.reason)}"
    }

    fun startListening() = talk.start()

    fun stopListening() = talk.stop()

    fun cancelListening() = talk.cancel()

    fun say(text: String) {
        _message.value = text
    }

    fun editHeard(item: FeedItem) {
        val text = item.heardAs ?: return
        dismiss(item.id)
        _draft.value = text
    }

    fun draftTaken() {
        _draft.value = null
    }

    internal suspend fun heardFromBytes(wav: ByteArray): VoiceResult = graph.voice.handle(wav, LocalDateTime.now())

    internal suspend fun dismissQuietly(id: String) {
        graph.coordinator.dismiss(id, LocalDateTime.now())
    }

    fun refreshFeed() {
        scope.launch {
            val now = LocalDateTime.now()
            _sections.value = FeedSections.of(withContext(Dispatchers.Default) { Feed.items(graph.feed.todayRows(FEED_ROWS), now, AppGraph.UNDO_WINDOW_SECONDS) }, now)
        }
    }

    private var diagnosticsJob: Job? = null

    /** An empty report means the diagnostics could not be read. */
    fun loadDiagnostics() {
        diagnosticsJob?.cancel()
        _diagnostics.value = null
        diagnosticsJob = scope.launch {
            _diagnostics.value = withContext(Dispatchers.IO) {
                runCatching { Diagnostics.report(Diagnostics.refusals(graph.storage.ledger)) }.getOrDefault("")
            }
        }
    }

    fun loadLedger() {
        _ledger.value = null
        scope.launch { _ledger.value = withContext(Dispatchers.IO) { LedgerLines.of(graph.feed.rows(LEDGER_LIMIT)) } }
    }

    fun retryModel() {
        scope.launch {
            runCatching {
                graph.modelHost.acquire()
                graph.modelHost.release()
            }
        }
    }

    fun getModel() = graph.supply.start()

    fun useMobileData() = graph.supply.useCellular()

    fun cancelDownload() = graph.supply.cancel()

    fun refreshSupply() = graph.supply.refresh()

    fun cancelSubmit() {
        submitJob?.cancel()
        _busy.value = false
    }

    fun approve(id: String) = act { graph.coordinator.approve(id, LocalDateTime.now()) }

    fun undo(id: String) = act { graph.coordinator.undo(id, LocalDateTime.now()) }

    fun completeTask(id: String) {
        scope.launch(Dispatchers.IO) { graph.memory.markDone(id, LocalDateTime.now()) }
    }

    fun reopenTask(id: String) {
        scope.launch(Dispatchers.IO) { graph.memory.markDone(id, null) }
    }

    fun dismiss(id: String) {
        scope.launch { graph.coordinator.dismiss(id, LocalDateTime.now()) }
    }

    suspend fun close() {
        talk.cancel()
        scope.cancel()
        graph.modelHost.shutdown()
        graph.close()
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun act(block: suspend () -> ApprovalResult) {
        scope.launch {
            _message.value = when (val r = block()) {
                ApprovalResult.Executed, ApprovalResult.Undone -> null
                is ApprovalResult.Failed -> "That did not work: ${sentence(r.reason)}"
                is ApprovalResult.Refused -> if (r.reason.startsWith(ALREADY_DONE)) null else "Not done: ${sentence(r.reason)}"
            }
        }
    }

    private fun sentence(reason: String) = if (reason.endsWith(".")) reason else "$reason."

    private companion object {
        const val FEED_ROWS = 100
        const val PLAN_TITLE = "plan"
        val HAND_OFFS = setOf("CreateCalendarEvent", "DraftMessage", "PlaceCall", "OpenRoute")
        const val ALREADY_DONE = "proposal is already"
    }
}
