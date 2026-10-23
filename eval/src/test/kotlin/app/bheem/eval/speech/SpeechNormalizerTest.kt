package app.bheem.eval.speech

import kotlin.test.Test
import kotlin.test.assertEquals

class SpeechNormalizerTest {
    private fun n(s: String) = SpeechNormalizer.normalize(s)

    @Test
    fun lowercasesAndDropsPunctuation() {
        assertEquals("remind me tomorrow at 9 am to call anita", n("Remind me, tomorrow at 9 AM, to call Anita."))
        assertEquals("", n(""))
        assertEquals("", n(" ...  "))
    }

    @Test
    fun apostrophesInsideWordsAreDropped() {
        assertEquals("ravis birthday", n("Ravi's birthday"))
        assertEquals("ravis birthday", n("Ravi’s birthday"))
        assertEquals("dont", n("don't"))
    }

    @Test
    fun currencyMarkersAreDroppedSoEveryWayOfSayingAnAmountMatches() {
        val spoken = listOf(
            "I spent 840 rupees at Swiggy",
            "I spent Rs 840 at Swiggy",
            "I spent Rs. 840 at Swiggy",
            "I spent ₹840 at Swiggy",
            "I spent 840/- at Swiggy",
            "I spent INR 840 at Swiggy",
        )
        for (s in spoken) assertEquals("i spent 840 at swiggy", n(s), s)
    }

    @Test
    fun thousandSeparatorsAndTrailingZeroDecimalsAreRemoved() {
        assertEquals("pay priya 2300", n("pay Priya 2,300 rupees"))
        assertEquals("1250", n("1,250"))
        assertEquals("125000", n("1,25,000"))
        assertEquals("840", n("840.00"))
        assertEquals("840.50", n("840.50"))
    }

    @Test
    fun numberWordsBecomeDigits() {
        assertEquals("pay ravi 840", n("pay Ravi eight hundred and forty rupees"))
        assertEquals("2300", n("two thousand three hundred"))
        assertEquals("1250", n("one thousand two hundred and fifty"))
        assertEquals("5000", n("five thousand"))
        assertEquals("999", n("nine hundred ninety nine"))
        assertEquals("1200", n("twelve hundred"))
        assertEquals("120", n("a hundred and twenty"))
        assertEquals("25", n("twenty five"))
        assertEquals("i will reach in 10 minutes", n("I will reach in ten minutes"))
    }

    @Test
    fun digitsSpokenOneByOneAreJoined() {
        assertEquals("pay arun 840", n("pay Arun eight four zero"))
        assertEquals("999", n("nine nine nine"))
    }

    @Test
    fun numberWordsInsideOrdinaryTextStayApart() {
        assertEquals("1 of them", n("one of them"))
        assertEquals("add a task", n("add a task"))
        assertEquals("a meeting with ravi", n("a meeting with Ravi"))
    }

    @Test
    fun meridiemVariantsAreUnified() {
        for (s in listOf("9 AM", "9am", "9 a.m.", "9 a m", "nine a.m.", "9:00 AM", "nine am")) {
            assertEquals("at 9 am", n("at $s"), s)
        }
        assertEquals("at 6 pm", n("at 6 p m"))
        assertEquals("at 9:30 pm", n("at 9:30 PM"))
    }

    @Test
    fun aLoneLetterAandMWordsAreNotMistakenForAMeridiem() {
        assertEquals("a m", n("a m"))
        assertEquals("a meeting", n("a meeting"))
    }

    @Test
    fun fillersAreDropped() {
        assertEquals("remind me tomorrow", n("um remind me uh tomorrow"))
        assertEquals("remind me", n("remind me hmm"))
    }

    @Test
    fun equivalentSpellingsOfKnownCompoundsMatch() {
        assertEquals("message arun on whatsapp", n("message Arun on WhatsApp"))
        assertEquals("message arun on whatsapp", n("message Arun on whats app"))
        assertEquals("the wifi password", n("the Wi-Fi password"))
        assertEquals("the wifi password", n("the wifi password"))
        assertEquals("i spent 120 at bigbasket", n("I spent 120 rupees at Big Basket"))
        assertEquals("i spent 120 at bigbasket", n("I spent 120 rupees at BigBasket"))
    }

    @Test
    fun tokensAreTheNormalizedTextSplitOnSpaces() {
        assertEquals(listOf("pay", "meena", "840"), SpeechNormalizer.tokens("Pay Meena Rs. 840!"))
    }
}
