package app.bheem.storage

import android.content.Context
import app.bheem.core.feed.FeedSource
import app.bheem.core.ledger.LedgerStore
import app.bheem.core.memory.MemoryStore
import app.bheem.core.proposal.ProposalStore
import app.bheem.core.proposal.TransactionRunner
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

internal const val SCHEMA_VERSION = 2

class Storage private constructor(private val helper: Helper, private val signal: MutableSharedFlow<Unit>) {
    private val runner = SqlTransactionRunner({ helper.writableDatabase }, signal)

    val transactions: TransactionRunner = runner
    val ledger: LedgerStore = SqlLedgerStore(helper, runner)
    val proposals: ProposalStore = SqlProposalStore(helper, runner)
    val feed: FeedSource = SqlFeedSource(helper)
    val memory: MemoryStore = SqlMemoryStore(helper, runner)
    val changes: Flow<Unit> get() = signal

    fun userVersion(): Int = helper.readableDatabase.rawQuery("PRAGMA user_version", emptyArray()).use { it.moveToFirst(); it.getInt(0) }

    fun tableNames(): List<String> = names("table")

    fun indexNames(): List<String> = names("index")

    private fun names(type: String): List<String> =
        helper.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type = ?", arrayOf(type)).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun close() {
        helper.close()
    }

    private class Helper(context: Context, name: String, key: ByteArray) :
        SQLiteOpenHelper(context, name, key, null, SCHEMA_VERSION, 1, null, null, false) {
        override fun onCreate(db: SQLiteDatabase) {
            for (statement in Schema.v2) db.execSQL(statement)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            error("no migration from schema $oldVersion to $newVersion")
        }
    }

    companion object {
        const val DEFAULT_NAME = "bheem-v2.db"

        init {
            System.loadLibrary("sqlcipher")
        }

        fun open(context: Context, name: String = DEFAULT_NAME, vault: KeyVault = KeyVault(context)): Storage {
            val helper = Helper(context.applicationContext, name, vault.databaseKey())
            helper.writableDatabase
            return Storage(helper, MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST))
        }

        fun erase(context: Context, name: String = DEFAULT_NAME, vault: KeyVault = KeyVault(context)) {
            val db = context.getDatabasePath(name)
            for (suffix in listOf("", "-wal", "-shm", "-journal")) File(db.path + suffix).delete()
            vault.erase()
            context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}

object LegacyDatabase {
    fun delete(context: Context) {
        val file = context.getDatabasePath("bheem.db")
        for (suffix in listOf("", "-wal", "-shm", "-journal")) File(file.path + suffix).delete()
    }
}
