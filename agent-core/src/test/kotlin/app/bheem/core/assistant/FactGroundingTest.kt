package app.bheem.core.assistant

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactGroundingTest {
    @Test
    fun aFactGroundsWhenItsContentWordsAreTheOwnersOwn() {
        assertTrue(OfferCheck.factGrounded("I moved to Bengaluru", "I moved to Bengaluru last month"))
        assertTrue(OfferCheck.factGrounded("I live in Bengaluru", "I moved to Bengaluru last month"))
        assertTrue(OfferCheck.factGrounded("My sister is called Meena", "my sisters are called Meena and Riya"))
        assertTrue(OfferCheck.factGrounded("I like filter coffee", "I like filter coffee in the morning"))
    }

    @Test
    fun anInventedPlaceOrPersonIsRejected() {
        assertFalse(OfferCheck.factGrounded("I live in Pune", "I moved to Bengaluru"))
        assertFalse(OfferCheck.factGrounded("My sister is called Meena", "I live in Bengaluru"))
    }

    @Test
    fun aFactOfOnlyFunctionWordsIsRejected() {
        assertFalse(OfferCheck.factGrounded("I am", "I live in Bengaluru"))
        assertFalse(OfferCheck.factGrounded("", "I live in Bengaluru"))
    }
}
