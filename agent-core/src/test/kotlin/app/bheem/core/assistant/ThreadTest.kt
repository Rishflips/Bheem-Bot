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
}
