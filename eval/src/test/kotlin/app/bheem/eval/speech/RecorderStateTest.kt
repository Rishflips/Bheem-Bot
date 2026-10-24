package app.bheem.eval.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecorderStateTest {
    private fun take(n: Int, speaker: String = "S01", condition: RecordingCondition = RecordingCondition.QUIET) =
        Take("$speaker-cmd-%03d-${condition.code}".format(n), speaker, "cmd-%03d".format(n), "sentence $n", condition, n)

    private val takes = listOf(
        take(1), take(2), take(3, condition = RecordingCondition.NOISY), take(4, condition = RecordingCondition.NOISY),
        take(5, condition = RecordingCondition.FAR),
    )

    private fun id(n: Int) = takes[n - 1].id

    @Test
    fun startsAtTheFirstTakeThatIsNotDoneYet() {
        assertEquals(id(1), RecorderState.start(takes, emptySet()).current?.id)
        assertEquals(id(3), RecorderState.start(takes, setOf(id(1), id(2))).current?.id)
    }

    @Test
    fun whenEverythingIsDoneItIsFinishedAndShowsTheFirstTake() {
        val state = RecorderState.start(takes, takes.map { it.id }.toSet())
        assertTrue(state.finished)
        assertEquals(id(1), state.current?.id)
    }

    @Test
    fun markingATakeDoneMovesToTheNextOneNotDone() {
        var state = RecorderState.start(takes, setOf(id(2)))
        state = state.markDone(id(1))
        assertEquals(id(3), state.current?.id)
        assertEquals(2, state.doneCount - 0)
        assertFalse(state.finished)
    }

    @Test
    fun afterTheLastTakeItWrapsToAnEarlierOneThatIsStillOpen() {
        var state = RecorderState.start(takes, setOf(id(2), id(3), id(4)))
        state = state.markDone(id(1))
        assertEquals(id(5), state.current?.id)
        state = state.markDone(id(5))
        assertTrue(state.finished)
    }

    @Test
    fun movingStaysInsideTheList() {
        val state = RecorderState.start(takes, emptySet())
        assertEquals(id(1), state.move(-1).current?.id)
        assertEquals(id(3), state.move(2).current?.id)
        assertEquals(id(5), state.move(99).current?.id)
    }

    @Test
    fun theBlockStartsWhereTheConditionChanges() {
        val atFirst = RecorderState.start(takes, emptySet())
        assertTrue(atFirst.startsBlock)
        assertEquals("Block 1 of 3: quiet", atFirst.blockLabel())
        assertFalse(atFirst.move(1).startsBlock)
        val noisy = atFirst.move(2)
        assertTrue(noisy.startsBlock)
        assertEquals("Block 2 of 3: noisy", noisy.blockLabel())
        assertFalse(noisy.move(1).startsBlock)
        assertEquals("Block 3 of 3: far", noisy.move(2).blockLabel())
    }

    @Test
    fun doneIdsFromOtherSpeakersAreNotCounted() {
        val state = RecorderState.start(takes, setOf(id(1), "S09-cmd-001-q"))
        assertEquals(1, state.doneCount)
    }

    @Test
    fun speakersAreListedOnceInOrderAndEachGetsItsTakesInSheetOrder() {
        val all = listOf(take(2, "S02"), take(1, "S02"), take(1, "S01"))
        assertEquals(listOf("S01", "S02"), RecorderState.speakers(all))
        val state = RecorderState.forSpeaker(all, "S02", emptySet())
        assertEquals(listOf(1, 2), state.takes.map { it.position })
        assertEquals("S02-cmd-001-q", state.current?.id)
    }

    @Test
    fun anEmptyListHasNoCurrentTake() {
        val state = RecorderState.start(emptyList(), emptySet())
        assertNull(state.current)
        assertTrue(state.finished)
    }
}
