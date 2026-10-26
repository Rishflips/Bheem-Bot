package app.bheem.app

import app.bheem.core.feed.Feed
import app.bheem.core.feed.FeedSections
import app.bheem.core.feed.LedgerLine
import app.bheem.core.feed.LedgerLines
import app.bheem.core.feed.FeedItem
import app.bheem.core.memory.Ask
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

class AppController(private val graph: AppGraph) {
    private val failures = CoroutineExceptionHandler { _, error ->
        _message.value = "Something went wrong: ${error.message ?: error::class.java.simpleName}."
        _busy.value = false
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + failures)
    private val _sections = MutableStateFlow(FeedSections(emptyList(), emptyList(), emptyList()))
    private val _ledger = MutableStateFlow<List<LedgerLine>>(emptyList())
    private val _message = MutableStateFlow<String?>(null)
    private val _busy = MutableStateFlow(false)

    val sections: StateFlow<FeedSections> = _sections
    val ledger: StateFlow<List<LedgerLine>> = _ledger
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
    ) { millis, audioMillis -> Log.i("BheemVoice", "release to result: $millis ms, $audioMillis ms of audio") }

    val voice: StateFlow<VoiceState> = talk.state
    val draft: StateFlow<String?> = _draft

    private var submitJob: Job? = null

    init {
        scope.launch { _lastLoadSeconds.value = withContext(Dispatchers.IO) { graph.lastLoadSeconds } }
        scope.launch { graph.storage.changes.onStart { emit(Unit) }.collectLatest { showFeed() } }
        scope.launch {
            graph.modelHost.state.collect { state ->
                if (state is ModelState.Ready) _lastLoadSeconds.value = withContext(Dispatchers.IO) { graph.lastLoadSeconds }
            }
        }
    }

    private suspend fun showFeed() {
        while (true) {
            val now = LocalDateTime.now()
            val items = withContext(Dispatchers.Default) { Feed.items(graph.feed.rows(FEED_ROWS), now, AppGraph.UNDO_WINDOW_SECONDS) }
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
                _message.value = messageFor(graph.pipeline.handle(text.trim(), now), now)
            } finally {
                _busy.value = false
            }
        }
    }

    private fun messageFor(outcome: Outcome, now: LocalDateTime): String? = when (outcome) {
        is Outcome.Card -> null
        is Outcome.Question -> Ask.answer(outcome.query, graph.memory, now).text
        Outcome.Nothing -> "I can't help with that yet. I can set reminders, tasks, notes, calendar events and draft messages."
        is Outcome.Refused -> "I could not do that: ${sentence(outcome.reason)}"
        is Outcome.ModelUnavailable -> "${outcome.reason.replaceFirstChar { it.uppercase() }}. Until the model is ready I can still do: remind me to ... at ..., note ..., task ..."
    }

    private fun voiceMessage(result: VoiceResult): String? = when (result) {
        is VoiceResult.Heard -> {
            val now = LocalDateTime.now()
            messageFor(result.outcome, now)?.let { "Heard: \"${result.transcript}\". $it" }
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
            _sections.value = FeedSections.of(withContext(Dispatchers.Default) { Feed.items(graph.feed.rows(FEED_ROWS), now, AppGraph.UNDO_WINDOW_SECONDS) }, now)
        }
    }

    fun loadLedger() {
        scope.launch { _ledger.value = withContext(Dispatchers.IO) { LedgerLines.of(graph.feed.rows(LEDGER_ROWS)) } }
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
                is ApprovalResult.Refused -> "Not done: ${sentence(r.reason)}"
            }
        }
    }

    private fun sentence(reason: String) = if (reason.endsWith(".")) reason else "$reason."

    private companion object {
        const val FEED_ROWS = 100
        const val LEDGER_ROWS = 300
    }
}
