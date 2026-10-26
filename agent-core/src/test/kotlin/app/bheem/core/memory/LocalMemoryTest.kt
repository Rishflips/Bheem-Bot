package app.bheem.core.memory

import app.bheem.core.action.ProposedAction
import app.bheem.core.proposal.ExecutionResult
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LocalMemoryTest {
    private val t0 = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val store = InMemoryMemoryStore()
    private var n = 0
    private val newId = { "r${++n}" }

    private fun note(text: String) = ProposedAction("SaveNote", mapOf("text" to text))

    private fun save(text: String) = runBlocking { LocalExecutors.note(store, newId) { t0 }.execute(note(text)) }

    @Test
    fun savingANoteStoresItAndUndoRemovesIt() {
        val exec = LocalExecutors.note(store, newId) { t0 }
        val done = assertIs<ExecutionResult.Done>(runBlocking { exec.execute(note("the locker number is 27")) })
        assertEquals(listOf("the locker number is 27"), store.all().map { it.text })
        assertEquals(RecordKind.NOTE, store.all().single().kind)
        assertEquals(true, runBlocking { exec.undo(note("x"), done.undoToken) })
        assertEquals(emptyList(), store.all())
    }

    @Test
    fun aTaskAndAnExpenseAreStoredWithTheirOwnKinds() {
        runBlocking {
            LocalExecutors.task(store, newId) { t0 }.execute(ProposedAction("CreateTask", mapOf("text" to "renew the passport")))
            LocalExecutors.expense(store, newId) { t0 }.execute(
                ProposedAction("LogExpense", mapOf("amount" to "380.00", "merchant" to "Uber", "direction" to "debit")),
            )
        }
        assertEquals(listOf(RecordKind.TASK, RecordKind.EXPENSE), store.all().map { it.kind })
        assertEquals("Uber", store.all().last().merchant)
        assertEquals("380.00", store.all().last().amount)
    }

    @Test
    fun undoOfAnUnknownTokenIsHarmlessAndReportsFalse() {
        assertEquals(false, runBlocking { LocalExecutors.note(store, newId) { t0 }.undo(note("x"), "nope") })
    }

    @Test
    fun searchRanksByWordOverlapIgnoringFillerWords() {
        save("the scooter key is kept in the cupboard")
        save("Anita prefers tea without sugar")
        save("the wifi password is under the router")
        val hits = MemorySearch.search("where did I keep the scooter key", store.all(), limit = 3)
        assertEquals("the scooter key is kept in the cupboard", hits.first().record.text)
        assertTrue(hits.none { it.record.text.contains("Anita") })
    }

    @Test
    fun aQueryWithNoMatchingWordsFindsNothing() {
        save("Anita prefers tea without sugar")
        assertEquals(emptyList(), MemorySearch.search("when is the dentist appointment", store.all(), limit = 3))
    }

    @Test
    fun theAnswerCitesTheRecordsItUsed() {
        save("the scooter key is kept in the cupboard")
        val answer = Ask.answer("where did I keep the scooter key", store.all(), t0)
        assertTrue("scooter key is kept in the cupboard" in answer.text)
        assertEquals(listOf("r1"), answer.citations)
    }

    @Test
    fun anEmptyMemoryAnswersHonestly() {
        val answer = Ask.answer("where did I keep the scooter key", store.all(), t0)
        assertTrue("nothing" in answer.text.lowercase())
        assertEquals(emptyList(), answer.citations)
    }

    @Test
    fun executingTheSameProposalTwiceStoresOneRecordAndKeepsOneUndoToken() {
        val exec = LocalExecutors.note(store, newId) { t0 }
        val first = assertIs<ExecutionResult.Done>(runBlocking { exec.execute(note("the locker number is 27"), "p7") })
        val second = assertIs<ExecutionResult.Done>(runBlocking { exec.execute(note("the locker number is 27"), "p7") })
        assertEquals(1, store.all().size)
        assertEquals(first.undoToken, second.undoToken)
        assertEquals("p7", first.undoToken)
    }

    @Test
    fun twoDifferentProposalsWithTheSameTextAreTwoRecordsWithTheirOwnTokens() {
        val exec = LocalExecutors.note(store, newId) { t0 }
        val a = assertIs<ExecutionResult.Done>(runBlocking { exec.execute(note("same words"), "p1") })
        val b = assertIs<ExecutionResult.Done>(runBlocking { exec.execute(note("same words"), "p2") })
        assertEquals(2, store.all().size)
        assertEquals(true, runBlocking { exec.undo(note("x"), a.undoToken) })
        assertEquals(listOf("same words"), store.all().map { it.text })
        assertEquals(listOf("p2"), store.all().map { it.id })
        assertEquals("p2", b.undoToken)
    }

    @Test
    fun undoingTwiceReportsFalseTheSecondTimeBecauseNothingChanged() {
        val exec = LocalExecutors.note(store, newId) { t0 }
        val done = assertIs<ExecutionResult.Done>(runBlocking { exec.execute(note("x y"), "p1") })
        assertEquals(true, runBlocking { exec.undo(note("x"), done.undoToken) })
        assertEquals(false, runBlocking { exec.undo(note("x"), done.undoToken) })
    }

    @Test
    fun aSpendingQuestionListsTheMatchingRecordsAndNeverAddsThemUp() {
        runBlocking {
            val exec = LocalExecutors.expense(store, newId) { t0 }
            exec.execute(ProposedAction("LogExpense", mapOf("amount" to "380.00", "merchant" to "Swiggy", "direction" to "debit")), "e1")
            exec.execute(ProposedAction("LogExpense", mapOf("amount" to "120.50", "merchant" to "Swiggy", "direction" to "debit")), "e2")
            exec.execute(ProposedAction("LogExpense", mapOf("amount" to "90.00", "merchant" to "Uber", "direction" to "debit")), "e3")
        }
        val answer = Ask.answer("what did I spend at Swiggy", store.all(), t0)
        assertTrue("Swiggy 380.00" in answer.text, answer.text)
        assertTrue("Swiggy 120.50" in answer.text, answer.text)
        assertTrue("500.50" !in answer.text, answer.text)
        assertTrue("Uber" !in answer.text, answer.text)
        assertEquals(setOf("e1", "e2"), answer.citations.toSet())
    }

    @Test
    fun eachAnswerLineSaysWhatKindOfRecordItIsAndWhenItWasSaved() {
        runBlocking {
            LocalExecutors.note(store, newId) { t0 }.execute(note("the scooter key is kept in the cupboard"), "n1")
            LocalExecutors.task(store, newId) { t0.plusDays(2) }.execute(ProposedAction("CreateTask", mapOf("text" to "service the scooter")), "t1")
        }
        val answer = Ask.answer("scooter", store.all(), t0.plusDays(3))
        assertTrue("- service the scooter (task, 25 Oct)" in answer.text, answer.text)
        assertTrue("- the scooter key is kept in the cupboard (note, 23 Oct)" in answer.text, answer.text)
    }

    @Test
    fun askNeverMentionsSpendingTotals() {
        val answer = Ask.answer("how much did I spend this week", store.all(), t0)
        assertTrue("spent" !in answer.text.lowercase(), answer.text)
        assertEquals(emptyList(), answer.citations)
    }
}
