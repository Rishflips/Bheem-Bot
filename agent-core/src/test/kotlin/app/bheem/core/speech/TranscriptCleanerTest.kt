package app.bheem.core.speech

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptCleanerTest {
    private fun clean(s: String) = TranscriptCleaner.clean(s)

    @Test
    fun aPlainTranscriptIsKept() {
        assertEquals("pay Ravi 840 rupees", clean("pay Ravi 840 rupees"))
        assertEquals("pay Ravi 840 rupees", clean("  pay Ravi 840 rupees \n"))
    }

    @Test
    fun wrappingQuotesAreRemovedButQuotesInsideStay() {
        assertEquals("pay Ravi 840 rupees", clean("\"pay Ravi 840 rupees\""))
        assertEquals("pay Ravi 840 rupees", clean("“pay Ravi 840 rupees”"))
        assertEquals("pay Ravi 840 rupees", clean("`pay Ravi 840 rupees`"))
        assertEquals("say \"hello\" to Ravi", clean("say \"hello\" to Ravi"))
    }

    @Test
    fun aLeadingLabelIsRemoved() {
        assertEquals("pay Ravi 840 rupees", clean("Transcript: pay Ravi 840 rupees"))
        assertEquals("pay Ravi 840 rupees", clean("transcription: \"pay Ravi 840 rupees\""))
    }

    @Test
    fun codeFencesAndLineBreaksBecomeOneLine() {
        assertEquals("pay Ravi 840 rupees", clean("```\npay Ravi\n840 rupees\n```"))
        assertEquals("pay Ravi 840 rupees", clean("```text\npay Ravi 840 rupees\n```"))
    }

    @Test
    fun thinkingBlocksAreRemoved() {
        assertEquals("pay Ravi 840 rupees", clean("<think>the speaker says pay</think>pay Ravi 840 rupees"))
    }

    @Test
    fun blankOutputGivesAnEmptyTranscript() {
        assertEquals("", clean(""))
        assertEquals("", clean("  \n "))
        assertEquals("", clean("\"\""))
    }
}
