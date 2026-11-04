package app.bheem.core.memory

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneBookTest {
    private val at = LocalDateTime.of(2026, 10, 20, 9, 0)
    private fun note(id: String, text: String) = MemoryRecord(id, RecordKind.NOTE, text, at)

    @Test
    fun aFactNoteGivesANumberForAName() {
        val book = PhoneBook(listOf(note("n1", "Amma's number is +91 98765 43210")))
        assertEquals("+919876543210", book.find("Amma"))
    }

    @Test
    fun severalWaysOfWritingItAreUnderstood() {
        val book = PhoneBook(
            listOf(
                note("n1", "Ravi number 98765-11111"),
                note("n2", "Meena: 9876522222"),
                note("n3", "Suresh phone number is 0441234567"),
                note("n4", "Dr Rao mobile no. 9876533333"),
            ),
        )
        assertEquals("9876511111", book.find("Ravi"))
        assertEquals("9876522222", book.find("Meena"))
        assertEquals("0441234567", book.find("Suresh"))
        assertEquals("9876533333", book.find("Dr Rao"))
    }

    @Test
    fun theNameMatchIgnoresCaseAndAnUnknownNameHasNoNumber() {
        val book = PhoneBook(listOf(note("n1", "Amma's number is 9876543210")))
        assertEquals("9876543210", book.find("amma"))
        assertNull(book.find("Appa"))
    }

    @Test
    fun theNewestNoteWinsWhenANumberChanged() {
        val book = PhoneBook(
            listOf(
                MemoryRecord("old", RecordKind.NOTE, "Amma's number is 9876543210", at),
                MemoryRecord("new", RecordKind.NOTE, "Amma's number is 9123456789", at.plusDays(3)),
            ),
        )
        assertEquals("9123456789", book.find("Amma"))
    }

    @Test
    fun aTextThatOnlyLooksLikeANumberIsIgnored() {
        val book = PhoneBook(listOf(note("n1", "Amma's number is 12"), note("n2", "buy 2 kg rice number 9876543210 maybe tomorrow")))
        assertNull(book.find("Amma"))
        assertNull(book.find("rice"))
    }
}
