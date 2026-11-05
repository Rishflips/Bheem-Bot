package app.bheem.core.assistant

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactGroundingTest {
    @Test
    fun aFactGroundsWhenItsContentWordsAreTheOwnersOwn() {
        assertTrue(OfferCheck.factGrounded("I moved to Bengaluru", "I moved to Bengaluru last month"))
        assertTrue(OfferCheck.factGrounded("My sister is called Meena", "my sisters are called Meena and Riya"))
        assertTrue(OfferCheck.factGrounded("I like filter coffee", "I like filter coffee in the morning"))
        assertTrue(OfferCheck.factGrounded("My name is Rish", "my name is Rish"))
    }

    @Test
    fun anInventedPlaceOrPersonIsRejected() {
        assertFalse(OfferCheck.factGrounded("I live in Pune", "I moved to Bengaluru"))
        assertFalse(OfferCheck.factGrounded("My sister is called Meena", "I live in Bengaluru"))
    }

    @Test
    fun aSlotFactNeedsItsSlotWordsInTheOwnersMessage() {
        // "live" is the city slot's own word: visiting a place is not living there
        assertFalse(OfferCheck.factGrounded("I live in Chennai", "I visited Chennai last week"))
        assertFalse(OfferCheck.factGrounded("I live in Bengaluru", "I moved to Bengaluru last month"))
        assertTrue(OfferCheck.factGrounded("I moved to Bengaluru", "I moved to Bengaluru"))
        assertTrue(OfferCheck.factGrounded("I work at a school", "I work at a school near the lake"))
    }

    @Test
    fun aNegationMustBeInBothOrNeither() {
        assertFalse(OfferCheck.factGrounded("I don't like coffee", "I like filter coffee"))
        assertFalse(OfferCheck.factGrounded("I like coffee", "I don't like coffee"))
        assertFalse(OfferCheck.factGrounded("I am not a morning person", "I am a morning person"))
        assertTrue(OfferCheck.factGrounded("I don't like coffee", "I don't like coffee"))
        assertTrue(OfferCheck.factGrounded("I never eat sweets", "I never eat sweets"))
    }

    @Test
    fun hateIsNotANegator() {
        assertTrue(OfferCheck.factGrounded("I hate crowds", "I hate crowds"))
    }

    @Test
    fun aFactOfOnlyFunctionWordsIsRejected() {
        assertFalse(OfferCheck.factGrounded("I am", "I live in Bengaluru"))
        assertFalse(OfferCheck.factGrounded("", "I live in Bengaluru"))
    }
}
