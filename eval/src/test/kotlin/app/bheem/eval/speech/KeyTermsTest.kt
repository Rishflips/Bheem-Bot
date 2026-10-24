package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.gen.CommandGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyTermsTest {
    private val english = CommandGenerator.generate().filter { it.lang == "en" }
    private val expectedNames = setOf(
        "Anita", "Ravi", "Arun", "Meena", "Vivek", "Karthik", "Suresh", "Priya",
        "Swiggy", "Zomato", "Uber", "Big Basket", "Murugan Idli", "Reliance Fresh",
    )

    private fun case(text: String, action: String, vararg args: Pair<String, String>) =
        CommandCase("c", "en", text, ExpectedAction(action, args.toMap()))

    @Test
    fun theNamesAndMerchantsOfTheCommandSetComeBackInTheirOwnSpelling() {
        assertEquals(expectedNames, KeyTerms.fromCommands(english).toSet())
    }

    @Test
    fun eachTermAppearsOnceAndNoneHasAComma() {
        val terms = KeyTerms.fromCommands(english)
        assertEquals(terms.size, terms.map { it.lowercase() }.toSet().size)
        assertTrue(terms.none { ',' in it })
    }

    @Test
    fun aPossessiveBecomesTheName() {
        val terms = KeyTerms.fromCommands(listOf(case("save a note: Ravi's birthday is on 12 December", "SaveNote", "text" to "Ravi's birthday is on 12 December")))
        assertEquals(listOf("Ravi"), terms)
    }

    @Test
    fun smallTalkChannelsMonthsAndTheFirstWordAreNotTerms() {
        val terms = KeyTerms.fromCommands(
            listOf(
                case("what is the capital of France", "NoAction"),
                case("message Arun on WhatsApp that I am late", "DraftMessage", "channel" to "whatsapp", "to" to "Arun", "text" to "I am late"),
                case("Remind me in December to call Priya", "CreateReminder", "text" to "call Priya", "time" to "2026-12-01T09:00"),
            ),
        )
        assertEquals(listOf("Arun", "Priya"), terms)
    }

    @Test
    fun decoysAreCommonNamesThatNeverOverlapTheCommandSet() {
        val decoys = KeyTerms.decoys
        assertTrue(decoys.size >= 50, "${decoys.size} decoys")
        assertEquals(decoys.size, decoys.map { it.lowercase() }.toSet().size)
        assertTrue(decoys.none { ',' in it })
        assertTrue(decoys.all { it.first().isUpperCase() })
        assertTrue(decoys.map { it.lowercase() }.toSet().intersect(expectedNames.map { it.lowercase() }.toSet()).isEmpty())
    }

    @Test
    fun theModeDecidesWhichTermsAreUsed() {
        val names = KeyTerms.fromCommands(english)
        assertEquals(emptyList(), KeyTerms.forMode(KeyTermMode.OFF, english))
        assertEquals(names, KeyTerms.forMode(KeyTermMode.NAMES, english))
        assertEquals(names + KeyTerms.decoys, KeyTerms.forMode(KeyTermMode.DECOYS, english))
    }

    @Test
    fun theTagNamesTheModeInResultIds() {
        assertEquals("", KeyTermMode.OFF.tag)
        assertEquals("+names", KeyTermMode.NAMES.tag)
        assertEquals("+decoys", KeyTermMode.DECOYS.tag)
        assertFalse(KeyTermMode.entries.any { it.tag.endsWith("-live") })
    }
}
