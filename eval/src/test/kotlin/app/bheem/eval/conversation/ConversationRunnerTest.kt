package app.bheem.eval.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConversationRunnerTest {
    private val fixture = Fixture(
        now = "2026-11-04T19:42",
        notes = listOf(FixtureNote("n1", "Amma's number is 98765 43210", daysAgo = 3)),
        reminders = listOf(FixtureReminder("call Arun", "2026-11-05T09:00"), FixtureReminder("call Priya for lunch", "2026-11-05T12:00")),
    )

    private fun case(vararg turns: ConvTurn) = ConvCase("t", "test", turns = turns.toList())

    private fun play(vararg turns: ConvTurn) = ConversationRunner(fixture).play(case(*turns))

    @Test
    fun aGreetingIsSmallTalkAnsweredByCodeWithNoPrompt() {
        val result = play(ConvTurn("hi")).single()
        assertEquals("smalltalk", result.path)
        assertTrue(result.text.isNotBlank())
        assertNull(result.prompt)
    }

    @Test
    fun anIdentityQuestionGoesToChatWithTheConfirmedFactsAndNotTheNotes() {
        val withFacts = fixture.copy(
            notes = fixture.notes + FixtureNote("n9", "Your name is B H E E M", daysAgo = 1),
            facts = listOf(FixtureNote("f1", "My name is Gowri", daysAgo = 12)),
        )
        val result = ConversationRunner(withFacts).play(case(ConvTurn("what is my name"))).single()
        assertEquals("chat", result.path)
        val prompt = assertNotNull(result.prompt)
        assertTrue(prompt.contains("My name is Gowri"), prompt)
        assertFalse(prompt.contains("B H E E M"), "a note is never put in front of the model as a fact")
        assertEquals("chat", play(ConvTurn("who are you")).single().path)
    }

    @Test
    fun aScheduleQuestionIsRecalledFromTheSeededReminders() {
        val result = play(ConvTurn("what is on tomorrow")).single()
        assertEquals("recall", result.path)
        assertTrue(result.text.contains("call Arun") && result.text.contains("call Priya"), result.text)
        assertEquals(2, result.citations.size)
    }

    @Test
    fun aGeneralQuestionFallsToChatWithTheRealPromptAndNoMemoryInIt() {
        val result = play(ConvTurn("tell me something about volcanoes")).single()
        assertEquals("chat", result.path)
        val prompt = assertNotNull(result.prompt)
        assertTrue(prompt.contains("You are Bheem"), prompt)
        assertTrue(prompt.contains("volcanoes"), prompt)
        assertFalse(prompt.contains("Amma"), "today's chat prompt carries no memory")
        assertTrue(result.promptTokensEstimate!! > 0)
    }

    @Test
    fun aCommandTakesTheScriptedPlannerLineThroughTheRealPipelineAndGate() {
        val result = play(ConvTurn("remind me tomorrow at 9 to call Arun", planner = "CreateReminder|call Arun|2026-11-05T09:00")).single()
        assertEquals("action", result.path)
        assertEquals("CreateReminder", result.action)
        assertEquals("2026-11-05T09:00", result.args["time"])
    }

    @Test
    fun aQuestionThePhoneCanAnswerIsAnsweredByCodeFromTheFixtureClock() {
        val result = play(ConvTurn("what day is it today")).single()
        assertEquals("device", result.path)
        assertTrue(result.text.contains("Wednesday") && result.text.contains("4 November"), result.text)
        assertNull(result.prompt)
    }

    @Test
    fun theFixturePhoneStateIsReadFromTheFixture() {
        val phone = fixture.copy(device = FixtureDevice(batteryPercent = 63, charging = true, network = "WIFI"))
        val result = ConversationRunner(phone).play(case(ConvTurn("is my phone charging"), ConvTurn("tell me something about volcanoes")))
        assertEquals("device", result[0].path)
        assertTrue(result[0].text.contains("63"), result[0].text)
        assertTrue(assertNotNull(result[1].prompt).contains("Now: Wed 4 Nov 2026"), "the chat prompt carries the now line")
    }

    @Test
    fun earlierTurnsReachTheNextChatPrompt() {
        val results = play(ConvTurn("my dentist is on friday at 4"), ConvTurn("what did i just tell you"))
        assertTrue(assertNotNull(results[1].prompt).contains("my dentist is on friday at 4"))
    }

    @Test
    fun theScorerChecksPathActionAndTextOfCodeWrittenReplies() {
        val result = play(ConvTurn("what is on tomorrow")).single()
        val good = ConversationScorer.score(ExpectedTurn(path = "recall", must = listOf("call Arun"), mustNot = listOf("dentist")), result)
        assertTrue(good.passed, good.reasons.toString())
        val wrongPath = ConversationScorer.score(ExpectedTurn(path = "device"), result)
        assertFalse(wrongPath.passed)
        assertTrue(wrongPath.reasons.single().contains("path"))
        val missing = ConversationScorer.score(ExpectedTurn(path = "recall", must = listOf("nothing here")), result)
        assertFalse(missing.passed)
    }

    @Test
    fun textChecksOnAChatReplyNeedTheModelAndAreCountedNotFailed() {
        val result = play(ConvTurn("i had a rough day")).single()
        val score = ConversationScorer.score(ExpectedTurn(path = "chat", mustNot = listOf("I cannot"), judged = true), result)
        assertTrue(score.passed, score.reasons.toString())
        assertTrue(score.needsModel)
    }

    @Test
    fun theSummaryCountsRoutingTextAndNeedsModelPerGroup() {
        val cases = listOf(
            ConvCase("a", "small talk", turns = listOf(ConvTurn("hi", expect = ExpectedTurn(path = "smalltalk")))),
            ConvCase("b", "date and time", turns = listOf(ConvTurn("what day is it today", expect = ExpectedTurn(path = "device", must = listOf("Tuesday"))))),
            ConvCase("c", "persona", turns = listOf(ConvTurn("i had a rough day", expect = ExpectedTurn(path = "chat", judged = true)))),
        )
        val report = ConversationReport.of(cases, ConversationRunner(fixture))
        assertEquals(3, report.total.turns)
        assertEquals(2, report.total.passed)
        assertEquals(1, report.total.needsModel)
        assertEquals(0, report.group("date and time").passed)
        assertEquals(1, report.group("small talk").passed)
    }
}
