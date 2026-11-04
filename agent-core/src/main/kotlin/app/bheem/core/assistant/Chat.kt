package app.bheem.core.assistant

import app.bheem.models.GenerationRequest
import app.bheem.models.host.ModelHost
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object ChatPrompt {
    const val ID = "chat-v1"

    val SYSTEM: String = """
        You are Bheem, a friendly assistant that runs on the owner's phone. Answer the owner in plain text, in under 60 words, with no markdown, lists or links.
        You cannot check live information such as weather, news, prices or scores; say so if asked. A line starting with Now: gives the owner's current date, time and phone state; use it when they ask about those.
        You cannot look up the owner's notes, tasks, reminders or contacts in this chat, and you cannot set reminders, send messages, call or open anything: never say that you did.
        If the owner wants one of those done, tell them to say it as a command, for example: remind me tomorrow at 9 to call Arun.
        For health, medicine, legal or money questions give general facts only and say to check with a professional.
        If you are not sure, say you are not sure. Do not make up facts.
    """.trimIndent().replace("\n", " ")
}

/** Cleans what the model wrote before it is shown: plain text only, a sane length, and no claim to have done something chat cannot do. */
object ChatText {
    const val NOT_DONE = "I did not do that. To make something happen, say it as a command, for example: remind me tomorrow at 9 to call Arun."
    const val NO_ANSWER = "I do not have an answer for that."
    const val CAUTION = "This is general information, not advice. Check with a professional before you rely on it."
    private const val MAX_CHARS = 700

    /** An answer this long that stops mid sentence was most likely cut by the token limit; a short one is left as the model wrote it. */
    private const val LONG_ANSWER = 300

    private val verbs = "set|saved|added|created|scheduled|booked|sent|noted"
    private val doing = "set|setting|add|adding|send|sending|call|calling|open|opening|save|saving|create|creating|schedule|scheduling|book|booking"
    private val claims = listOf(
        Regex("""\bI(?:'ve|\s+have)\s+(?:just\s+)?(?:$verbs)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bI\s+(?:$verbs)\b""", RegexOption.IGNORE_CASE),
        Regex("""\byour\s+(?:reminder|note|task|event|message)\s+(?:has\s+been|was|is\s+now)\s+(?:$verbs)\b""", RegexOption.IGNORE_CASE),
        Regex("""\bI(?:\s+will|'ll)\s+remind\s+you\b""", RegexOption.IGNORE_CASE),
        Regex("""\bI(?:'ll|\s+will|'m|\s+am)\s+(?:now\s+)?(?:$doing)\b""", RegexOption.IGNORE_CASE),
        Regex("""\b(?:reminder|note|task|event)\s+(?:set|saved|added|created)\b""", RegexOption.IGNORE_CASE),
        Regex("""\badded\s+to\s+your\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:^|[.!?]\s+)(?:calling|opening|setting|sending)\b""", RegexOption.IGNORE_CASE),
    )
    private val link = Regex("""\[([^\]]*)]\([^)]*\)""")
    private val bullet = Regex("""(?m)^\s*(?:[-*•]|\d+[.)])\s+""")
    private val heading = Regex("""(?m)^\s*#+\s*""")
    private val advice = Regex(
        """\b(dose|dosage|mg|symptoms?|medicine|medication|tablets?|diagnos\w*|loan|invest\w*|stocks?|tax|taxes|legal|court|lawyer|insurance|mortgage)\b""",
        RegexOption.IGNORE_CASE,
    )

    fun claimsToHaveActed(text: String): Boolean = claims.any { it.containsMatchIn(text.replace('’', '\'')) }

    /** True when the question is about health, money or law: the answer then carries a fixed caution line. */
    fun needsCaution(userText: String): Boolean = advice.containsMatchIn(userText)

    /** The text so far while the model is still writing: plain, and hidden if it already claims an action. */
    fun partial(raw: String): String = if (claimsToHaveActed(raw)) "" else plain(raw)

    fun clean(raw: String): String {
        if (claimsToHaveActed(raw)) return NOT_DONE
        val text = plain(raw)
        if (text.isEmpty()) return NO_ANSWER
        val capped = if (text.length <= MAX_CHARS) text else cut(text)
        return if (capped.last() in ".!?" || capped.length < LONG_ANSWER) capped else lastSentence(capped)
    }

    private fun plain(raw: String): String =
        raw.replace(link, "$1").replace(bullet, "").replace(heading, "")
            .filter { (!it.isISOControl() || it == '\n' || it == '\t') && it.category != CharCategory.FORMAT }
            .replace("**", "").replace("__", "").replace("`", "")
            .replace(Regex("""\s+"""), " ").trim()

    private fun cut(text: String): String {
        val head = text.take(MAX_CHARS)
        val end = sentenceEnd(head)
        if (end > 0) return head.substring(0, end + 1)
        return head.substringBeforeLast(' ').trimEnd(',', ';', ':') + "."
    }

    /** An answer stopped by the length limit ends mid sentence: keep the sentences that are whole, if there are any. */
    private fun lastSentence(text: String): String {
        val end = sentenceEnd(text)
        return if (end > 0) text.substring(0, end + 1) else text
    }

    private fun sentenceEnd(text: String): Int = maxOf(text.lastIndexOf(". "), text.lastIndexOf("! "), text.lastIndexOf("? "))

    private const val TURN_CHARS = 200
    private const val TURNS = 4
    private const val MESSAGE_CHARS = 500

    /** The owner's message, with the last few exchanges before it so "make it shorter" has something to refer to. [history] must not contain the message itself. Briefs, plans and citations are left out. */
    fun message(history: List<Turn>, text: String, nowLine: String? = null): String {
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
        val body = if (lines.isEmpty()) now else "Earlier in this conversation:\n${lines.joinToString("\n")}\n\nThe owner now says: $now"
        return if (nowLine == null) body else "$nowLine\n\n$body"
    }
}

/** Answers a general question with the language model. It has no tools and cannot look anything up: it can only produce text, which is shown as the model's words. */
class ChatReplier(private val host: ModelHost) {
    suspend fun reply(history: List<Turn>, text: String, nowLine: String? = null, onPartial: (String) -> Unit = {}): String {
        val engine = host.acquire()
        try {
            val request = GenerationRequest(
                ChatPrompt.SYSTEM, ChatText.message(history, text, nowLine), maxOutputTokens = MAX_OUTPUT_TOKENS, temperature = TEMPERATURE,
                onText = { onPartial(ChatText.partial(it)) },
            )
            val completion = engine.complete(request)
            currentCoroutineContext().ensureActive()
            val answer = ChatText.clean(completion.text)
            return if (answer != ChatText.NOT_DONE && ChatText.needsCaution(text)) "$answer ${ChatText.CAUTION}" else answer
        } finally {
            host.release()
        }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 120
        const val TEMPERATURE = 0.2
    }
}
