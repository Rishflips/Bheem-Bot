package app.bheem.core.proposal

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.Provenance
import app.bheem.core.gate.RiskLevel
import java.time.LocalDateTime

data class ProposalMeta(val promptVersion: String, val modelId: String, val backend: String)

data class StoredProposal(
    val id: String,
    val action: ProposedAction,
    val level: RiskLevel,
    val requiresTap: Boolean,
    val createdAt: LocalDateTime,
    val userText: String,
    val undoToken: String?,
    val provenance: Provenance = Provenance.USER,
    val source: String = "bar",
    val promptVersion: String = "",
    val modelId: String = "",
    val backend: String = "",
)

interface ProposalStore {
    fun put(proposal: StoredProposal)

    fun get(id: String): StoredProposal?
}

interface TransactionRunner {
    fun <T> run(block: () -> T): T

    object None : TransactionRunner {
        override fun <T> run(block: () -> T): T = block()
    }
}

class LockingTransactionRunner : TransactionRunner {
    private val lock = java.util.concurrent.locks.ReentrantLock()

    override fun <T> run(block: () -> T): T {
        lock.lock()
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }
}

class InMemoryProposalStore : ProposalStore {
    private val items = linkedMapOf<String, StoredProposal>()

    @Synchronized
    override fun put(proposal: StoredProposal) {
        items[proposal.id] = proposal
    }

    @Synchronized
    override fun get(id: String): StoredProposal? = items[id]

    @Synchronized
    fun all(): List<StoredProposal> = items.values.toList()
}

sealed interface ExecutionResult {
    data class Done(val undoToken: String?, val note: String? = null) : ExecutionResult

    data class Failed(val reason: String) : ExecutionResult
}

interface ActionExecutor {
    val handles: String

    suspend fun execute(action: ProposedAction): ExecutionResult

    suspend fun execute(action: ProposedAction, proposalId: String): ExecutionResult = execute(action)

    suspend fun undo(action: ProposedAction, undoToken: String?): Boolean

    suspend fun discard(action: ProposedAction, proposalId: String) {}
}

sealed interface ApprovalResult {
    data object Executed : ApprovalResult

    data object Undone : ApprovalResult

    data class Failed(val reason: String) : ApprovalResult

    data class Refused(val reason: String) : ApprovalResult
}
