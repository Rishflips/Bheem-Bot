package app.bheem.core.proposal

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.RiskLevel
import java.time.LocalDateTime

data class StoredProposal(
    val id: String,
    val action: ProposedAction,
    val level: RiskLevel,
    val requiresTap: Boolean,
    val createdAt: LocalDateTime,
    val userText: String,
    val undoToken: String?,
)

interface ProposalStore {
    fun put(proposal: StoredProposal)

    fun get(id: String): StoredProposal?

    fun all(): List<StoredProposal>
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
    override fun all(): List<StoredProposal> = items.values.toList()
}

sealed interface ExecutionResult {
    data class Done(val undoToken: String?) : ExecutionResult

    data class Failed(val reason: String) : ExecutionResult
}

interface ActionExecutor {
    val handles: String

    suspend fun execute(action: ProposedAction): ExecutionResult

    suspend fun undo(action: ProposedAction, undoToken: String?): Boolean
}

sealed interface ApprovalResult {
    data object Executed : ApprovalResult

    data object Undone : ApprovalResult

    data class Failed(val reason: String) : ApprovalResult

    data class Refused(val reason: String) : ApprovalResult
}
