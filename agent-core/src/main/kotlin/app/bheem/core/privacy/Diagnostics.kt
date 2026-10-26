package app.bheem.core.privacy

import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.ledger.LedgerStore
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class RefusalLine(val at: LocalDateTime, val action: String, val source: String, val reason: String)

/**
 * Local diagnostics: the latest refusals, with when, what kind of action, where it came from and why. Nothing a person said is
 * kept here, and nothing is sent anywhere; the person can read the report and choose to share it.
 */
object Diagnostics {
    const val LIMIT = 50
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun refusals(store: LedgerStore, limit: Int = LIMIT): List<RefusalLine> =
        store.recent(LedgerEvent.REJECTED, limit).map { RefusalLine(it.at, Redact.reason(it.action), Redact.reason(it.source), Redact.reason(it.detail)) }

    fun report(lines: List<RefusalLine>): String {
        val header = "Bheem diagnostics. Refusals are listed newest first. This was made on this phone and is never sent anywhere; you decide whether to share it."
        if (lines.isEmpty()) return "$header\n\nNo refusals recorded."
        return header + "\n\n" + lines.joinToString("\n") { "${it.at.format(stamp)} | ${it.action} | ${it.source} | ${it.reason}" }
    }
}
