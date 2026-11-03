package app.bheem.storage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import net.zetetic.database.sqlcipher.SQLiteDatabase

/** A database from an older layout is never dropped or rebuilt: opening it fails and its rows stay where they were. */
class SchemaUpgradeInstrumentedTest {
    private lateinit var context: Context
    private lateinit var name: String
    private lateinit var vault: KeyVault

    @BeforeTest
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString().take(8)
        name = "upgrade-$id.db"
        vault = KeyVault(context, alias = "upgrade.key.$id", fileName = "upgrade-$id.key")
    }

    @AfterTest
    fun tearDown() {
        Storage.erase(context, name, vault)
    }

    private fun createOldDatabase(version: Int) {
        System.loadLibrary("sqlcipher")
        val file = context.getDatabasePath(name).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, vault.databaseKey(), null, null, null).use { db ->
            db.execSQL("CREATE TABLE memory (id TEXT PRIMARY KEY, text TEXT NOT NULL)")
            db.execSQL("INSERT INTO memory (id, text) VALUES ('old-1', 'a note from an older release')")
            db.version = version
        }
    }

    private fun oldRows(): Int {
        val file = context.getDatabasePath(name)
        return SQLiteDatabase.openOrCreateDatabase(file, vault.databaseKey(), null, null, null).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM memory WHERE id = 'old-1'", emptyArray()).use { it.moveToFirst(); it.getInt(0) }
        }
    }

    @Test
    fun aLayoutTwoDatabaseIsRefusedAndItsRowsSurvive() {
        createOldDatabase(version = 2)
        assertFailsWith<IllegalStateException> { Storage.open(context, name, vault) }
        assertEquals(1, oldRows())
    }

    @Test
    fun aDatabaseFromAnyOtherOlderLayoutIsRefusedToo() {
        createOldDatabase(version = 1)
        assertFailsWith<IllegalStateException> { Storage.open(context, name, vault) }
        assertEquals(1, oldRows())
    }
}
