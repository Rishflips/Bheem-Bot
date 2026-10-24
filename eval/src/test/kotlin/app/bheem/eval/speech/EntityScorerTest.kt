package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntityScorerTest {
    private val payRavi = CommandCase("cmd-233", "en", "pay Ravi 840 rupees", ExpectedAction("OpenUpiPayment", mapOf("payee" to "Ravi", "amount" to "840")))
    private val reminder = CommandCase(
        "cmd-020", "en", "remind me tomorrow at 9 PM to call Meena",
        ExpectedAction("CreateReminder", mapOf("text" to "call Meena", "time" to "2026-10-24T21:00")),
    )
    private val bigBasket = CommandCase(
        "cmd-074", "en", "I spent 120 rupees at Big Basket",
        ExpectedAction("LogExpense", mapOf("amount" to "120.00", "merchant" to "Big Basket", "direction" to "debit")),
    )

    private fun entities(c: CommandCase) = EntityExtractor.fromCommand(c.text, c.expected)

    @Test
    fun everyEntityRightMeansNoEntityErrors() {
        val score = EntityScorer.score(entities(payRavi), "pay Ravi eight hundred forty rupees")
        assertEquals(2, score.totalCount)
        assertEquals(2, score.correctCount)
        assertEquals(0.0, score.errorRate())
    }

    @Test
    fun aWrongAmountIsOnlyAnAmountError() {
        val score = EntityScorer.score(entities(payRavi), "pay Ravi 849 rupees")
        assertEquals(1, score.correctCount)
        assertEquals(1.0, score.errorRate(EntityKind.AMOUNT))
        assertEquals(0.0, score.errorRate(EntityKind.NAME))
        assertEquals(0.5, score.errorRate())
    }

    @Test
    fun anAmountMustBeTheWholeNumber() {
        assertEquals(1.0, EntityScorer.score(entities(payRavi), "pay Ravi 8400 rupees").errorRate(EntityKind.AMOUNT))
        assertEquals(1.0, EntityScorer.score(entities(payRavi), "pay Ravi 8 40 rupees").errorRate(EntityKind.AMOUNT))
        assertEquals(0.0, EntityScorer.score(entities(payRavi), "pay Ravi 840.00 rupees").errorRate(EntityKind.AMOUNT))
    }

    @Test
    fun aMisheardNameIsANameError() {
        val score = EntityScorer.score(entities(payRavi), "pay Robi 840 rupees")
        assertEquals(1.0, score.errorRate(EntityKind.NAME))
        assertEquals(0.0, score.errorRate(EntityKind.AMOUNT))
    }

    @Test
    fun timeEntitiesCheckTheDayAndTheHourSeparately() {
        assertEquals(0.0, EntityScorer.score(entities(reminder), "remind me tomorrow at nine p.m. to call Meena").errorRate())
        val wrong = EntityScorer.score(entities(reminder), "remind me today at 9 AM to call Meena")
        assertEquals(1.0, wrong.errorRate(EntityKind.TIME))
        assertEquals(0.0, wrong.errorRate(EntityKind.NAME))
        assertEquals(2.0 / 3.0, wrong.errorRate())
        val dayOnly = EntityScorer.score(entities(reminder), "remind me today at 9 pm to call Meena")
        assertEquals(0.5, dayOnly.errorRate(EntityKind.TIME))
    }

    @Test
    fun spellingVariantsOfAMerchantMatchAfterNormalization() {
        assertEquals(0.0, EntityScorer.score(entities(bigBasket), "I spent 120 rupees at big basket").errorRate())
        assertEquals(0.0, EntityScorer.score(entities(bigBasket), "I spent Rs. 120 at BigBasket").errorRate())
    }

    @Test
    fun anEmptyHypothesisGetsEveryEntityWrong() {
        assertEquals(1.0, EntityScorer.score(entities(payRavi), "").errorRate())
    }

    @Test
    fun noEntitiesMeansNoRate() {
        val score = EntityScorer.score(emptyList(), "anything at all")
        assertEquals(0, score.totalCount)
        assertNull(score.errorRate())
        assertNull(EntityScorer.score(entities(payRavi), "pay Ravi 840").errorRate(EntityKind.TIME))
    }

    @Test
    fun theCorpusAddsCountsPerKind() {
        val a = EntityScorer.score(entities(payRavi), "pay Ravi 849 rupees")
        val b = EntityScorer.score(entities(reminder), "remind me tomorrow at 9 pm to call Meena")
        val corpus = EntityScorer.corpus(listOf(a, b))
        assertEquals(5, corpus.totalCount)
        assertEquals(4, corpus.correctCount)
        assertEquals(1.0, corpus.errorRate(EntityKind.AMOUNT))
        assertEquals(0.0, corpus.errorRate(EntityKind.TIME))
        assertEquals(EntityScore.EMPTY, EntityScorer.corpus(emptyList()))
    }

    @Test
    fun anUtteranceScoreCombinesWerAndEntitiesForACommandCase() {
        val score = SpeechScorer.score(payRavi, "pay Ravi 849 rupees")
        assertEquals(WerScore(1, 0, 0, 3), score.wer)
        assertEquals(1, score.entities.correctCount)
        val total = SpeechScorer.corpus(listOf(score, SpeechScorer.score(payRavi, "pay Ravi 840 rupees")))
        assertEquals(WerScore(1, 0, 0, 6), total.wer)
        assertEquals(3, total.entities.correctCount)
        assertEquals(4, total.entities.totalCount)
    }
}
