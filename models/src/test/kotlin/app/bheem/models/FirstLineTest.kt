package app.bheem.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FirstLineTest {
    @Test
    fun itKeepsReadingUntilTheFirstLineIsFinished() {
        val line = FirstLine()
        assertFalse(line.accept("Remind me"))
        assertFalse(line.accept(" to call Arun"))
        assertEquals("Remind me to call Arun", line.text)
    }

    @Test
    fun aNewlineEndsTheLineAndLaterTextIsDropped() {
        val line = FirstLine()
        assertFalse(line.accept("Remind me to call Arun"))
        assertTrue(line.accept(" tomorrow\nOkay, anything else?"))
        assertEquals("Remind me to call Arun tomorrow", line.text)
    }

    @Test
    fun aLeadingBlankLineDoesNotEndTheReply() {
        val line = FirstLine()
        assertFalse(line.accept("\n"))
        assertFalse(line.accept("  \r\n"))
        assertTrue(line.accept("Buy milk\n"))
        assertEquals("Buy milk", line.text)
    }

    @Test
    fun aReplyWithoutANewlineNeverStops() {
        val line = FirstLine()
        repeat(200) { assertFalse(line.accept("word ")) }
        assertTrue(line.text.startsWith("word word"))
    }

    @Test
    fun anEmptyChunkChangesNothing() {
        val line = FirstLine()
        assertFalse(line.accept(""))
        assertEquals("", line.text)
    }
}
