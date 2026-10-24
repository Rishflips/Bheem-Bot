package app.bheem.core.ledger

import java.time.LocalDateTime

enum class LedgerEvent { READ, PROPOSED, REJECTED, APPROVED, EXECUTED, FAILED, UNDONE, DISMISSED }

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
    fun append(entry: LedgerEntry)

    fun all(): List<LedgerEntry>
}

class InMemoryLedgerStore : LedgerStore {
    private val entries = mutableListOf<LedgerEntry>()

    @Synchronized
    override fun append(entry: LedgerEntry) {
        entries += entry
    }

    @Synchronized
    override fun all(): List<LedgerEntry> = entries.toList()
}

sealed interface LedgerResult {
    data class Recorded(val entry: LedgerEntry) : LedgerResult

    data class Refused(val reason: String) : LedgerResult
}

class Ledger(private val store: LedgerStore) {
    @Synchronized
    fun record(
        proposalId: String,
        event: LedgerEvent,
        at: LocalDateTime,
        detail: String = "",
        action: String? = null,
        source: String? = null,
    ): LedgerResult {
        val history = historyOf(proposalId)
        val last = history.lastOrNull()
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
        val entry = LedgerEntry(store.all().size + 1L, at, proposalId, event, fixedAction, fixedSource, detail)
        store.append(entry)
        return LedgerResult.Recorded(entry)
    }

    fun history(proposalId: String): List<LedgerEntry> = historyOf(proposalId)

    fun stateOf(proposalId: String): LedgerEvent? = historyOf(proposalId).lastOrNull()?.event

    fun openProposals(): List<String> =
        store.all().groupBy { it.proposalId }.filterValues { it.last().event == LedgerEvent.PROPOSED }.keys.toList()

    fun canUndo(proposalId: String, now: LocalDateTime, windowSeconds: Long): Boolean {
        val last = historyOf(proposalId).lastOrNull() ?: return false
        return last.event == LedgerEvent.EXECUTED && !now.isAfter(last.at.plusSeconds(windowSeconds))
    }

    private fun historyOf(proposalId: String) = store.all().filter { it.proposalId == proposalId }

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
