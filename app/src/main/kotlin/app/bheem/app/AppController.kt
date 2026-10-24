package app.bheem.app

import app.bheem.core.feed.Feed
import app.bheem.core.feed.FeedItem
import app.bheem.core.memory.Ask
import app.bheem.core.pipeline.Outcome
import app.bheem.core.proposal.ApprovalResult
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class AppController(private val graph: AppGraph) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _feed = MutableStateFlow<List<FeedItem>>(emptyList())
    private val _message = MutableStateFlow<String?>(null)
    private val _busy = MutableStateFlow(false)

    val feed: StateFlow<List<FeedItem>> = _feed
    val message: StateFlow<String?> = _message
    val busy: StateFlow<Boolean> = _busy

    init {
        refresh()
        graph.storage.changes.onEach { refresh() }.launchIn(scope)
    }

    fun refresh() {
        scope.launch {
            val items = Feed.items(graph.feed.rows(FEED_ROWS), LocalDateTime.now(), AppGraph.UNDO_WINDOW_SECONDS)
            _feed.value = items
            if (items.any { it.canUndo }) {
                delay((AppGraph.UNDO_WINDOW_SECONDS + 1) * 1000)
                refresh()
            }
        }
    }

    fun submit(text: String) {
        if (text.isBlank() || _busy.value) return
        if (!graph.modelPresent) {
            _message.value = "The model file is missing. Put ${AppGraph.MODEL_FILE} in ${graph.modelFile.parent}."
            return
        }
        scope.launch {
            _busy.value = true
            _message.value = null
            val now = LocalDateTime.now()
            _message.value = when (val outcome = graph.pipeline.handle(text.trim(), now)) {
                is Outcome.Card -> null
                is Outcome.Question -> Ask.answer(outcome.query, graph.memory, now).text
                Outcome.Nothing -> "I can't help with that yet. I can set reminders, tasks, notes, calendar events and draft messages."
                is Outcome.Refused -> "I could not do that: ${outcome.reason}."
            }
            _busy.value = false
            refresh()
        }
    }

    fun approve(id: String) = act { graph.coordinator.approve(id, LocalDateTime.now()) }

    fun undo(id: String) = act { graph.coordinator.undo(id, LocalDateTime.now()) }

    fun dismiss(id: String) {
        graph.coordinator.dismiss(id, LocalDateTime.now())
        refresh()
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
            refresh()
        }
    }

    private companion object {
        const val FEED_ROWS = 100
    }
}
