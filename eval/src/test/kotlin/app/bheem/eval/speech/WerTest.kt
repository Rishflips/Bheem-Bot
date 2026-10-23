package app.bheem.eval.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WerTest {
    private fun score(reference: String, hypothesis: String) =
        Wer.score(reference.split(" ").filter { it.isNotEmpty() }, hypothesis.split(" ").filter { it.isNotEmpty() })

    @Test
    fun identicalTextHasNoErrors() {
        assertEquals(WerScore(0, 0, 0, 3), score("pay ravi 840", "pay ravi 840"))
        assertEquals(0.0, score("pay ravi 840", "pay ravi 840").rate)
    }

    @Test
    fun countsSubstitutionsDeletionsAndInsertionsSeparately() {
        assertEquals(WerScore(1, 0, 0, 3), score("pay ravi 840", "pay arun 840"))
        assertEquals(WerScore(0, 1, 0, 3), score("pay ravi 840", "pay 840"))
        assertEquals(WerScore(0, 0, 1, 2), score("pay 840", "pay ravi 840"))
    }

    @Test
    fun theTotalIsTheMinimumNumberOfEdits() {
        assertEquals(3, score("a b c d", "a x d e").errors)
        assertEquals(4, score("one two three", "four five six seven").errors)
    }

    @Test
    fun anEmptyHypothesisDeletesEverythingAndAnEmptyReferenceHasNoRate() {
        assertEquals(WerScore(0, 3, 0, 3), score("pay ravi 840", ""))
        assertEquals(1.0, score("pay ravi 840", "").rate)
        assertEquals(WerScore(0, 0, 2, 0), score("", "um yes"))
        assertNull(score("", "um yes").rate)
        assertNull(score("", "").rate)
    }

    @Test
    fun theRateCanExceedOneWhenThereAreManyInsertions() {
        assertEquals(2.0, score("a", "a b c").rate)
    }

    @Test
    fun scoreTextNormalizesBothSidesFirst() {
        assertEquals(0, Wer.scoreText("Pay Ravi Rs. 840", "pay ravi eight hundred and forty rupees").errors)
        assertEquals(1, Wer.scoreText("Pay Ravi Rs. 840", "pay ravi 849").errors)
    }

    @Test
    fun theCorpusRateIsTotalErrorsOverTotalReferenceWordsNotAMeanOfRates() {
        val corpus = Wer.corpus(listOf(score("a b c d", "a b c x"), score("e", "e")))
        assertEquals(WerScore(1, 0, 0, 5), corpus)
        assertEquals(0.2, corpus.rate)
        assertEquals(WerScore(0, 0, 0, 0), Wer.corpus(emptyList()))
    }
}
