package app.bheem.storage

import android.content.ContentValues
import app.bheem.core.action.ProposedAction
import app.bheem.core.feed.FeedRow
import app.bheem.core.feed.FeedSource
import app.bheem.core.gate.Provenance
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.LedgerEntry
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.ledger.LedgerStore
import app.bheem.core.ledger.NewLedgerEntry
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.MemoryStore
import app.bheem.core.memory.RecordKind
import app.bheem.core.proposal.ProposalStore
import app.bheem.core.proposal.StoredProposal
import app.bheem.core.proposal.TransactionRunner
import app.bheem.core.reminder.Reminder
import app.bheem.core.reminder.ReminderState
import app.bheem.core.reminder.ReminderStore
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableSharedFlow
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONObject

internal class Db(private val helper: net.zetetic.database.sqlcipher.SQLiteOpenHelper) {
    @Volatile
    var closed = false
        private set

    val writableDatabase: SQLiteDatabase get() = open().writableDatabase

    val readableDatabase: SQLiteDatabase get() = open().readableDatabase

    fun close() {
        closed = true
        helper.close()
    }

    private fun open(): net.zetetic.database.sqlcipher.SQLiteOpenHelper {
        check(!closed) { "the database is closed" }
        return helper
    }
}

internal class SqlTransactionRunner(
    private val database: () -> SQLiteDatabase,
    private val signal: MutableSharedFlow<Unit>,
) : TransactionRunner {
    private val depth = ThreadLocal.withInitial { 0 }
    private val poisoned = ThreadLocal.withInitial { false }

    override fun <T> run(block: () -> T): T {
        val db = database()
        db.beginTransaction()
        depth.set(depth.get() + 1)
        var committed = false
        try {
            val result = block()
            db.setTransactionSuccessful()
            committed = true
            return result
        } finally {
            depth.set(depth.get() - 1)
            try {
                db.endTransaction()
            } finally {
                if (!committed) poisoned.set(true)
                if (depth.get() == 0) {
                    val clean = committed && !poisoned.get()
                    poisoned.set(false)
                    if (clean) signal.tryEmit(Unit)
                    if (committed && !clean) throw IllegalStateException("a nested transaction failed, so the whole transaction was rolled back")
                }
            }
        }
    }
}

private fun android.database.Cursor.string(name: String): String = getString(getColumnIndexOrThrow(name))

private fun android.database.Cursor.stringOrNull(name: String): String? = getString(getColumnIndexOrThrow(name))

private fun android.database.Cursor.long(name: String): Long = getLong(getColumnIndexOrThrow(name))

internal class SqlLedgerStore(private val helper: Db, private val runner: SqlTransactionRunner) : LedgerStore {
    override fun append(entry: NewLedgerEntry): LedgerEntry = runner.run {
        val zone = ZoneId.systemDefault()
        val values = ContentValues().apply {
            put("at_epoch", Times.toEpochMillis(entry.at, zone))
            put("zone_id", zone.id)
            put("proposal_id", entry.proposalId)
            put("event", entry.event.name)
            put("action", entry.action)
            put("source", entry.source)
            put("detail", entry.detail)
        }
        val id = helper.writableDatabase.insertOrThrow("ledger", null, values)
        LedgerEntry(id, entry.at, entry.proposalId, entry.event, entry.action, entry.source, entry.detail)
    }

    override fun history(proposalId: String): List<LedgerEntry> =
        helper.readableDatabase.query("ledger", null, "proposal_id = ?", arrayOf(proposalId), null, null, "id").use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    override fun lastEvent(proposalId: String): LedgerEntry? =
        helper.readableDatabase.query("ledger", null, "proposal_id = ?", arrayOf(proposalId), null, null, "id DESC", "1").use { c -> if (c.moveToFirst()) read(c) else null }

    override fun openProposalIds(): List<String> = helper.readableDatabase.rawQuery(
        "SELECT p.id FROM proposals p WHERE (SELECT event FROM ledger WHERE proposal_id = p.id ORDER BY id DESC LIMIT 1) = 'PROPOSED' ORDER BY p.created_at_epoch",
        emptyArray(),
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    override fun approvedProposalIds(): List<String> = helper.readableDatabase.rawQuery(
        "SELECT p.id FROM proposals p WHERE (SELECT event FROM ledger WHERE proposal_id = p.id ORDER BY id DESC LIMIT 1) = 'APPROVED' ORDER BY p.created_at_epoch",
        emptyArray(),
    ).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    override fun recent(event: LedgerEvent, limit: Int): List<LedgerEntry> =
        helper.readableDatabase.query("ledger", null, "event = ?", arrayOf(event.name), null, null, "id DESC", limit.toString()).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    private fun read(c: android.database.Cursor) = LedgerEntry(
        c.long("id"), Times.fromEpoch(c.long("at_epoch"), c.string("zone_id")), c.string("proposal_id"),
        LedgerEvent.valueOf(c.string("event")), c.string("action"), c.string("source"), c.string("detail"),
    )
}

internal fun readProposal(c: android.database.Cursor, prefix: String = ""): StoredProposal {
    val json = JSONObject(c.string("${prefix}args"))
    val args = json.keys().asSequence().associateWith { json.getString(it) }
    return StoredProposal(
        c.string("${prefix}id"), ProposedAction(c.string("${prefix}action"), args), RiskLevel.valueOf(c.string("${prefix}level")),
        c.getInt(c.getColumnIndexOrThrow("${prefix}requires_tap")) == 1,
        Times.fromEpoch(c.long("${prefix}created_at_epoch"), c.string("${prefix}zone_id")), c.string("${prefix}user_text"), c.stringOrNull("${prefix}undo_token"),
        Provenance.valueOf(c.string("${prefix}provenance")), c.string("${prefix}source"), c.string("${prefix}prompt_version"), c.string("${prefix}model_id"), c.string("${prefix}backend"),
    )
}

internal class SqlProposalStore(private val helper: Db, private val runner: SqlTransactionRunner) : ProposalStore {
    override fun put(proposal: StoredProposal) {
        runner.run {
            val zone = ZoneId.systemDefault()
            val values = ContentValues().apply {
                put("id", proposal.id)
                put("action", proposal.action.action)
                put("args", JSONObject(proposal.action.args).toString())
                put("level", proposal.level.name)
                put("requires_tap", if (proposal.requiresTap) 1 else 0)
                put("created_at_epoch", Times.toEpochMillis(proposal.createdAt, zone))
                put("zone_id", zone.id)
                put("user_text", proposal.userText)
                put("undo_token", proposal.undoToken)
                put("provenance", proposal.provenance.name)
                put("source", proposal.source)
                put("prompt_version", proposal.promptVersion)
                put("model_id", proposal.modelId)
                put("backend", proposal.backend)
            }
            helper.writableDatabase.insertWithOnConflict("proposals", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    override fun get(id: String): StoredProposal? =
        helper.readableDatabase.query("proposals", null, "id = ?", arrayOf(id), null, null, null).use { c -> if (c.moveToFirst()) readProposal(c) else null }
}

internal class SqlFeedSource(private val helper: Db) : FeedSource {
    override fun rows(limit: Int): List<FeedRow> = query("", arrayOf(limit.toString()))

    override fun rowsFor(action: String, limit: Int): List<FeedRow> = query("WHERE p.action = ?", arrayOf(action, limit.toString()))

    private fun query(where: String, args: Array<String>): List<FeedRow> = helper.readableDatabase.rawQuery(
        """
        SELECT p.id, p.action, p.args, p.level, p.requires_tap, p.created_at_epoch, p.zone_id, p.user_text, p.undo_token,
               p.provenance, p.source, p.prompt_version, p.model_id, p.backend,
               l.id AS l_id, l.at_epoch AS l_at, l.zone_id AS l_zone, l.event AS l_event, l.action AS l_action, l.source AS l_source, l.detail AS l_detail
        FROM proposals p JOIN ledger l ON l.id = (SELECT MAX(id) FROM ledger WHERE proposal_id = p.id)
        $where
        ORDER BY p.created_at_epoch DESC LIMIT ?
        """.trimIndent(),
        args,
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                val proposal = readProposal(c)
                val last = LedgerEntry(
                    c.long("l_id"), Times.fromEpoch(c.long("l_at"), c.string("l_zone")), proposal.id, LedgerEvent.valueOf(c.string("l_event")),
                    c.string("l_action"), c.string("l_source"), c.string("l_detail"),
                )
                add(FeedRow(proposal, last))
            }
        }
    }
}

internal class SqlMemoryStore(private val helper: Db, private val runner: SqlTransactionRunner) : MemoryStore {
    override fun add(record: MemoryRecord) {
        runner.run {
            val zone = ZoneId.systemDefault()
            val values = ContentValues().apply {
                put("id", record.id)
                put("kind", record.kind.name)
                put("text", record.text)
                put("created_at_epoch", Times.toEpochMillis(record.createdAt, zone))
                put("zone_id", zone.id)
                put("merchant", record.merchant)
                put("amount", record.amount)
                put("direction", record.direction)
            }
            helper.writableDatabase.insertWithOnConflict("memory", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    override fun remove(id: String): Boolean = runner.run { helper.writableDatabase.delete("memory", "id = ?", arrayOf(id)) > 0 }

    override fun ofKind(kind: RecordKind): List<MemoryRecord> =
        helper.readableDatabase.query("memory", null, "kind = ?", arrayOf(kind.name), null, null, "created_at_epoch").use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    override fun search(query: String, limit: Int): List<MemoryRecord> {
        val match = FtsQuery.build(query) ?: return emptyList()
        return helper.readableDatabase.rawQuery(
            "SELECT m.* FROM memory_fts f JOIN memory m ON m.seq = f.rowid WHERE memory_fts MATCH ? ORDER BY bm25(memory_fts) LIMIT ?",
            arrayOf(match, limit.toString()),
        ).use { c -> buildList { while (c.moveToNext()) add(read(c)) } }
    }

    private fun read(c: android.database.Cursor) = MemoryRecord(
        c.string("id"), RecordKind.valueOf(c.string("kind")), c.string("text"), Times.fromEpoch(c.long("created_at_epoch"), c.string("zone_id")),
        c.stringOrNull("merchant"), c.stringOrNull("amount"), c.stringOrNull("direction"),
    )
}

internal class SqlReminderStore(private val helper: Db, private val runner: SqlTransactionRunner) : ReminderStore {
    override fun add(proposalId: String, text: String, fireAtEpochMs: Long, zoneId: String): Reminder = runner.run {
        val values = ContentValues().apply {
            put("proposal_id", proposalId)
            put("text", text)
            put("fire_at_epoch", fireAtEpochMs)
            put("zone_id", zoneId)
            put("state", ReminderState.SCHEDULED.name)
        }
        val id = helper.writableDatabase.insertOrThrow("reminders", null, values)
        Reminder(id, proposalId, text, fireAtEpochMs, zoneId, ReminderState.SCHEDULED)
    }

    override fun byProposal(proposalId: String): Reminder? =
        helper.readableDatabase.query("reminders", null, "proposal_id = ?", arrayOf(proposalId), null, null, "id DESC", "1").use { c -> if (c.moveToFirst()) read(c) else null }

    override fun get(id: Long): Reminder? =
        helper.readableDatabase.query("reminders", null, "id = ?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) read(c) else null }

    override fun scheduled(): List<Reminder> = inState(ReminderState.SCHEDULED)

    override fun missed(): List<Reminder> = inState(ReminderState.MISSED)

    private fun inState(state: ReminderState): List<Reminder> =
        helper.readableDatabase.query("reminders", null, "state = ?", arrayOf(state.name), null, null, "fire_at_epoch ASC, id ASC").use { c ->
            buildList { while (c.moveToNext()) add(read(c)) }
        }

    override fun transitionAll(from: ReminderState, to: ReminderState) {
        runner.run {
            val values = ContentValues().apply { put("state", to.name) }
            helper.writableDatabase.update("reminders", values, "state = ?", arrayOf(from.name))
        }
    }

    override fun transition(id: Long, from: ReminderState, to: ReminderState): Boolean = runner.run {
        val values = ContentValues().apply { put("state", to.name) }
        helper.writableDatabase.update("reminders", values, "id = ? AND state = ?", arrayOf(id.toString(), from.name)) > 0
    }

    override fun reschedule(id: Long, fireAtEpochMs: Long, zoneId: String) {
        runner.run {
            val values = ContentValues().apply {
                put("fire_at_epoch", fireAtEpochMs)
                put("zone_id", zoneId)
            }
            helper.writableDatabase.update("reminders", values, "id = ? AND state = ?", arrayOf(id.toString(), ReminderState.SCHEDULED.name))
        }
    }

    private fun read(c: android.database.Cursor) = Reminder(
        c.long("id"), c.string("proposal_id"), c.string("text"), c.long("fire_at_epoch"), c.string("zone_id"), ReminderState.valueOf(c.string("state")),
    )
}
