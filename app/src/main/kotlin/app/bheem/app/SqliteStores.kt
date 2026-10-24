package app.bheem.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.LedgerEntry
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.ledger.LedgerStore
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.MemoryStore
import app.bheem.core.memory.RecordKind
import app.bheem.core.proposal.ProposalStore
import app.bheem.core.proposal.StoredProposal
import java.time.LocalDateTime
import org.json.JSONObject

class BheemDb(context: Context) : SQLiteOpenHelper(context, "bheem.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE ledger (id INTEGER PRIMARY KEY, at TEXT, proposal_id TEXT, event TEXT, action TEXT, source TEXT, detail TEXT)")
        db.execSQL("CREATE TABLE proposals (id TEXT PRIMARY KEY, action TEXT, args TEXT, level TEXT, requires_tap INTEGER, created_at TEXT, user_text TEXT, undo_token TEXT)")
        db.execSQL("CREATE TABLE memory (id TEXT PRIMARY KEY, kind TEXT, text TEXT, created_at TEXT, merchant TEXT, amount TEXT, direction TEXT)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
}

private fun Cursor.str(name: String): String? = getString(getColumnIndexOrThrow(name))

class SqliteLedgerStore(private val helper: BheemDb) : LedgerStore {
    override fun append(entry: LedgerEntry) {
        val values = ContentValues().apply {
            put("id", entry.id)
            put("at", entry.at.toString())
            put("proposal_id", entry.proposalId)
            put("event", entry.event.name)
            put("action", entry.action)
            put("source", entry.source)
            put("detail", entry.detail)
        }
        helper.writableDatabase.insertOrThrow("ledger", null, values)
    }

    override fun all(): List<LedgerEntry> = helper.readableDatabase.query("ledger", null, null, null, null, null, "id").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    LedgerEntry(
                        c.getLong(c.getColumnIndexOrThrow("id")), LocalDateTime.parse(c.str("at")), c.str("proposal_id").orEmpty(),
                        LedgerEvent.valueOf(c.str("event").orEmpty()), c.str("action").orEmpty(), c.str("source").orEmpty(), c.str("detail").orEmpty(),
                    ),
                )
            }
        }
    }
}

class SqliteProposalStore(private val helper: BheemDb) : ProposalStore {
    override fun put(proposal: StoredProposal) {
        val values = ContentValues().apply {
            put("id", proposal.id)
            put("action", proposal.action.action)
            put("args", JSONObject(proposal.action.args).toString())
            put("level", proposal.level.name)
            put("requires_tap", if (proposal.requiresTap) 1 else 0)
            put("created_at", proposal.createdAt.toString())
            put("user_text", proposal.userText)
            put("undo_token", proposal.undoToken)
        }
        helper.writableDatabase.insertWithOnConflict("proposals", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    override fun get(id: String): StoredProposal? =
        helper.readableDatabase.query("proposals", null, "id = ?", arrayOf(id), null, null, null).use { c -> if (c.moveToFirst()) read(c) else null }

    override fun all(): List<StoredProposal> =
        helper.readableDatabase.query("proposals", null, null, null, null, null, "created_at").use { c -> buildList { while (c.moveToNext()) add(read(c)) } }

    private fun read(c: Cursor): StoredProposal {
        val json = JSONObject(c.str("args").orEmpty())
        val args = json.keys().asSequence().associateWith { json.getString(it) }
        return StoredProposal(
            c.str("id").orEmpty(), ProposedAction(c.str("action").orEmpty(), args), RiskLevel.valueOf(c.str("level").orEmpty()),
            c.getInt(c.getColumnIndexOrThrow("requires_tap")) == 1, LocalDateTime.parse(c.str("created_at")), c.str("user_text").orEmpty(), c.str("undo_token"),
        )
    }
}

class SqliteMemoryStore(private val helper: BheemDb) : MemoryStore {
    override fun add(record: MemoryRecord) {
        val values = ContentValues().apply {
            put("id", record.id)
            put("kind", record.kind.name)
            put("text", record.text)
            put("created_at", record.createdAt.toString())
            put("merchant", record.merchant)
            put("amount", record.amount)
            put("direction", record.direction)
        }
        helper.writableDatabase.insertOrThrow("memory", null, values)
    }

    override fun remove(id: String): Boolean = helper.writableDatabase.delete("memory", "id = ?", arrayOf(id)) > 0

    override fun all(): List<MemoryRecord> = helper.readableDatabase.query("memory", null, null, null, null, null, "created_at").use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    MemoryRecord(
                        c.str("id").orEmpty(), RecordKind.valueOf(c.str("kind").orEmpty()), c.str("text").orEmpty(),
                        LocalDateTime.parse(c.str("created_at")), c.str("merchant"), c.str("amount"), c.str("direction"),
                    ),
                )
            }
        }
    }
}
