package app.bheem.core.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SmallTalkTest {
    @Test
    fun aGreetingGetsAGreetingAndAnOffer() {
        for (text in listOf("hi", "Hi!", "hello", "hey", "hey bheem", "hello there", "namaste", "hii")) {
            val reply = SmallTalk.reply(text)
            assertTrue(reply != null && reply.startsWith("Hi") && reply.contains("How can I help"), "$text -> $reply")
        }
    }

    @Test
    fun aTimeOfDayGreetingIsReturned() {
        assertTrue(SmallTalk.reply("good morning")!!.startsWith("Good morning"))
        assertTrue(SmallTalk.reply("Good evening Bheem")!!.startsWith("Good evening"))
        assertEquals("Good night. Sleep well.", SmallTalk.reply("good night"))
    }

    @Test
    fun thanksAndGoodbyeAreAnsweredBriefly() {
        for (text in listOf("thanks", "thank you", "thank you bheem", "thanks a lot", "thx")) assertEquals("You are welcome.", SmallTalk.reply(text), text)
        for (text in listOf("bye", "goodbye", "see you", "bye bheem")) assertEquals("Bye. I am here when you need me.", SmallTalk.reply(text), text)
    }

    @Test
    fun howAreYouGetsAnAnswerThatTurnsBackToTheWork() {
        for (text in listOf("how are you", "how are you doing", "how is it going", "what's up", "whats up", "sup")) {
            val reply = SmallTalk.reply(text)
            assertTrue(reply != null && reply.startsWith("I am doing well") && reply.contains("What can I do for you"), "$text -> $reply")
        }
    }

    @Test
    fun whatCanYouDoListsWhatBheemCanDo() {
        for (text in listOf("what can you do", "help", "what do you do")) {
            val reply = SmallTalk.reply(text)
            assertTrue(reply != null && reply.contains("reminders") && reply.contains("Try:"), "$text -> $reply")
        }
    }

    @Test
    fun whoAreYouIsLeftToThePersonaChatNotAFixedParagraph() {
        for (text in listOf("who are you", "what are you", "what is your name", "whats your name", "who are you bheem")) {
            assertEquals(null, SmallTalk.reply(text), text)
        }
    }

    @Test
    fun anythingThatIsMoreThanSmallTalkIsLeftAlone() {
        for (text in listOf(
            "hi remind me tomorrow at 9 to call Arun", "thanks, remind me to pay rent at 5", "hello what is on tomorrow", "help me plan my day",
            "how are you doing today with my tasks", "remind me to say hi to Arun", "", "   ",
        )) {
            assertNull(SmallTalk.reply(text), text)
        }
    }
}
