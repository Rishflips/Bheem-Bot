package app.bheem.eval.speech

import kotlin.test.Test
import kotlin.test.assertEquals

class StreamLinesTest {
    @Test
    fun theLatestTextOfEachLineIsKeptInTheOrderTheLinesFirstAppeared() {
        val lines = StreamLines()
        lines.update(1, 10, "pay")
        lines.update(1, 10, "pay ravi")
        lines.update(1, 11, "840 rupees")
        lines.update(1, 10, "pay Ravi")
        assertEquals("pay Ravi 840 rupees", lines.take(1))
    }

    @Test
    fun streamsDoNotMix() {
        val lines = StreamLines()
        lines.update(1, 10, "one")
        lines.update(2, 10, "two")
        assertEquals("two", lines.take(2))
        assertEquals("one", lines.take(1))
    }

    @Test
    fun takingEmptiesTheStreamAndAnUnknownStreamIsEmpty() {
        val lines = StreamLines()
        lines.update(1, 10, "one")
        lines.take(1)
        assertEquals("", lines.take(1))
        assertEquals("", lines.take(99))
    }

    @Test
    fun blankTextAndExtraSpacesAreIgnored() {
        val lines = StreamLines()
        lines.update(1, 10, "  pay Ravi ")
        lines.update(1, 11, "   ")
        lines.update(1, 12, "840")
        assertEquals("pay Ravi 840", lines.take(1))
    }
}
