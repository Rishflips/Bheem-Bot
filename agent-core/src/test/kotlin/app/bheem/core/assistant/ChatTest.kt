package app.bheem.core.assistant

import app.bheem.core.memory.Citation
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelState
import app.bheem.models.host.ModelUnavailable
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class ChatTest {
    private val at = LocalDateTime.of(2026, 10, 23, 8, 0)

    private class FakeEngine(var reply: String = "GST is a tax on goods and services.", val pieces: List<String> = emptyList()) : LlmEngine {
        var request: GenerationRequest? = null

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            this.request = request
            pieces.forEach { request.onText?.invoke(it) }
            return Completion(reply, 500, 3000, 12)
        }

        override suspend fun unload() = Unit
    }

    private class FakeHost(val engine: LlmEngine, var unavailable: ModelUnavailable? = null) : ModelHost {
        var acquired = 0
        var released = 0
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Unloaded)

        override suspend fun acquire(): LlmEngine {
            unavailable?.let { throw it }
            acquired++
            return engine
        }

        override fun release() {
            released++
        }

        override fun trim() = Unit

        override suspend fun shutdown() = Unit

        override fun abortLoad(reason: String) = Unit
    }

    private fun replier(engine: FakeEngine = FakeEngine()) = ChatReplier(FakeHost(engine)) to engine

    @Test
    fun theRequestIsShortAndTheSystemPromptStatesWhatChatCannotDo() = runBlocking<Unit> {
        val (chat, engine) = replier()
        assertEquals("GST is a tax on goods and services.", chat.reply(emptyList(), "explain GST"))
        val sent = checkNotNull(engine.request)
        assertEquals(160, sent.maxOutputTokens)
        assertEquals(0.6, sent.temperature)
        assertEquals("explain GST", sent.user)
        for (rule in listOf("2 to 5 sentences", "cannot check live information", "never say that you did", "remind me tomorrow at 9 to call Arun")) {
            assertTrue(rule in sent.system, rule)
        }
        assertFalse("CreateReminder" in sent.system)
    }

    @Test
    fun theLastFewTurnsGoIntoTheMessageOldestFirstWithoutBriefsOrPlans() = runBlocking<Unit> {
        val (chat, engine) = replier()
        val history = listOf(
            Turn.User("first", false, at), Turn.Brief("Your day, Fri 23 Oct", emptyList(), at), Turn.Answer("old answer", emptyList(), at),
            Turn.Plan("plan", listOf("p1"), emptyList(), at), Turn.User("what is GST", false, at), Turn.Chat("GST is a tax.", at), Turn.Notice("note", at),
            Turn.User("and VAT", false, at),
        )
        chat.reply(history, "make it shorter")
        val sent = checkNotNull(engine.request).user
        assertTrue(sent.startsWith("Earlier in this conversation:"), sent)
        assertTrue(sent.contains("You: what is GST\nBheem: GST is a tax.\nBheem: note\nYou: and VAT"), sent)
        assertFalse("Your day" in sent || "plan" in sent.substringAfter("Earlier"), sent)
        assertEquals(6, sent.lines().count { it.startsWith("You:") || it.startsWith("Bheem:") }, sent)
        assertTrue(sent.endsWith("The owner now says: make it shorter"), sent)
    }

    @Test
    fun aLongTurnIsCutBeforeItGoesIntoThePrompt() = runBlocking<Unit> {
        val (chat, engine) = replier()
        chat.reply(listOf(Turn.User("x".repeat(2000), false, at)), "y".repeat(2000))
        val sent = checkNotNull(engine.request).user
        assertTrue(sent.length < 1200, sent.length.toString())
    }

    @Test
    fun markdownAndControlCharactersAreStrippedFromTheAnswer() = runBlocking<Unit> {
        val (chat, _) = replier(FakeEngine("**GST** is a tax.\n\n- on goods\n- on services\u0007 `ok` # done"))
        assertEquals("GST is a tax. on goods on services ok # done", chat.reply(emptyList(), "explain GST"))
    }

    @Test
    fun aTooLongAnswerIsCutAtASentenceEnd() {
        val long = "This is a sentence. ".repeat(100)
        val cleaned = ChatText.clean(long)
        assertTrue(cleaned.length <= 1400, cleaned.length.toString())
        assertTrue(cleaned.endsWith("."), cleaned)
    }

    @Test
    fun anEmptyAnswerBecomesAnHonestOne() {
        assertEquals("I do not have an answer for that.", ChatText.clean("  \n "))
    }

    @Test
    fun aClaimOfHavingDoneSomethingIsReplacedBecauseChatDoesNothing() = runBlocking<Unit> {
        for (claim in listOf("I have set a reminder for 5 pm.", "Done! I've saved that note.", "Your reminder has been set.", "I will remind you tomorrow.", "I have added it to your calendar.")) {
            val (chat, _) = replier(FakeEngine(claim))
            assertEquals("I did not do that. To make something happen, say it as a command, for example: remind me tomorrow at 9 to call Arun.", chat.reply(emptyList(), "please"), claim)
        }
    }

    @Test
    fun textIsShownWhileItIsWrittenAndCleanedLikeTheFinalAnswer() = runBlocking<Unit> {
        val (chat, _) = replier(FakeEngine(pieces = listOf("**GST**", "**GST** is a", "**GST** is a tax.")))
        val seen = mutableListOf<String>()
        chat.reply(emptyList(), "explain GST") { seen += it }
        assertEquals(listOf("GST", "GST is a", "GST is a tax."), seen)
    }

    @Test
    fun theModelIsReleasedAndAnUnavailableModelIsPassedOn() = runBlocking<Unit> {
        val host = FakeHost(FakeEngine())
        ChatReplier(host).reply(emptyList(), "hello there friend")
        assertEquals(1, host.acquired)
        assertEquals(1, host.released)
        val down = FakeHost(FakeEngine(), ModelUnavailable("the model is not on this phone yet"))
        assertEquals("the model is not on this phone yet", assertFailsWith<ModelUnavailable> { ChatReplier(down).reply(emptyList(), "hello") }.message)
    }

    @Test
    fun theCitationsOfAnAnswerTurnNeverReachTheModel() = runBlocking<Unit> {
        val (chat, engine) = replier()
        chat.reply(listOf(Turn.Answer("You have 1 task:\n- buy milk", listOf(Citation("t1", "task", at)), at)), "thanks")
        assertFalse("t1" in checkNotNull(engine.request).user)
    }

    @Test
    fun morePhrasingsOfHavingActedAreReplacedAndExplanationsAreKept() {
        for (claim in listOf("I'll set a reminder for 5 pm.", "Reminder set for 5 pm.", "Added to your tasks.", "Calling Arun now.", "Opening maps for you.", "I am sending the message.", "Sure. Setting a reminder now.")) {
            assertEquals(ChatText.NOT_DONE, ChatText.clean(claim), claim)
        }
        for (fine in listOf("A task was created by the scheduler when the timer ends.", "The message is now sent to the server when you press the button.", "A reminder app lets you set alerts.")) {
            assertEquals(fine, ChatText.clean(fine), fine)
        }
    }

    @Test
    fun hashAndFormatCharactersAreHandledWithCare() {
        assertEquals("C# is a language.", ChatText.clean("C# is a language."))
        assertEquals("Hello", ChatText.clean("## Hello"))
        assertEquals("Hello world", ChatText.clean("Hello‮ world"))
    }

    @Test
    fun anAnswerThatStoppedMidSentenceIsCutBackToTheLastFullSentence() {
        val whole = "GST is a tax. ".repeat(25).trim()
        assertEquals(whole, ChatText.clean("$whole It is levied on the sup"))
        assertEquals("GST is a tax. It is levied on the supply", ChatText.clean("GST is a tax. It is levied on the supply"))
    }

    @Test
    fun healthMoneyAndLegalQuestionsGetAFixedCautionAndOtherQuestionsDoNot() = runBlocking<Unit> {
        val (chat, _) = replier(FakeEngine("Adults often take 500 mg."))
        val reply = chat.reply(emptyList(), "what is the dose of paracetamol")
        assertTrue(reply.endsWith(ChatText.CAUTION), reply)
        assertFalse(ChatText.CAUTION in replier().first.reply(emptyList(), "explain GST"))
        for (text in listOf("should I invest in stocks", "how do I appeal in court", "what are the symptoms of flu", "tax on a loan")) assertTrue(ChatText.needsCaution(text), text)
        assertFalse(ChatText.needsCaution("what is the capital of France"))
    }

    @Test
    fun theSystemPromptSaysChatCannotLookAnythingUpAndGivesCareWithAdvice() {
        assertTrue("cannot look up" in ChatPrompt.SYSTEM)
        assertTrue("health, medicine, legal or money" in ChatPrompt.SYSTEM)
    }

    @Test
    fun aCancelledAnswerReturnsNothingAndStillReleasesTheModel() = runBlocking<Unit> {
        val host = FakeHost(object : LlmEngine {
            override suspend fun load(spec: ModelSpec) = 0L

            override suspend fun complete(request: GenerationRequest): Completion = throw kotlinx.coroutines.CancellationException("stopped")

            override suspend fun unload() = Unit
        })
        assertFailsWith<kotlinx.coroutines.CancellationException> { ChatReplier(host).reply(emptyList(), "explain GST") }
        assertEquals(1, host.released)
    }

    @Test
    fun theHistoryIsTheTurnsBeforeTheMessageSoTheMessageIsNeverRepeated() = runBlocking<Unit> {
        val (chat, engine) = replier()
        chat.reply(listOf(Turn.User("what is GST", false, at), Turn.Chat("GST is a tax.", at)), "make it shorter")
        val sent = checkNotNull(engine.request).user
        assertEquals(1, Regex("make it shorter").findAll(sent).count())
    }

    @Test
    fun anAnswerThatArrivesAfterTheOwnerStoppedIsNeverReturned() = runBlocking<Unit> {
        val host = FakeHost(object : LlmEngine {
            override suspend fun load(spec: ModelSpec) = 0L

            override suspend fun complete(request: GenerationRequest): Completion {
                kotlin.coroutines.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
                return Completion("A long answer that nobody wants any more.", 500, 3000, 12)
            }

            override suspend fun unload() = Unit
        })
        val outcome = runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Job()) { ChatReplier(host).reply(emptyList(), "write a story") } }
        assertTrue(outcome.exceptionOrNull() is kotlinx.coroutines.CancellationException, outcome.toString())
        assertEquals(1, host.released)
    }
}
