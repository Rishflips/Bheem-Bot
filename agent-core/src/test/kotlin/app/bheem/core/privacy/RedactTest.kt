package app.bheem.core.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RedactTest {
    @Test
    fun describeSaysHowLongTheTextIsAndNothingElse() {
        assertEquals("<13 chars>", Redact.describe("hello Arun 99"))
        assertFalse(Redact.describe("my secret 4242").contains("secret"))
        assertEquals("<0 chars>", Redact.describe(""))
    }

    @Test
    fun aReasonKeepsItsWordsButLosesControlCharactersNewlinesAndLength() {
        assertEquals("amount is out of range", Redact.reason("amount is out of range"))
        assertFalse(Redact.reason("line one\nline two\r\tthree").any { it.isISOControl() })
        val long = Redact.reason("x".repeat(500))
        assertTrue(long.length <= Redact.MAX_REASON + 1)
        assertTrue(long.endsWith("…"))
    }

    @Test
    fun aReasonIsNeverEmpty() {
        assertEquals("no reason recorded", Redact.reason(""))
        assertEquals("no reason recorded", Redact.reason(" \n "))
    }

    @Test
    fun controlAndFormattingCharactersAreRemovedIncludingBellEscapeAndBidiOverride() {
        val dirty = "a" + 7.toChar() + "b" + 27.toChar() + "c" + "‮" + "d" + "​" + "e" + "⁦" + "f"
        val clean = Redact.reason(dirty)
        assertEquals("abcdef", clean.replace(" ", ""))
        assertTrue(clean.none { it.isISOControl() || Character.getType(it) == Character.FORMAT.toInt() }, clean)
    }

    @Test
    fun aLongReasonIsCutOnACodePointBoundary() {
        val emoji = "😀"
        val cut = Redact.reason("x".repeat(Redact.MAX_REASON - 1) + emoji + "tail")
        assertTrue(cut.length <= Redact.MAX_REASON + 1)
        val lone = cut.indices.any { i -> Character.isHighSurrogate(cut[i]) && (i + 1 >= cut.length || !Character.isLowSurrogate(cut[i + 1])) }
        assertFalse(lone, "a half surrogate pair survived")
    }

    @Test
    fun aHugeReasonIsNotProcessedWhole() {
        val started = System.nanoTime()
        Redact.reason("A".repeat(5_000_000))
        assertTrue(System.nanoTime() - started < 500_000_000, "a huge reason took too long")
    }
}
