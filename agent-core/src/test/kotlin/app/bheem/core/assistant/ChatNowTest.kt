package app.bheem.core.assistant

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatNowTest {
    private val at = LocalDateTime.of(2026, 11, 4, 19, 42)

    @Test
    fun theChatPromptNoLongerSaysItCannotKnowTheTime() {
        assertFalse(ChatPrompt.SYSTEM.contains("or the time"), ChatPrompt.SYSTEM)
        assertTrue(ChatPrompt.SYSTEM.contains("Now:"), "the prompt should say where the date and time are")
        assertTrue(ChatPrompt.SYSTEM.contains("live news"), "the prompt should say it cannot see live information")
        assertTrue(ChatPrompt.SYSTEM.contains("never invent more"), "the memory lines are the owner's own words")
    }

    @Test
    fun theNowLineComesFirstInTheMessage() {
        val message = ChatText.message(listOf(Turn.User("hi", false, at), Turn.Chat("hello", at)), "what should I eat", "Now: Wed 4 Nov 2026, 7:42 PM IST.")
        assertTrue(message.startsWith("Now: Wed 4 Nov 2026, 7:42 PM IST."), message)
        assertTrue(message.contains("Earlier in this conversation:"))
        assertTrue(message.endsWith("The owner now says: what should I eat"), message)
    }

    @Test
    fun withoutANowLineTheMessageIsAsBefore() {
        assertTrue(ChatText.message(emptyList(), "hello there") == "hello there")
    }
}
