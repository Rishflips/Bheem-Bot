package app.bheem.core.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpokenTextTest {
    @Test
    fun aBriefIsReadAsSentencesWithoutListMarks() {
        val brief = "Your day, Wed 4 Nov:\nDue today:\n- 5:00 PM call Arun\n- 7:00 PM call the bank\nClashes:\n- 7:00 PM call Shankar and 7:00 PM call the bank are 0 minutes apart"
        assertEquals(
            "Your day, Wed 4 Nov. Due today. 5:00 PM call Arun. 7:00 PM call the bank. Clashes. 7:00 PM call Shankar and 7:00 PM call the bank are 0 minutes apart.",
            SpokenText.of(brief),
        )
    }

    @Test
    fun aSourceLineIsNeverRead() {
        assertEquals("You have 1 task. buy milk.", SpokenText.of("You have 1 task:\n- buy milk\nFrom: task 4 Nov"))
    }

    @Test
    fun aPlainSentenceIsReadAsItIs() {
        assertEquals("GST is a tax on goods and services.", SpokenText.of("GST is a tax on goods and services."))
    }

    @Test
    fun aLongAnswerIsCutAtASentenceEnd() {
        val spoken = SpokenText.of("This is a sentence. ".repeat(60))!!
        assertTrue(spoken.endsWith(SpokenText.MORE), spoken)
        assertTrue(spoken.removeSuffix(" " + SpokenText.MORE).length <= 450, spoken.length.toString())
        assertEquals("GST is a tax.", SpokenText.of("GST is a tax."))
    }

    @Test
    fun nothingToSayIsNull() {
        assertNull(SpokenText.of(""))
        assertNull(SpokenText.of("   \n- \n"))
        assertNull(SpokenText.of("From: reminder 4 Nov"))
    }

    @Test
    fun controlCharactersAreDropped() {
        assertEquals("Hello world.", SpokenText.of("Hello\u0007 world"))
    }
}
