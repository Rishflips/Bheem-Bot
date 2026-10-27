package app.bheem.storage

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the database layout. A release must stay readable by the previous release (rollback is "install the previous APK"), so a change
 * to the schema is a decision: add a migration that only adds columns or tables (never drops or renames one the previous version reads),
 * raise SCHEMA_VERSION, note it in docs/RELEASING.md, and only then update the pin below.
 */
class SchemaPinTest {
    private val pinnedVersion = 3
    private val pinnedHash = "223542a927e0b8d11238c2fe4168cf5da006162c8ff18b2116973ba76c5f23f1"

    private fun hash(): String {
        val normalized = Schema.current.joinToString("\n") { statement -> statement.lines().joinToString(" ") { it.trim() }.replace(Regex(" +"), " ") }
        return MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun theSchemaVersionIsThePinnedOne() {
        assertEquals(pinnedVersion, SCHEMA_VERSION, "SCHEMA_VERSION changed: see the note at the top of this test")
    }

    @Test
    fun theSchemaStatementsAreThePinnedOnes() {
        assertEquals(pinnedHash, hash(), "the schema changed: see the note at the top of this test")
    }
}
