package app.bheem.storage

import android.content.Context
import app.bheem.core.feed.FeedSource
import app.bheem.core.ledger.LedgerStore
import app.bheem.core.memory.MemoryStore
import app.bheem.core.proposal.ProposalStore
import app.bheem.core.proposal.TransactionRunner
import app.bheem.core.reminder.ReminderStore
import java.io.File
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

internal const val SCHEMA_VERSION = 3

class Storage private constructor(private val helper: Db, private val signal: MutableSharedFlow<Unit>) {
    private val runner = SqlTransactionRunner({ helper.writableDatabase }, signal)

    val transactions: TransactionRunner = runner
    val ledger: LedgerStore = SqlLedgerStore(helper, runner)
    val proposals: ProposalStore = SqlProposalStore(helper, runner)
    val feed: FeedSource = SqlFeedSource(helper)
    val memory: MemoryStore = SqlMemoryStore(helper, runner)
    val reminders: ReminderStore = SqlReminderStore(helper, runner)
    val changes: Flow<Unit> get() = signal

    fun userVersion(): Int = helper.readableDatabase.rawQuery("PRAGMA user_version", emptyArray()).use { it.moveToFirst(); it.getInt(0) }

    fun tableNames(): List<String> = names("table")

    fun indexNames(): List<String> = names("index")

    private fun names(type: String): List<String> =
        helper.readableDatabase.rawQuery("SELECT name FROM sqlite_master WHERE type = ?", arrayOf(type)).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }

    fun close() {
        helper.close()
    }

    fun erase(context: Context, name: String = DEFAULT_NAME, vault: KeyVault = KeyVault(context)) {
        close()
        Companion.erase(context, name, vault)
    }

    internal fun execForTest(sql: String) {
        helper.writableDatabase.execSQL(sql)
    }

    private class Helper(context: Context, name: String, key: ByteArray) :
        SQLiteOpenHelper(context, name, key, null, SCHEMA_VERSION, 1, null, null, false) {
        override fun onCreate(db: SQLiteDatabase) {
            for (statement in Schema.current) db.execSQL(statement)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            // Layout 2 was a development layout that never shipped, so it is rebuilt instead of migrated; every later change needs a real migration.
            check(oldVersion == 2) { "no migration from schema $oldVersion to $newVersion" }
            for (statement in Schema.dropAll) db.execSQL(statement)
            onCreate(db)
        }
    }

    companion object {
        const val DEFAULT_NAME = "bheem-v2.db"

        init {
            System.loadLibrary("sqlcipher")
        }

        fun open(context: Context, name: String = DEFAULT_NAME, vault: KeyVault = KeyVault(context)): Storage {
            if (context.getDatabasePath(name).exists() && !vault.hasKey()) {
                throw KeyLostException("the database exists but its key is gone; the data cannot be read and must be erased")
            }
            val helper = Db(Helper(context.applicationContext, name, vault.databaseKey()))
            helper.writableDatabase
            return Storage(helper, MutableSharedFlow(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST))
        }

        fun erase(context: Context, name: String = DEFAULT_NAME, vault: KeyVault = KeyVault(context)) {
            vault.erase()
            val db = context.getDatabasePath(name)
            for (suffix in listOf("", "-wal", "-shm", "-journal")) File(db.path + suffix).delete()
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
