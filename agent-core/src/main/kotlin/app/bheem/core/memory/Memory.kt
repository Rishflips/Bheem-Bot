package app.bheem.core.memory

import app.bheem.core.action.ProposedAction
import app.bheem.core.proposal.ActionExecutor
import app.bheem.core.proposal.ExecutionResult
import java.time.LocalDateTime

enum class RecordKind { NOTE, TASK, EXPENSE }

data class MemoryRecord(
    val id: String,
    val kind: RecordKind,
    val text: String,
    val createdAt: LocalDateTime,
    val merchant: String? = null,
    val amount: String? = null,
    val direction: String? = null,
)

interface MemoryStore {
    fun add(record: MemoryRecord)

    fun remove(id: String): Boolean

    fun ofKind(kind: RecordKind): List<MemoryRecord>

    fun search(query: String, limit: Int): List<MemoryRecord>
}

class InMemoryMemoryStore : MemoryStore {
    private val items = mutableListOf<MemoryRecord>()

    @Synchronized
    override fun add(record: MemoryRecord) {
        items += record
    }

    @Synchronized
    override fun remove(id: String): Boolean = items.removeAll { it.id == id }

    @Synchronized
    override fun ofKind(kind: RecordKind): List<MemoryRecord> = items.filter { it.kind == kind }

    @Synchronized
    override fun search(query: String, limit: Int): List<MemoryRecord> = MemorySearch.search(query, items, limit).map { it.record }

    @Synchronized
    fun all(): List<MemoryRecord> = items.toList()
}

object LocalExecutors {
    fun note(store: MemoryStore, newId: () -> String, clock: () -> LocalDateTime): ActionExecutor =
        StoreExecutor("SaveNote", store, newId, clock) { id, now, a -> MemoryRecord(id, RecordKind.NOTE, a.args.getValue("text"), now) }

    fun task(store: MemoryStore, newId: () -> String, clock: () -> LocalDateTime): ActionExecutor =
        StoreExecutor("CreateTask", store, newId, clock) { id, now, a -> MemoryRecord(id, RecordKind.TASK, a.args.getValue("text"), now) }

    fun expense(store: MemoryStore, newId: () -> String, clock: () -> LocalDateTime): ActionExecutor =
        StoreExecutor("LogExpense", store, newId, clock) { id, now, a ->
            val merchant = a.args.getValue("merchant")
            val amount = a.args.getValue("amount")
            MemoryRecord(id, RecordKind.EXPENSE, "$merchant $amount", now, merchant, amount, a.args.getValue("direction"))
        }

    private class StoreExecutor(
        override val handles: String,
        private val store: MemoryStore,
        private val newId: () -> String,
        private val clock: () -> LocalDateTime,
        private val build: (String, LocalDateTime, ProposedAction) -> MemoryRecord,
    ) : ActionExecutor {
        override suspend fun execute(action: ProposedAction): ExecutionResult {
            val id = newId()
            store.add(build(id, clock(), action))
            return ExecutionResult.Done(id)
        }

        override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean = undoToken != null && store.remove(undoToken)
    }
}
