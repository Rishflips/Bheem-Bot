package app.bheem.core.assistant

import app.bheem.models.GenerationRequest
import app.bheem.models.host.ModelHost

object ChatPrompt {
    const val ID = "chat-v1"

    val SYSTEM: String = """
        You are Bheem, a friendly assistant that runs on the owner's phone. Answer the owner in plain text, in under 60 words, with no markdown, lists or links.
        You cannot check live information such as weather, news, prices, scores or the time; say so if asked.
        You have no access to the owner's notes, tasks, reminders or contacts in this chat, and you cannot set reminders, send messages, call or open anything: never say that you did.
        If the owner wants one of those done, tell them to say it as a command, for example: remind me tomorrow at 9 to call Arun.
        If you are not sure, say you are not sure. Do not make up facts.
    """.trimIndent().replace("\n", " ")
}

/** Cleans what the model wrote before it is shown: plain text only, a sane length, and no claim to have done something chat cannot do. */
object ChatText {
    const val NOT_DONE = "I did not do that. To make something happen, say it as a command, for example: remind me tomorrow at 9 to call Arun."
    const val NO_ANSWER = "I do not have an answer for that."
    private const val MAX_CHARS = 700

    private val claims = listOf(
        Regex("""\bI(?:'ve|\s+have)\s+(?:just\s+)?(?:set|saved|added|created|scheduled|booked|sent|noted)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bI\s+(?:set|saved|added|created|scheduled|booked|sent|noted)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:reminder|note|task|event|message)\s+(?:has\s+been|was|is\s+now)\s+(?:set|saved|added|created|scheduled|sent)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bI(?:\s+will|'ll)\s+remind\s+you\b""", RegexOption.IGNORE_CASE),
    )
    private val link = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val bullet = Regex("""(?m)^\s*(?:[-*•]|\d+[.)])\s+""")

    fun claimsToHaveActed(text: String): Boolean = claims.any { it.containsMatchIn(text.replace('’', '\'')) }

    /** The text so far while the model is still writing: plain, and hidden if it already claims an action. */
    fun partial(raw: String): String = if (claimsToHaveActed(raw)) "" else plain(raw)

    fun clean(raw: String): String {
        if (claimsToHaveActed(raw)) return NOT_DONE
        val text = plain(raw)
        if (text.isEmpty()) return NO_ANSWER
        return if (text.length <= MAX_CHARS) text else cut(text)
    }

    private fun plain(raw: String): String =
        raw.replace(link, "$1").replace(bullet, "").filter { !it.isISOControl() || it == '\n' || it == '\t' }
            .replace("**", "").replace("__", "").replace("`", "").replace("#", "")
            .replace(Regex("""\s+"""), " ").trim()

    private fun cut(text: String): String {
        val head = text.take(MAX_CHARS)
        val end = maxOf(head.lastIndexOf(". "), head.lastIndexOf("! "), head.lastIndexOf("? "))
        if (end > 0) return head.substring(0, end + 1)
        return head.substringBeforeLast(' ').trimEnd(',', ';', ':') + "."
    }

    private const val TURN_CHARS = 200
    private const val TURNS = 4
    private const val MESSAGE_CHARS = 500

    /** The owner's message, with the last few exchanges before it so "make it shorter" has something to refer to. Briefs, plans and citations are left out. */
    fun message(history: List<Turn>, text: String): String {
        fun flat(s: String) = s.replace(Regex("""\s+"""), " ").trim().take(TURN_CHARS)
        val lines = history.mapNotNull { turn ->
            when (turn) {
                is Turn.User -> "You: ${flat(turn.text)}"
                is Turn.Answer -> "Bheem: ${flat(turn.text)}"
                is Turn.Chat -> "Bheem: ${flat(turn.text)}"
                is Turn.Notice -> "Bheem: ${flat(turn.text)}"
                is Turn.Brief, is Turn.Plan -> null
            }
        }.takeLast(TURNS)
        val now = text.trim().take(MESSAGE_CHARS)
        return if (lines.isEmpty()) now else "Earlier in this conversation:\n${lines.joinToString("\n")}\n\nThe owner now says: $now"
    }
}

/** Answers a general question with the language model. It has no tools and no records: it can only produce text, which is shown as the model's words. */
class ChatReplier(private val host: ModelHost) {
    suspend fun reply(history: List<Turn>, text: String, onPartial: (String) -> Unit = {}): String {
        val engine = host.acquire()
        try {
            val request = GenerationRequest(
                ChatPrompt.SYSTEM, ChatText.message(history, text), maxOutputTokens = MAX_OUTPUT_TOKENS, temperature = TEMPERATURE,
                onText = { onPartial(ChatText.partial(it)) },
            )
            return ChatText.clean(engine.complete(request).text)
        } finally {
            host.release()
        }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 120
        const val TEMPERATURE = 0.2
    }
}
