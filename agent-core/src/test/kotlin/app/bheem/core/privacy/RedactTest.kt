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
        assertEquals("a b c", Redact.reason("a\nb\u0007c".replace('\u0007', ' ')))
        assertFalse(Redact.reason("line one\nline two\r\tthree").any { it.isISOControl() })
        val long = Redact.reason("x".repeat(500))
        assertTrue(long.length <= Redact.MAX_REASON + 1)
        assertTrue(long.endsWith("\u2026"))
    }

    @Test
    fun aReasonIsNeverEmpty() {
        assertEquals("no reason recorded", Redact.reason(""))
        assertEquals("no reason recorded", Redact.reason(" \n "))
    }
}
