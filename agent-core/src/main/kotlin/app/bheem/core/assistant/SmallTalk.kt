package app.bheem.core.assistant

/**
 * Plain replies to greetings, thanks, goodbyes, "how are you" and "what can you do". "Who are you" is not here: it goes to the persona chat. Written by code, so they are instant and never invented by
 * a model. Only a message that is nothing but small talk matches; a greeting with a request in it is left to the normal path.
 */
object SmallTalk {
    private const val OFFER = "How can I help?"
    private const val CAPABILITIES =
        "I can set reminders, save notes and tasks, add calendar events, draft messages, call someone whose number you saved, open a route, and tell you what is on your day. Try: remind me tomorrow at 9 to call Arun."

    private val name = setOf("bheem", "there", "bro", "buddy", "friend")
    private val greetings = setOf("hi", "hii", "hiii", "hello", "hey", "hiya", "yo", "namaste", "hola", "heya")
    private val timeGreetings = mapOf("good morning" to "Good morning", "good afternoon" to "Good afternoon", "good evening" to "Good evening")
    private val thanks = setOf("thanks", "thank you", "thanks a lot", "thank you so much", "thx", "ok thanks", "okay thanks", "many thanks")
    private val goodbyes = setOf("bye", "goodbye", "see you", "see ya", "bye bye", "cya", "see you later")
    private val howAreYou = setOf("how are you", "how are you doing", "how is it going", "how are things", "what is up", "whats up", "sup", "how do you do")
    private val about = setOf("what can you do", "what do you do", "help", "what can i ask", "what can i ask you")

    fun reply(text: String): String? {
        val words = text.lowercase().replace("'", "").replace("’", "").split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > MAX_WORDS) return null
        val phrase = words.dropLastWhile { it in name }.dropWhile { it in name && words.size > 1 }.joinToString(" ")
        return when {
            phrase in greetings -> "Hi! $OFFER $CAPABILITIES"
            phrase in timeGreetings -> "${timeGreetings.getValue(phrase)}! $OFFER"
            phrase == "good night" -> "Good night. Sleep well."
            phrase in thanks -> "You are welcome."
            phrase in goodbyes -> "Bye. I am here when you need me."
            phrase in howAreYou -> "I am doing well, thank you. What can I do for you?"
            phrase in about -> CAPABILITIES
            else -> null
        }
    }

    private const val MAX_WORDS = 6
}
