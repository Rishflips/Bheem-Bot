package app.bheem.core.assistant

import app.bheem.core.memory.InterviewFacts
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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
    @Test
    fun answerShapedPhrasesAreNotCommands() {
        for (text in listOf("Open University", "Open to anything", "Call centre work", "Book reading")) {
            assertFalse(Interview.isCommand(text), text)
        }
        assertTrue(Interview.isCommand("Call Amma"))
        assertTrue(Interview.isCommand("Open WhatsApp"))
        assertTrue(Interview.isCommand("Book a call with Suresh"))
    }

    @Test
    fun anInterviewResumesAtTheNextQuestionWithItsAnswers() {
        val interview = Interview()
        interview.start()
        interview.answer("Rish")
        interview.skip()
        val stored = InterviewStateCodec.encode(interview.pairs(), interview.nextIndex())
        val resumed = InterviewStateCodec.decode(stored)
        assertEquals(listOf(Interview.QUESTIONS[0] to "Rish"), resumed.pairs)
        assertEquals(2, resumed.nextIndex)

        val next = Interview()
        next.resume(resumed.pairs, resumed.nextIndex)
        assertEquals(Interview.QUESTIONS[2], next.currentQuestion())
        assertTrue(next.isActive())
    }

    @Test
    fun interviewFactsAreKeptOnlyWhenTheAnswerGroundsThem() {
        val city = listOf(Interview.QUESTIONS[1] to "Chennai")
        assertEquals(listOf("I live in Chennai"), Interview.groundedFacts(city, listOf("I live in Chennai")))
        assertEquals(listOf("I live in Chennai"), Interview.groundedFacts(city, listOf("I live in Pune")))

        val moved = listOf(Interview.QUESTIONS[1] to "Bengaluru")
        assertEquals(listOf("I live in Bengaluru"), Interview.groundedFacts(moved, listOf("I moved to Bengaluru")))
    }

    @Test
    fun aNameComesOnlyFromTheFactsThatWereKept() {
        assertEquals("Rish", Interview.nameIn(listOf("I live in Chennai", "My name is Rish")))
        assertNull(Interview.nameIn(listOf("I live in Chennai")))
    }

}
