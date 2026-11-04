package app.bheem.core.assistant

import app.bheem.core.memory.Citation
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ThreadTest {
    private val at = LocalDateTime.of(2026, 10, 23, 8, 0)

    @Test
    fun turnsKeepTheirOrder() {
        val thread = Thread()
        thread.add(Turn.User("what is on tomorrow", spoken = false, at))
        thread.add(Turn.Answer("You have nothing on Sat 24 Oct.", emptyList(), at))
        val turns = thread.turns()
        assertIs<Turn.User>(turns[0])
        assertIs<Turn.Answer>(turns[1])
    }

    @Test
    fun theThreadKeepsOnlyTheNewestTurns() {
        val thread = Thread(limit = 3)
        repeat(5) { thread.add(Turn.Notice("n$it", at)) }
        assertEquals(listOf("n2", "n3", "n4"), thread.turns().map { (it as Turn.Notice).text })
    }

    @Test
    fun anAnswerKeepsItsCitations() {
        val cite = Citation("n1", "note", at)
        val thread = Thread()
        thread.add(Turn.Answer("Here is what I found:", listOf(cite), at))
        assertEquals(listOf(cite), (thread.turns().single() as Turn.Answer).citations)
    }

    @Test
    fun clearingEmptiesTheThread() {
        val thread = Thread()
        thread.add(Turn.Notice("x", at))
        thread.clear()
        assertEquals(emptyList(), thread.turns())
    }

    private class FakeStore : TurnStore {
        val saved = mutableListOf<Turn>()

        override fun add(turn: Turn) {
            saved += turn
        }

        override fun recent(limit: Int): List<Turn> = saved.takeLast(limit)

        override fun clear() = saved.clear()
    }

    @Test
    fun aThreadReloadsTheTurnsItSavedLastTime() {
        val store = FakeStore()
        Thread(store = store).add(Turn.User("what is on tomorrow", spoken = true, at))
        val again = Thread(store = store)
        assertEquals(listOf<Turn>(Turn.User("what is on tomorrow", spoken = true, at)), again.turns())
    }

    @Test
    fun everyNewTurnIsSavedAndClearingForgetsThem() {
        val store = FakeStore()
        val thread = Thread(store = store)
        thread.add(Turn.Brief("Your day", emptyList(), at))
        assertEquals(1, store.saved.size)
        thread.clear()
        assertEquals(emptyList(), store.saved)
    }

    @Test
    fun citationsSurviveTheirStoredForm() {
        val cites = listOf(Citation("p1", "reminder", at), Citation("n1", "note", at.minusDays(2)))
        assertEquals(cites, TurnCodec.decode(TurnCodec.encode(cites)))
        assertEquals(emptyList(), TurnCodec.decode(""))
        assertEquals(emptyList(), TurnCodec.decode("garbage|only"))
    }

    @Test
    fun aPlanKeepsItsStepsAndSkippedJobsInTheirStoredForm() {
        val (steps, skipped) = TurnCodec.decodePlan(TurnCodec.encodePlan(listOf("p1", "p2"), listOf("remind me to call Ravi: What time should I use for that?"), at))
        assertEquals(listOf("p1", "p2"), steps)
        assertEquals(listOf("remind me to call Ravi: What time should I use for that?"), skipped)
        assertEquals(Pair(emptyList<String>(), emptyList<String>()), TurnCodec.decodePlan(""))
    }
}
