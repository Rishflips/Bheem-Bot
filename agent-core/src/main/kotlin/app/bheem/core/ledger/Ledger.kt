package app.bheem.core.ledger

import app.bheem.core.proposal.LockingTransactionRunner
import app.bheem.core.proposal.TransactionRunner
import java.time.LocalDateTime

enum class LedgerEvent { READ, PROPOSED, REJECTED, APPROVED, EXECUTED, FAILED, UNDONE, DISMISSED }

data class NewLedgerEntry(
    val at: LocalDateTime,
    val proposalId: String,
    val event: LedgerEvent,
    val action: String,
    val source: String,
    val detail: String,
)

data class LedgerEntry(
    val id: Long,
    val at: LocalDateTime,
    val proposalId: String,
    val event: LedgerEvent,
    val action: String,
    val source: String,
    val detail: String,
)

interface LedgerStore {
    fun append(entry: NewLedgerEntry): LedgerEntry

    fun history(proposalId: String): List<LedgerEntry>

    fun lastEvent(proposalId: String): LedgerEntry?

    fun openProposalIds(): List<String>
}

class InMemoryLedgerStore(firstId: Long = 1) : LedgerStore {
    private val entries = mutableListOf<LedgerEntry>()
    private var next = firstId

    @Synchronized
    override fun append(entry: NewLedgerEntry): LedgerEntry {
        val stored = LedgerEntry(next++, entry.at, entry.proposalId, entry.event, entry.action, entry.source, entry.detail)
        entries += stored
        return stored
    }

    @Synchronized
    override fun history(proposalId: String): List<LedgerEntry> = entries.filter { it.proposalId == proposalId }

    @Synchronized
    override fun lastEvent(proposalId: String): LedgerEntry? = entries.lastOrNull { it.proposalId == proposalId }

    @Synchronized
    override fun openProposalIds(): List<String> =
        entries.groupBy { it.proposalId }.filterValues { it.last().event == LedgerEvent.PROPOSED }.keys.toList()

    @Synchronized
    fun all(): List<LedgerEntry> = entries.toList()
}

sealed interface LedgerResult {
    data class Recorded(val entry: LedgerEntry) : LedgerResult

    data class Refused(val reason: String) : LedgerResult
}

class Ledger(private val store: LedgerStore, private val tx: TransactionRunner = LockingTransactionRunner()) {
    fun record(
        proposalId: String,
        event: LedgerEvent,
        at: LocalDateTime,
        detail: String = "",
        action: String? = null,
        source: String? = null,
    ): LedgerResult = tx.run { recordInTransaction(proposalId, event, at, detail, action, source) }

    private fun recordInTransaction(
        proposalId: String,
        event: LedgerEvent,
        at: LocalDateTime,
        detail: String,
        action: String?,
        source: String?,
    ): LedgerResult {
        val last = store.lastEvent(proposalId)
        val fixedAction: String
        val fixedSource: String
        if (last == null) {
            if (event !in firstEvents) return LedgerResult.Refused("first entry must be PROPOSED, REJECTED or READ, not $event")
            if (action.isNullOrBlank() || source.isNullOrBlank()) return LedgerResult.Refused("first entry needs an action and source")
            fixedAction = action
            fixedSource = source
        } else {
            if (event !in next.getValue(last.event)) return LedgerResult.Refused("$event is not allowed after ${last.event}")
            fixedAction = last.action
            fixedSource = last.source
        }
        return LedgerResult.Recorded(store.append(NewLedgerEntry(at, proposalId, event, fixedAction, fixedSource, detail)))
    }

    fun history(proposalId: String): List<LedgerEntry> = store.history(proposalId)

    fun stateOf(proposalId: String): LedgerEvent? = store.lastEvent(proposalId)?.event

    fun openProposals(): List<String> = store.openProposalIds()

    fun canUndo(proposalId: String, now: LocalDateTime, windowSeconds: Long): Boolean {
        val last = store.lastEvent(proposalId) ?: return false
        return last.event == LedgerEvent.EXECUTED && !now.isAfter(last.at.plusSeconds(windowSeconds))
    }

    private companion object {
        val firstEvents = setOf(LedgerEvent.PROPOSED, LedgerEvent.REJECTED, LedgerEvent.READ)
        val next: Map<LedgerEvent, Set<LedgerEvent>> = mapOf(
            LedgerEvent.PROPOSED to setOf(LedgerEvent.APPROVED, LedgerEvent.DISMISSED),
            LedgerEvent.APPROVED to setOf(LedgerEvent.EXECUTED, LedgerEvent.FAILED),
            LedgerEvent.FAILED to setOf(LedgerEvent.APPROVED, LedgerEvent.DISMISSED),
            LedgerEvent.EXECUTED to setOf(LedgerEvent.UNDONE),
            LedgerEvent.REJECTED to emptySet(),
            LedgerEvent.READ to emptySet(),
            LedgerEvent.DISMISSED to emptySet(),
            LedgerEvent.UNDONE to emptySet(),
        )
    }
}
