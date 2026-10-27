package app.bheem.storage

import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the database layout. A change to the schema is a decision: write a migration that keeps every user's data from every shipped
 * version (add tables or columns; never drop or rename one in use), raise SCHEMA_VERSION, note it in docs/RELEASING.md, and only then
 * update the pin below. This guards the creation statements, not the migration code; an older release cannot open a newer schema.
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
