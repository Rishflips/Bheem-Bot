package app.bheem.core.memory

import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.Gate
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.FactLookup
import app.bheem.core.pipeline.Outcome
import app.bheem.core.proposal.ExecutionResult
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class FactsTest {
    private val now = LocalDateTime.of(2026, 11, 4, 19, 42)

    @Test
    fun theOwnersOwnWordsAboutThemselvesBecomeAFactToRemember() {
        assertEquals("my sister is called Meena", FactRules.remember("remember that my sister is called Meena"))
        assertEquals("I take the 8:10 train", FactRules.remember("Remember I take the 8:10 train."))
        assertEquals("my name is Gowri", FactRules.remember("my name is Gowri"))
        assertEquals("call me Gowri", FactRules.remember("call me Gowri"))
        assertEquals("I live in Chennai", FactRules.remember("I live in Chennai"))
        assertEquals("I like filter coffee", FactRules.remember("I like filter coffee"))
        assertEquals("from now on keep answers short", FactRules.remember("from now on keep answers short"))
    }

    @Test
    fun aReminderOrAPlanIsNotAFact() {
        assertNull(FactRules.remember("remember to call Arun at 9"))
        assertNull(FactRules.remember("remember that Suresh is allergic to peanuts"), "about someone else, so it stays a note")
        assertNull(FactRules.remember("remember to water the plants, add it as a task"))
        assertNull(FactRules.remember("my dentist is on Friday at 4"))
        assertNull(FactRules.remember("my sister is coming tomorrow at 5"))
        assertNull(FactRules.remember("what is my name"))
        assertNull(FactRules.remember("tell me something about volcanoes"))
    }

    @Test
    fun aThingThatHappenedOrARequestIsNotALastingFactEvenWhenItIsAboutTheOwner() {
        assertNull(FactRules.remember("remember that I lent Ravi the blue umbrella"), "an event stays a note")
        assertNull(FactRules.remember("remember I parked on level 3"))
        assertNull(FactRules.remember("remember that my scooter key is in the blue drawer"), "a place to find something is a note")
        assertNull(FactRules.remember("remember that I owe Suresh 500 rupees"))
        assertNull(FactRules.remember("remember that I have a meeting at 4"))
        assertNull(FactRules.remember("I work at home today, remind me to call Arun"))
        assertNull(FactRules.remember("I like the red one, add it to my task list"))
        assertNull(FactRules.remember("I live in Chennai, note that down for tomorrow"))
        assertEquals("I live in Chennai", FactRules.remember("remember that I live in Chennai"))
        assertEquals("I usually wake up at 6", FactRules.remember("remember I usually wake up at 6"))
    }

    @Test
    fun forgettingNamesWhatToForget() {
        assertEquals("my sister", FactRules.forgetQuery("forget my sister"))
        assertEquals("I live in Chennai", FactRules.forgetQuery("forget that I live in Chennai"))
        assertNull(FactRules.forgetQuery("remember my sister"))
    }

    private fun fact(id: String, text: String, daysAgo: Int = 0, kind: RecordKind = RecordKind.FACT) = MemoryRecord(id, kind, text, now.minusDays(daysAgo.toLong()))

    @Test
    fun theMemoryBlockHoldsOnlyFactsAndLeadsWithTheName() {
        val facts = listOf(
            fact("f1", "my sister is called Meena", 5), fact("f2", "I live in Chennai", 9), fact("f3", "my name is Gowri", 20),
            fact("n1", "electrician Sundar charges 600", 1, RecordKind.NOTE), fact("g1", "Owner dislikes mornings", 0, RecordKind.GUESS),
        )
        val block = assertNotNull(MemoryBlock.build(facts, "what is my sister's name"))
        assertTrue(block.startsWith("Things the owner told you earlier:"), block)
        assertTrue(block.lines()[1].contains("Gowri"), "the name comes first: $block")
        assertTrue(block.contains("Meena"), block)
        assertFalse(block.contains("electrician") || block.contains("mornings"), "only facts the owner confirmed: $block")
    }

    @Test
    fun theMemoryBlockKeepsTheNameThenCityAndWorkThenTheMatchingFacts() {
        val facts = listOf(
            fact("f1", "I live in Chennai", 9),
            fact("f2", "I work at Infosys", 8),
            fact("f3", "my name is Gowri", 20),
            fact("f4", "my sister is called Meena", 5),
            fact("f5", "I like filter coffee", 2),
        )
        val lines = assertNotNull(MemoryBlock.build(facts, "what is my sister's name")).lines().drop(1)
        assertTrue(lines[0].contains("Gowri"), lines.toString())
        assertTrue(lines[1].contains("Chennai"), lines.toString())
        assertTrue(lines[2].contains("Infosys"), lines.toString())
        assertTrue(lines[3].contains("Meena"), lines.toString())
    }

    @Test
    fun withNoFactsThereIsNoBlockAndALongListIsCapped() {
        assertNull(MemoryBlock.build(emptyList(), "hello"))
        assertNull(MemoryBlock.build(listOf(fact("n", "a note", 0, RecordKind.NOTE)), "hello"))
        val many = (1..40).map { fact("f$it", "my favourite thing number $it is something quite long that goes on and on and on for a while", it) }
        val block = assertNotNull(MemoryBlock.build(many, "thing"))
        assertTrue(block.length <= 320, block.length.toString())
        assertTrue(block.lines().size <= 5, block)
    }

    @Test
    fun forgettingMatchesEveryMeaningfulWordAndOnlyFacts() {
        val records = listOf(fact("f1", "my sister is called Meena"), fact("f2", "my brother is called Ravi"), fact("n1", "my sister note", 0, RecordKind.NOTE))
        assertEquals(listOf("my sister is called Meena"), MemoryBlock.matching(records, "my sister"))
        assertEquals(listOf("my sister is called Meena", "my brother is called Ravi"), MemoryBlock.matching(records, "called"))
        assertTrue(MemoryBlock.matching(records, "the").isEmpty())
        assertTrue(MemoryBlock.matching(records, "dentist").isEmpty())
    }

    private fun pipeline(lookup: FactLookup? = null, known: Collection<String> = emptyList()): CommandPipeline {
        var n = 0
        return CommandPipeline({ _, _ -> "NoAction" }, Gate(knownFacts = { known }), Ledger(InMemoryLedgerStore()), facts = lookup, newId = { "p${n++}" })
    }

    private fun lookup(vararg facts: String) = object : FactLookup {
        override fun matching(query: String): List<String> = emptyList()

        override fun all(): List<String> = facts.toList()
    }

    private fun handle(p: CommandPipeline, text: String) = runBlocking { p.handle(text, now) }

    @Test
    fun rememberBecomesACardThatWaitsForATap() {
        val card = assertIs<Outcome.Card>(handle(pipeline(), "remember that my sister is called Meena"))
        assertEquals(ProposedAction("RememberFact", mapOf("text" to "my sister is called Meena")), card.action)
        assertTrue(card.requiresTap)
    }

    @Test
    fun aNewCityReplacesTheConfirmedOneAndCarriesItsExactText() {
        val p = pipeline(lookup("I live in Chennai"), listOf("I live in Chennai"))
        val card = assertIs<Outcome.Card>(handle(p, "I live in Bengaluru"))
        assertEquals(ProposedAction("RememberFact", mapOf("text" to "I live in Bengaluru", "replaces" to "I live in Chennai")), card.action)
    }

    @Test
    fun planFactsAlsoAddsTheReplacesArgumentForTheSameSlot() {
        val p = pipeline(lookup("I live in Chennai"), listOf("I live in Chennai"))
        val plan = p.planFacts(listOf("I live in Bengaluru"), now)
        val card = assertIs<Outcome.Card>(plan.cards.single())
        assertEquals("I live in Bengaluru", card.action.args["text"])
        assertEquals("I live in Chennai", card.action.args["replaces"])
    }

    @Test
    fun aFactWithNoSlotAndNoOldFactHasNoReplacesArgument() {
        val p = pipeline(lookup("I live in Chennai"), listOf("I live in Chennai"))
        val card = assertIs<Outcome.Card>(handle(p, "I like tea"))
        assertFalse("replaces" in card.action.args)
    }

    @Test
    fun forgetNeedsExactlyOneMatchingMemory() {
        val one = pipeline(FactLookup { listOf("my sister is called Meena") })
        assertEquals(ProposedAction("ForgetFact", mapOf("text" to "my sister is called Meena")), assertIs<Outcome.Card>(handle(one, "forget my sister")).action)
        assertIs<Outcome.Refused>(handle(pipeline(FactLookup { emptyList() }), "forget my sister"))
        assertIs<Outcome.Refused>(handle(pipeline(FactLookup { listOf("a", "b") }), "forget my sister"))
    }

    @Test
    fun aModelLineForAFactActionIsRefusedBecauseTheModelNeverSuppliesOne() {
        var n = 0
        val p = CommandPipeline({ _, _ -> "RememberFact|the owner hates tea" }, Gate(), Ledger(InMemoryLedgerStore()), newId = { "p${n++}" })
        assertIs<Outcome.Refused>(runBlocking { p.handle("tell me something unrelated", now) })
    }

    @Test
    fun theFactExecutorSavesAFactAndUndoRemovesIt() = runBlocking<Unit> {
        val store = InMemoryMemoryStore()
        val executor = LocalExecutors.fact(store, { "new" }, { now })
        val done = executor.execute(ProposedAction("RememberFact", mapOf("text" to "I live in Chennai")), "p9")
        assertIs<ExecutionResult.Done>(done)
        assertEquals(listOf("I live in Chennai"), store.ofKind(RecordKind.FACT).map { it.text })
        assertTrue(executor.undo(ProposedAction("RememberFact", mapOf("text" to "I live in Chennai")), (done as ExecutionResult.Done).undoToken))
        assertTrue(store.ofKind(RecordKind.FACT).isEmpty())
    }

    @Test
    fun replacingAFactStoresTheNewOneRemovesTheOldAndUndoRestoresIt() = runBlocking<Unit> {
        val store = InMemoryMemoryStore()
        store.add(MemoryRecord("old", RecordKind.FACT, "I live in Chennai", now.minusDays(1)))
        val executor = LocalExecutors.fact(store, { "restored" }, { now })
        val action = ProposedAction("RememberFact", mapOf("text" to "I live in Bengaluru", "replaces" to "I live in Chennai"))
        val done = assertIs<ExecutionResult.Done>(executor.execute(action, "p9"))
        assertEquals(listOf("I live in Bengaluru"), store.ofKind(RecordKind.FACT).map { it.text })
        assertTrue(executor.undo(action, done.undoToken))
        assertEquals(listOf("I live in Chennai"), store.ofKind(RecordKind.FACT).map { it.text })
    }

    @Test
    fun replacingAFactThatIsGoneFailsWithoutStoringTheNewOne() = runBlocking<Unit> {
        val store = InMemoryMemoryStore()
        val executor = LocalExecutors.fact(store, { "new" }, { now })
        val action = ProposedAction("RememberFact", mapOf("text" to "I live in Bengaluru", "replaces" to "I live in Chennai"))
        assertIs<ExecutionResult.Failed>(executor.execute(action, "p9"))
        assertTrue(store.ofKind(RecordKind.FACT).isEmpty())
    }

    @Test
    fun theForgetExecutorRemovesTheFactAndUndoPutsItBack() = runBlocking<Unit> {
        val store = InMemoryMemoryStore()
        store.add(MemoryRecord("f1", RecordKind.FACT, "my sister is called Meena", now))
        store.add(MemoryRecord("n1", RecordKind.NOTE, "my sister is called Meena", now))
        val executor = LocalExecutors.forget(store, { "restored" }, { now })
        val action = ProposedAction("ForgetFact", mapOf("text" to "my sister is called Meena"))
        val done = executor.execute(action, "p9") as ExecutionResult.Done
        assertTrue(store.ofKind(RecordKind.FACT).isEmpty())
        assertEquals(1, store.ofKind(RecordKind.NOTE).size, "a note with the same words is left alone")
        assertTrue(executor.undo(action, done.undoToken))
        assertEquals(listOf("my sister is called Meena"), store.ofKind(RecordKind.FACT).map { it.text })
    }
}
