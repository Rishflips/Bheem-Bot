package app.bheem.core.memory

import app.bheem.core.action.ProposedAction
import app.bheem.core.proposal.ActionExecutor
import app.bheem.core.proposal.ExecutionResult
import app.bheem.core.proposal.TransactionRunner
import java.time.LocalDateTime

/** FACT is something the owner told Bheem about themselves and confirmed; GUESS is something Bheem thinks it learned and the owner has not confirmed. */
enum class RecordKind { NOTE, TASK, EXPENSE, FACT, GUESS }

data class MemoryRecord(
    val id: String,
    val kind: RecordKind,
    val text: String,
    val createdAt: LocalDateTime,
    val merchant: String? = null,
    val amount: String? = null,
    val direction: String? = null,
    val doneAt: LocalDateTime? = null,
)

interface MemoryStore {
    fun add(record: MemoryRecord)

    fun remove(id: String): Boolean

    /** Marks a task done at [at], or open again when [at] is null. False when there is no such record. */
    fun markDone(id: String, at: LocalDateTime?): Boolean

    fun ofKind(kind: RecordKind): List<MemoryRecord>

    fun search(query: String, limit: Int): List<MemoryRecord>
}

class InMemoryMemoryStore : MemoryStore {
    private val items = mutableListOf<MemoryRecord>()

    @Synchronized
    override fun add(record: MemoryRecord) {
        if (items.none { it.id == record.id }) items += record
    }

    @Synchronized
    override fun remove(id: String): Boolean = items.removeAll { it.id == id }

    @Synchronized
    override fun markDone(id: String, at: LocalDateTime?): Boolean {
        val index = items.indexOfFirst { it.id == id }
        if (index < 0) return false
        items[index] = items[index].copy(doneAt = at)
        return true
    }

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

    /** Saves a confirmed fact. When the action carries `replaces`, the old fact is removed and the new one stored in one transaction; undo puts the old one back. */
    fun fact(
        store: MemoryStore,
        newId: () -> String,
        clock: () -> LocalDateTime,
        tx: TransactionRunner = TransactionRunner.None,
    ): ActionExecutor = object : ActionExecutor {
        override val handles = "RememberFact"

        override suspend fun execute(action: ProposedAction): ExecutionResult = save(newId(), action)

        override suspend fun execute(action: ProposedAction, proposalId: String): ExecutionResult = save(proposalId, action)

        private fun save(id: String, action: ProposedAction): ExecutionResult = tx.run {
            val replaces = action.args["replaces"]?.trim()?.takeIf { it.isNotEmpty() }
            if (replaces != null) {
                val old = store.ofKind(RecordKind.FACT).filter { it.text == replaces }
                if (old.isEmpty()) return@run ExecutionResult.Failed("I no longer have that memory.")
                old.forEach { store.remove(it.id) }
            }
            store.add(MemoryRecord(id, RecordKind.FACT, action.args.getValue("text"), clock()))
            ExecutionResult.Done(id)
        }

        override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean = tx.run {
            if (undoToken == null || !store.remove(undoToken)) return@run false
            action.args["replaces"]?.trim()?.takeIf { it.isNotEmpty() }?.let { old ->
                store.add(MemoryRecord(newId(), RecordKind.FACT, old, clock()))
            }
            true
        }

        override suspend fun discard(action: ProposedAction, proposalId: String) {
            store.remove(proposalId)
        }
    }

    /** Removes the owner's confirmed facts with exactly this text, and nothing else. Undo puts the fact back. */
    fun forget(store: MemoryStore, newId: () -> String, clock: () -> LocalDateTime): ActionExecutor = object : ActionExecutor {
        override val handles = "ForgetFact"

        override suspend fun execute(action: ProposedAction): ExecutionResult {
            val text = action.args.getValue("text")
            val matches = store.ofKind(RecordKind.FACT).filter { it.text.equals(text, ignoreCase = true) }
            if (matches.isEmpty()) return ExecutionResult.Failed("I no longer have that memory.")
            matches.forEach { store.remove(it.id) }
            return ExecutionResult.Done("forgot")
        }

        override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean {
            if (undoToken != "forgot") return false
            store.add(MemoryRecord(newId(), RecordKind.FACT, action.args.getValue("text"), clock()))
            return true
        }
    }

    private class StoreExecutor(
        override val handles: String,
        private val store: MemoryStore,
        private val newId: () -> String,
        private val clock: () -> LocalDateTime,
        private val build: (String, LocalDateTime, ProposedAction) -> MemoryRecord,
    ) : ActionExecutor {
        override suspend fun execute(action: ProposedAction): ExecutionResult = save(newId(), action)

        override suspend fun execute(action: ProposedAction, proposalId: String): ExecutionResult = save(proposalId, action)

        override suspend fun discard(action: ProposedAction, proposalId: String) {
            store.remove(proposalId)
        }

        private fun save(id: String, action: ProposedAction): ExecutionResult {
            store.add(build(id, clock(), action))
            return ExecutionResult.Done(id)
        }

        override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean = undoToken != null && store.remove(undoToken)
    }
}
