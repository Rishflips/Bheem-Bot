package app.bheem.core.assistant

import app.bheem.core.memory.InterviewFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InterviewTest {
    @Test
    fun questionsAreAskedOneAtATimeInOrder() {
        val interview = Interview()
        interview.start()
        for ((index, question) in Interview.QUESTIONS.withIndex()) {
            assertEquals(question, interview.currentQuestion())
            assertEquals(Interview.Result.ANSWERED, interview.answer("answer $index"))
        }
        assertTrue(interview.isDone())
        assertEquals(Interview.QUESTIONS.size, interview.pairs().size)
    }

    @Test
    fun skipMovesOnWithoutAnAnswer() {
        val interview = Interview()
        interview.start()
        assertEquals(Interview.Result.ANSWERED, interview.answer("Rish"))
        assertEquals(Interview.Result.SKIPPED, interview.answer("skip"))
        assertEquals(Interview.QUESTIONS[2], interview.currentQuestion())
        assertEquals(listOf(Interview.QUESTIONS[0] to "Rish"), interview.pairs())
    }

    @Test
    fun stopEndsEarlyAndKeepsTheAnswersSoFar() {
        val interview = Interview()
        interview.start()
        interview.answer("Rish")
        interview.answer("Chennai")
        assertEquals(Interview.Result.STOPPED, interview.answer("later"))
        assertTrue(interview.isDone())
        assertEquals(listOf(Interview.QUESTIONS[0] to "Rish", Interview.QUESTIONS[1] to "Chennai"), interview.pairs())
    }

    @Test
    fun aCommandDoesNotConsumeTheQuestion() {
        val interview = Interview()
        interview.start()
        assertEquals(Interview.Result.COMMAND, interview.answer("remind me at 5 to call Amma"))
        assertEquals(Interview.QUESTIONS[0], interview.currentQuestion())
        assertTrue(interview.pairs().isEmpty())
        assertEquals(Interview.Result.ANSWERED, interview.answer("Rish"))
    }

    @Test
    fun fallbackFactsUseTheQuestionTopicForLaterAnswers() {
        val pairs = listOf(
            Interview.QUESTIONS[0] to "Rish",
            Interview.QUESTIONS[1] to "Chennai",
            Interview.QUESTIONS[2] to "I teach at a school",
            Interview.QUESTIONS[3] to "Meena and Arun",
            Interview.QUESTIONS[4] to "I wake at 6",
            Interview.QUESTIONS[5] to "Filter coffee",
            Interview.QUESTIONS[6] to "A house of my own",
            Interview.QUESTIONS[7] to "Tamil and English",
        )
        assertEquals(
            listOf(
                "My name is Rish",
                "I live in Chennai",
                "Work: I teach at a school",
                "People: Meena and Arun",
                "Routine: I wake at 6",
                "Preferences: Filter coffee",
                "Goals: A house of my own",
                "Languages: Tamil and English",
            ),
            Interview.fallbackFacts(pairs),
        )
    }

    @Test
    fun fallbackFactsAreCappedAtOneHundredAndTwentyCharacters() {
        val long = "a".repeat(300)
        val fact = Interview.fallbackFacts(listOf(Interview.QUESTIONS[2] to long)).single()
        assertTrue(fact.length <= 120, fact)
    }

    @Test
    fun factLinesAreParsedAndDedupedAgainstKnownFacts() {
        val raw = """
            fact|My name is Rish
            fact|My name is Rish
            fact|I live in Chennai
            something else
            fact|x
            fact|I live in Chennai
            fact|See http://example.com
        """.trimIndent()
        assertEquals(listOf("I live in Chennai"), InterviewFacts.parse(raw, existing = setOf("my name is rish")))
    }

    @Test
    fun theParseKeepsAtMostTwelveFacts() {
        val raw = (1..15).joinToString("\n") { "fact|Fact number $it" }
        val facts = InterviewFacts.parse(raw, emptySet())
        assertEquals(12, facts.size)
        assertEquals("Fact number 12", facts.last())
        assertFalse("Fact number 13" in facts)
    }
}
