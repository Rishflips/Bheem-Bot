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
    fun aSpendingQuestionTotalsTheMatchingExpensesAndCitesThem() {
        runBlocking {
            val exec = LocalExecutors.expense(store, newId) { t0 }
            exec.execute(ProposedAction("LogExpense", mapOf("amount" to "380.00", "merchant" to "Swiggy", "direction" to "debit")))
            exec.execute(ProposedAction("LogExpense", mapOf("amount" to "120.50", "merchant" to "Swiggy", "direction" to "debit")))
            exec.execute(ProposedAction("LogExpense", mapOf("amount" to "90.00", "merchant" to "Uber", "direction" to "debit")))
        }
        val answer = Ask.answer("what did I spend at Swiggy", store.all(), t0)
        assertTrue("500.50" in answer.text, answer.text)
        assertEquals(listOf("r1", "r2"), answer.citations)
    }

    @Test
    fun aSpendingQuestionAboutSomethingOtherThanAShopIsNotAnsweredWithAWrongTotal() {
        runBlocking {
            LocalExecutors.expense(store, newId) { t0 }.execute(
                ProposedAction("LogExpense", mapOf("amount" to "380.00", "merchant" to "Swiggy", "direction" to "debit")),
            )
        }
        val answer = Ask.answer("what did I spend on food last week", store.all(), t0)
        assertTrue("shop" in answer.text, answer.text)
        assertEquals(emptyList(), answer.citations)
        assertTrue("380.00" in Ask.answer("how much did I spend this week", store.all(), t0).text)
    }
}
