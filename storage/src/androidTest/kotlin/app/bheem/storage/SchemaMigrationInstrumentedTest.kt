package app.bheem.storage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.bheem.core.memory.RecordKind
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** The first real migration: a database written by the shipped layout 3 opens as layout 4 and keeps every row. */
class SchemaMigrationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var name: String
    private lateinit var vault: KeyVault
    private var storage: Storage? = null

    @BeforeTest
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString().take(8)
        name = "migrate-$id.db"
        vault = KeyVault(context, alias = "migrate.key.$id", fileName = "migrate-$id.key")
    }

    @AfterTest
    fun tearDown() {
        storage?.close()
        Storage.erase(context, name, vault)
    }

    private fun createLayoutThree() {
        System.loadLibrary("sqlcipher")
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, vault.databaseKey(), null, null, null).use { db ->
            for (statement in FrozenSchemaV3.statements) db.execSQL(statement)
            db.execSQL("INSERT INTO memory (id, kind, text, created_at_epoch, zone_id) VALUES ('m1', 'NOTE', 'the electrician is Suresh', 1700000000000, 'Asia/Kolkata')")
            db.execSQL("INSERT INTO memory (id, kind, text, created_at_epoch, zone_id) VALUES ('t1', 'TASK', 'buy milk', 1700000100000, 'Asia/Kolkata')")
            db.execSQL(
                "INSERT INTO proposals (id, action, args, level, requires_tap, created_at_epoch, zone_id, user_text, provenance, source, prompt_version, model_id, backend) " +
                    "VALUES ('p1', 'CreateReminder', '{\"text\":\"call Arun\",\"time\":\"2026-10-24T17:00\"}', 'L1', 1, 1700000200000, 'Asia/Kolkata', 'remind me', 'USER', 'bar', 'proposer-v3.8-line', 'gemma-4-e2b', 'GPU')",
            )
            db.execSQL("INSERT INTO reminders (proposal_id, text, fire_at_epoch, zone_id, state) VALUES ('p1', 'call Arun', 1800000000000, 'Asia/Kolkata', 'SCHEDULED')")
            db.version = 3
        }
    }

    @Test
    fun aLayoutThreeDatabaseOpensAsLayoutFourWithEveryRowIntact() {
        createLayoutThree()
        val opened = Storage.open(context, name, vault).also { storage = it }
        assertEquals(4, opened.userVersion())
        assertEquals(listOf("m1"), opened.memory.ofKind(RecordKind.NOTE).map { it.id })
        assertEquals("buy milk", opened.memory.ofKind(RecordKind.TASK).single().text)
        assertEquals("CreateReminder", opened.proposals.get("p1")?.action?.action)
        assertEquals(listOf("call Arun"), opened.reminders.scheduled().map { it.text })
    }

    @Test
    fun theNewColumnsAndTableWorkAfterTheMigration() {
        createLayoutThree()
        val opened = Storage.open(context, name, vault).also { storage = it }
        assertNull(opened.memory.ofKind(RecordKind.TASK).single().doneAt)
        val at = LocalDateTime.of(2026, 11, 4, 9, 0)
        assertTrue(opened.memory.markDone("t1", at))
        assertEquals(at, opened.memory.ofKind(RecordKind.TASK).single().doneAt)
        assertTrue(opened.memory.markDone("t1", null))
        assertNull(opened.memory.ofKind(RecordKind.TASK).single().doneAt)
        assertTrue("turns" in opened.tableNames())
        assertTrue("plan_id" in opened.columnNames("proposals"))
    }

    @Test
    fun openingTheMigratedDatabaseAgainChangesNothing() {
        createLayoutThree()
        Storage.open(context, name, vault).close()
        val again = Storage.open(context, name, vault).also { storage = it }
        assertEquals(4, again.userVersion())
        assertEquals(listOf("m1"), again.memory.ofKind(RecordKind.NOTE).map { it.id })
    }
}
