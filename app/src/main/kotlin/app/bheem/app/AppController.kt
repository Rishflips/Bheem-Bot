package app.bheem.app

import app.bheem.core.feed.Feed
import app.bheem.core.feed.FeedItem
import app.bheem.core.memory.Ask
import app.bheem.core.pipeline.Outcome
import app.bheem.core.proposal.ApprovalResult
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
    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    private val _message = MutableStateFlow<String?>(null)
    private val _busy = MutableStateFlow(false)

    val feed: StateFlow<List<FeedItem>> = _feed
    val message: StateFlow<String?> = _message
    val busy: StateFlow<Boolean> = _busy
    val model: StateFlow<ModelState> = graph.modelHost.state
    val supply: StateFlow<SupplyState> = graph.supply.state
    val reminderServices: ReminderServices = graph.reminderServices
    private val _lastLoadSeconds = MutableStateFlow<Long?>(null)
    val lastLoadSeconds: StateFlow<Long?> = _lastLoadSeconds

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
            val items = withContext(Dispatchers.Default) { Feed.items(graph.feed.rows(FEED_ROWS), LocalDateTime.now(), AppGraph.UNDO_WINDOW_SECONDS) }
            _feed.value = items
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
                _message.value = when (val outcome = graph.pipeline.handle(text.trim(), now)) {
                    is Outcome.Card -> null
                    is Outcome.Question -> Ask.answer(outcome.query, graph.memory, now).text
                    Outcome.Nothing -> "I can't help with that yet. I can set reminders, tasks, notes, calendar events and draft messages."
                    is Outcome.Refused -> "I could not do that: ${outcome.reason}."
                is Outcome.ModelUnavailable -> "${outcome.reason.replaceFirstChar { it.uppercase() }}. Until the model is ready I can still do: remind me to ... at ..., note ..., task ..."
                }
            } finally {
                _busy.value = false
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
        scope.cancel()
        graph.modelHost.shutdown()
        graph.storage.close()
        graph.close()
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun act(block: suspend () -> ApprovalResult) {
        scope.launch {
            _message.value = when (val r = block()) {
                ApprovalResult.Executed, ApprovalResult.Undone -> null
                is ApprovalResult.Failed -> "That did not work: ${r.reason}."
                is ApprovalResult.Refused -> "Not done: ${r.reason}."
            }
        }
    }

    private companion object {
        const val FEED_ROWS = 100
    }
}
