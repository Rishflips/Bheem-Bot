package app.bheem.storage

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import app.bheem.core.action.ProposedAction
import app.bheem.core.feed.Feed
import app.bheem.core.feed.FeedStatus
import app.bheem.core.gate.Provenance
import app.bheem.core.gate.RiskLevel
import app.bheem.core.ledger.Ledger
import app.bheem.core.ledger.LedgerEvent
import app.bheem.core.ledger.NewLedgerEntry
import app.bheem.core.memory.Ask
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.RecordKind
import app.bheem.core.proposal.StoredProposal
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import kotlin.system.measureTimeMillis
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class StorageInstrumentedTest {
    private lateinit var context: Context
    private lateinit var name: String
    private lateinit var vault: KeyVault
    private lateinit var storage: Storage
    private val t0 = LocalDateTime.of(2026, 10, 24, 9, 0)

    @BeforeTest
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString().take(8)
        name = "test-$id.db"
        vault = KeyVault(context, alias = "test.key.$id", fileName = "test-$id.key")
        storage = Storage.open(context, name, vault)
    }

    @AfterTest
    fun tearDown() {
        storage.close()
        Storage.erase(context, name, vault)
    }

    private fun entry(id: String, event: LedgerEvent, at: LocalDateTime = t0) = NewLedgerEntry(at, id, event, "CreateReminder", "bar", "")

    private fun proposal(id: String, at: LocalDateTime = t0) = StoredProposal(
        id, ProposedAction("CreateReminder", mapOf("text" to "call Ravi", "time" to "2026-10-25T09:00")), RiskLevel.L0, true, at,
        "remind me tomorrow at 9 to call Ravi", null, Provenance.USER, "bar", "proposer-v3.7-line", "gemma-4-e2b", "GPU",
    )

    @Test
    fun theDatabaseIsEncryptedAndTheWrongKeyIsRefused() {
        storage.ledger.append(entry("a", LedgerEvent.PROPOSED))
        storage.close()
        val file = context.getDatabasePath(name)
        assertTrue(file.exists())
        val header = file.inputStream().use { it.readNBytes(16) }
        assertFalse(String(header, Charsets.ISO_8859_1).startsWith("SQLite format 3"))
        val other = KeyVault(context, "other.${UUID.randomUUID()}", "other-${UUID.randomUUID()}.key").also { it.databaseKey() }
        val refused = assertFailsWith<Exception> { Storage.open(context, name, other).ledger.history("a") }
        other.erase()
        assertTrue("not a database" in (refused.message ?: "").lowercase(), "unexpected failure: ${refused::class.java.name}: ${refused.message}")
        storage = Storage.open(context, name, vault)
        assertEquals(1, storage.ledger.history("a").size)
    }

    @Test
    fun theSchemaIsVersionThreeWithTheIndexesAndTables() {
        assertEquals(3, storage.userVersion())
        assertEquals(setOf("proposals", "ledger", "memory", "memory_fts", "reminders"), storage.tableNames().filter { it in setOf("proposals", "ledger", "memory", "memory_fts", "reminders") }.toSet())
        assertTrue("ledger_proposal" in storage.indexNames())
    }

    @Test
    fun theLedgerAssignsRisingIdsAndAnswersWithoutScanning() {
        val a = storage.ledger.append(entry("a", LedgerEvent.PROPOSED))
        val b = storage.ledger.append(entry("b", LedgerEvent.PROPOSED))
        storage.ledger.append(entry("a", LedgerEvent.APPROVED))
        storage.ledger.append(entry("c", LedgerEvent.PROPOSED))
        storage.ledger.append(entry("c", LedgerEvent.DISMISSED))
        assertTrue(b.id > a.id)
        assertEquals(listOf(LedgerEvent.PROPOSED, LedgerEvent.APPROVED), storage.ledger.history("a").map { it.event })
        assertEquals(LedgerEvent.APPROVED, storage.ledger.lastEvent("a")?.event)
        assertNull(storage.ledger.lastEvent("missing"))
        storage.proposals.put(proposal("b"))
        storage.proposals.put(proposal("a"))
        storage.proposals.put(proposal("c"))
        assertEquals(listOf("b"), storage.ledger.openProposalIds())
    }

    @Test
    fun theLedgerRulesWorkOnTheRealStore() {
        val ledger = Ledger(storage.ledger)
        ledger.record("p1", LedgerEvent.PROPOSED, t0, action = "CreateReminder", source = "bar")
        assertTrue(ledger.record("p1", LedgerEvent.EXECUTED, t0) is app.bheem.core.ledger.LedgerResult.Refused)
        ledger.record("p1", LedgerEvent.APPROVED, t0)
        ledger.record("p1", LedgerEvent.EXECUTED, t0.plusSeconds(2))
        assertTrue(ledger.canUndo("p1", t0.plusSeconds(10), 30))
        assertFalse(ledger.canUndo("p1", t0.plusSeconds(40), 30))
    }

    @Test
    fun aThousandLedgerRowsAreReadFast() {
        storage.transactions.run { repeat(1000) { storage.ledger.append(entry("p${it % 50}", LedgerEvent.PROPOSED)) } }
        val ms = measureTimeMillis { repeat(20) { storage.ledger.history("p7"); storage.ledger.lastEvent("p7") } }
        assertTrue(ms / 20 < 50, "20 rounds of history plus lastEvent over 1,000 rows took $ms ms in total")
        android.util.Log.i("StorageTest", "ledger read, 20 rounds over 1000 rows: $ms ms")
    }

    @Test
    fun aProposalKeepsItsProvenanceVersionsAndZoneAwareTimeThroughAReload() {
        storage.proposals.put(proposal("p1").copy(undoToken = "tok"))
        storage.close()
        storage = Storage.open(context, name, vault)
        val back = storage.proposals.get("p1")
        assertEquals(proposal("p1").copy(undoToken = "tok"), back)
        assertEquals("proposer-v3.7-line", back?.promptVersion)
        assertEquals("GPU", back?.backend)
    }

    @Test
    fun theFeedRowsJoinTheLastLedgerEntryAndFeedBuildsItems() {
        val ledger = Ledger(storage.ledger)
        for ((i, id) in listOf("a", "b").withIndex()) {
            storage.transactions.run {
                storage.proposals.put(proposal(id, t0.plusMinutes(i.toLong())))
                ledger.record(id, LedgerEvent.PROPOSED, t0, action = "CreateReminder", source = "bar")
            }
        }
        ledger.record("a", LedgerEvent.APPROVED, t0)
        ledger.record("a", LedgerEvent.EXECUTED, t0)
        val rows = storage.feed.rows(limit = 10)
        assertEquals(listOf("b", "a"), rows.map { it.proposal.id })
        val items = Feed.items(rows, t0.plusSeconds(5), 30)
        assertEquals(listOf(FeedStatus.PENDING, FeedStatus.DONE), items.map { it.status })
        assertEquals(1, storage.feed.rows(limit = 1).size)
    }

    @Test
    fun aFailingTransactionLeavesNothingBehind() {
        assertFailsWith<IllegalStateException> {
            storage.transactions.run {
                storage.proposals.put(proposal("p1"))
                storage.ledger.append(entry("p1", LedgerEvent.PROPOSED))
                error("disk full")
            }
        }
        assertNull(storage.proposals.get("p1"))
        assertEquals(emptyList(), storage.ledger.history("p1"))
    }

    @Test
    fun nestedTransactionsCommitTogether() {
        storage.transactions.run {
            storage.transactions.run { storage.proposals.put(proposal("p1")) }
            storage.ledger.append(entry("p1", LedgerEvent.PROPOSED))
        }
        assertEquals("p1", storage.proposals.get("p1")?.id)
        assertEquals(1, storage.ledger.history("p1").size)
    }

    @Test
    fun memorySearchUsesFullTextAndIgnoresCaseAndAccents() {
        storage.memory.add(MemoryRecord("r1", RecordKind.NOTE, "The scooter key is kept in the cupboard", t0))
        storage.memory.add(MemoryRecord("r2", RecordKind.NOTE, "Meet at the Café on Friday", t0))
        storage.memory.add(MemoryRecord("r3", RecordKind.TASK, "renew the passport", t0))
        assertEquals(listOf("r1"), storage.memory.search("where did I keep the SCOOTER key", 5).map { it.id })
        assertEquals(listOf("r2"), storage.memory.search("cafe", 5).map { it.id })
        assertEquals(listOf("r3"), storage.memory.search("my tasks passport", 5).map { it.id })
        assertEquals(emptyList(), storage.memory.search("what is the", 5))
        assertEquals(emptyList(), storage.memory.search("quantum", 5))
    }

    @Test
    fun removingARecordRemovesItFromSearchAndKindQueriesWork() {
        storage.memory.add(MemoryRecord("r1", RecordKind.NOTE, "locker number 27", t0))
        storage.memory.add(MemoryRecord("e1", RecordKind.EXPENSE, "Swiggy 380.00", t0, "Swiggy", "380.00", "debit"))
        assertEquals(listOf("r1"), storage.memory.search("locker", 5).map { it.id })
        assertTrue(storage.memory.remove("r1"))
        assertEquals(emptyList(), storage.memory.search("locker", 5))
        assertEquals(listOf("e1"), storage.memory.ofKind(RecordKind.EXPENSE).map { it.id })
        assertTrue("380.00" in Ask.answer("what did I spend at Swiggy", storage.memory, t0).text)
    }

    @Test
    fun aCommittedWriteSignalsChangesOnceAndARollbackDoesNot() = runBlocking {
        val seen = mutableListOf<Unit>()
        val job = storage.changes.onEach { seen += it }.launchIn(this)
        kotlinx.coroutines.delay(100)
        storage.transactions.run {
            storage.proposals.put(proposal("p1"))
            storage.ledger.append(entry("p1", LedgerEvent.PROPOSED))
        }
        kotlinx.coroutines.delay(200)
        assertEquals(1, seen.size)
        runCatching { storage.transactions.run { storage.proposals.put(proposal("p2")); error("no") } }
        kotlinx.coroutines.delay(200)
        assertEquals(1, seen.size)
        val next = async(start = CoroutineStart.UNDISPATCHED) { withTimeoutOrNull(1000) { storage.changes.first() } }
        storage.memory.add(MemoryRecord("r1", RecordKind.NOTE, "x", t0))
        assertNotEquals(null, next.await())
        job.cancel()
    }

    @Test
    fun theKeyIsCreatedOnceKeptOnlyWrappedAndErasedOnRequest() {
        val key = vault.databaseKey()
        assertEquals(32, key.size)
        assertTrue(key.contentEquals(KeyVault(context, vault.alias, vault.fileName).databaseKey()))
        val file = File(context.noBackupFilesDir, vault.fileName)
        assertTrue(file.exists())
        assertFalse(file.readBytes().toList().windowed(key.size).any { it == key.toList() })
        vault.erase()
        assertFalse(file.exists())
        assertFalse(key.contentEquals(vault.databaseKey()))
    }

    @Test
    fun eraseEverythingRemovesTheDatabaseFilesAndTheKey() {
        storage.ledger.append(entry("a", LedgerEvent.PROPOSED))
        storage.close()
        val db = context.getDatabasePath(name)
        assertTrue(db.exists())
        Storage.erase(context, name, vault)
        assertFalse(db.exists())
        assertFalse(File(db.path + "-wal").exists())
        assertFalse(File(context.noBackupFilesDir, vault.fileName).exists())
        storage = Storage.open(context, name, vault)
        assertEquals(emptyList(), storage.ledger.history("a"))
    }

    @Test
    fun aLegacyPlainDatabaseFromTheFirstDevBuildIsDeleted() {
        val legacy = context.getDatabasePath("bheem.db")
        legacy.parentFile?.mkdirs()
        legacy.writeText("plain dev database")
        File(legacy.path + "-journal").writeText("x")
        LegacyDatabase.delete(context)
        assertFalse(legacy.exists())
        assertFalse(File(legacy.path + "-journal").exists())
    }

    @Test
    fun aDatabaseWhoseKeyFileIsGoneIsReportedAsLostAndLeftAlone() {
        storage.ledger.append(entry("a", LedgerEvent.PROPOSED))
        storage.close()
        File(context.noBackupFilesDir, vault.fileName).delete()
        val size = context.getDatabasePath(name).length()
        assertFailsWith<KeyLostException> { Storage.open(context, name, vault) }
        assertEquals(size, context.getDatabasePath(name).length())
        assertFalse(File(context.noBackupFilesDir, vault.fileName).exists())
        storage = Storage.open(context, "fresh-" + name, vault)
    }

    @Test
    fun anInstanceEraseClosesTheDatabaseThenRemovesTheKeyAndTheFiles() {
        storage.ledger.append(entry("a", LedgerEvent.PROPOSED))
        val db = context.getDatabasePath(name)
        storage.erase(context, name, vault)
        assertFalse(db.exists())
        assertFalse(File(context.noBackupFilesDir, vault.fileName).exists())
        assertFailsWith<Exception> { storage.ledger.history("a") }
        storage = Storage.open(context, name, vault)
    }

    @Test
    fun theSearchIndexSurvivesVacuumAndUpdates() {
        storage.memory.add(MemoryRecord("r1", RecordKind.NOTE, "first note about passports", t0))
        storage.memory.add(MemoryRecord("r2", RecordKind.NOTE, "second note about umbrellas", t0))
        storage.memory.add(MemoryRecord("r3", RecordKind.NOTE, "third note about lockers", t0))
        storage.memory.remove("r1")
        storage.execForTest("VACUUM")
        assertEquals(listOf("r3"), storage.memory.search("lockers", 5).map { it.id })
        assertEquals(listOf("r2"), storage.memory.search("umbrellas", 5).map { it.id })
        storage.execForTest("UPDATE memory SET text = 'second note about scooters' WHERE id = 'r2'")
        assertEquals(emptyList(), storage.memory.search("umbrellas", 5))
        assertEquals(listOf("r2"), storage.memory.search("scooters", 5).map { it.id })
    }

    @Test
    fun aFailedInnerTransactionCaughtByTheOuterOneDoesNotSignalOrPersist() = runBlocking {
        val seen = mutableListOf<Unit>()
        val job = storage.changes.onEach { seen += it }.launchIn(this)
        kotlinx.coroutines.delay(100)
        storage.transactions.run {
            runCatching { storage.transactions.run { storage.proposals.put(proposal("p1")); error("inner") } }
            storage.proposals.put(proposal("p2"))
        }
        kotlinx.coroutines.delay(200)
        assertEquals(0, seen.size)
        assertNull(storage.proposals.get("p1"))
        assertNull(storage.proposals.get("p2"))
        job.cancel()
    }

    @Test
    fun aDevelopmentDatabaseFromTheUnreleasedVersionTwoLayoutIsRecreated() {
        storage.memory.add(MemoryRecord("r1", RecordKind.NOTE, "old layout row", t0))
        storage.execForTest("PRAGMA user_version = 2")
        storage.close()
        storage = Storage.open(context, name, vault)
        assertEquals(3, storage.userVersion())
        assertEquals(emptyList(), storage.memory.ofKind(RecordKind.NOTE))
        storage.memory.add(MemoryRecord("r2", RecordKind.NOTE, "new layout row", t0))
        assertEquals(listOf("r2"), storage.memory.search("layout", 5).map { it.id })
    }
}
