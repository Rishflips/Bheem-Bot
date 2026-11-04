package app.bheem.core.assistant

import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelState
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class OfferTest {
    private val at = LocalDateTime.of(2026, 11, 4, 19, 42)

    @Test
    fun anOfferLineIsSplitOffTheAnswer() {
        val (text, offer) = ChatText.splitOffer("Sure, that sounds important. Want me to set it up?\nOffer|remind me Friday at 4 about the dentist")
        assertEquals("Sure, that sounds important. Want me to set it up?", text)
        assertEquals("remind me Friday at 4 about the dentist", offer)
    }

    @Test
    fun anAnswerWithoutAnOfferLineIsLeftAlone() {
        val (text, offer) = ChatText.splitOffer("Volcanoes form where magma reaches the surface.")
        assertEquals("Volcanoes form where magma reaches the surface.", text)
        assertNull(offer)
    }

    @Test
    fun theOfferLineIsNeverShownWhileTheModelIsStillWriting() {
        assertEquals("Want me to set it up?", ChatText.partial("Want me to set it up?\nOffer|remind me Friday at 4"))
        assertEquals("Want me to set it up?", ChatText.partial("Want me to set it up?\nOff"))
        assertEquals("Want me to set it up? Ok", ChatText.partial("Want me to set it up?\nOk"))
    }

    @Test
    fun anOfferMayOnlyUseWordsTheOwnerSaid() {
        val said = "I have a dentist appointment on Friday at 4"
        assertTrue(OfferCheck.grounded("remind me Friday at 4 about the dentist appointment", said))
        assertFalse(OfferCheck.grounded("remind me Friday at 5 about the dentist appointment", said), "a time the owner did not say")
        assertFalse(OfferCheck.grounded("remind me to call Ravi about the invoice", said), "new content")
        assertFalse(OfferCheck.grounded("", said))
    }

    @Test
    fun onlyPlainRecordsMayComeFromAnOffer() {
        for (name in listOf("CreateReminder", "CreateTask", "SaveNote", "CreateCalendarEvent")) assertTrue(OfferCheck.allows(name), name)
        for (name in listOf("DraftMessage", "PlaceCall", "OpenRoute", "OpenApp", "SetTimer", "Flashlight", "RememberFact", "ForgetFact", "LogExpense")) assertFalse(OfferCheck.allows(name), name)
    }

    private class Engine(var reply: String) : LlmEngine {
        var request: GenerationRequest? = null

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            this.request = request
            return Completion(reply, 300, 2000, 12)
        }

        override suspend fun unload() = Unit
    }

    private class Host(val engine: LlmEngine) : ModelHost {
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Unloaded)

        override suspend fun acquire(): LlmEngine = engine

        override fun release() = Unit

        override fun trim() = Unit

        override suspend fun shutdown() = Unit

        override fun abortLoad(reason: String) = Unit
    }

    @Test
    fun theReplierReturnsTheAnswerAndTheOfferSeparatelyWithTheNewSampler() = runBlocking<Unit> {
        val engine = Engine("That sounds important.\nOffer|remind me Friday at 4 about the dentist")
        val chat = ChatReplier(Host(engine))
        val reply = chat.replyWithOffer(emptyList(), "I have a dentist appointment on Friday at 4")
        assertEquals("That sounds important.", reply.text)
        assertEquals("remind me Friday at 4 about the dentist", reply.offer)
        val sent = checkNotNull(engine.request)
        assertEquals(160, sent.maxOutputTokens)
        assertEquals(0.6, sent.temperature)
        assertEquals(40, sent.topK)
        assertEquals("That sounds important.", chat.reply(emptyList(), "I have a dentist appointment on Friday at 4"))
    }

    @Test
    fun tellMeMoreGetsALongerAnswerAndAClaimOfActionDropsTheOffer() = runBlocking<Unit> {
        val engine = Engine("Here is more.")
        val chat = ChatReplier(Host(engine))
        chat.reply(emptyList(), "explain GST in detail")
        assertEquals(320, checkNotNull(engine.request).maxOutputTokens)
        engine.reply = "I have set a reminder for you.\nOffer|remind me Friday at 4"
        val claimed = chat.replyWithOffer(emptyList(), "remind me Friday at 4")
        assertEquals(ChatText.NOT_DONE, claimed.text)
        assertNull(claimed.offer)
    }

    @Test
    fun theMemoryBlockAndTheNowLineGoInFrontOfTheMessage() {
        val message = ChatText.message(emptyList(), "where do I live", "Now: Wed 4 Nov 2026, 7:42 PM IST.", "Things the owner told you earlier:\n- I live in Chennai")
        assertTrue(message.startsWith("Now: Wed 4 Nov 2026, 7:42 PM IST.\n\nThings the owner told you earlier:\n- I live in Chennai"), message)
        assertTrue(message.endsWith("where do I live"), message)
    }

    @Test
    fun theNewPersonaPromptOffersCardsAndIsShortEnough() {
        assertTrue(ChatPrompt.ID == "chat-v2")
        assertTrue("Offer|" in ChatPrompt.SYSTEM)
        assertTrue("2 to 5 sentences" in ChatPrompt.SYSTEM)
        assertTrue(ChatPrompt.SYSTEM.length < 1500, ChatPrompt.SYSTEM.length.toString())
    }
}
