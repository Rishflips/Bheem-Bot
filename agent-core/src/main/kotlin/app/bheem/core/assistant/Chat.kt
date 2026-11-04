package app.bheem.core.assistant

import app.bheem.models.GenerationRequest
import app.bheem.models.host.ModelHost
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

object ChatPrompt {
    const val ID = "chat-v2"

    val SYSTEM: String = """
        You are Bheem, the owner's own assistant, living on their phone. Talk like a warm, direct friend: plain English, use their name if you know it, no markdown, lists or links.
        Answer in 2 to 5 sentences unless the owner asks for more detail.
        A line starting with Now: gives the owner's current date, time and phone state. Lines under "Things the owner told you earlier" are the owner's own words about themselves: use them naturally and never invent more.
        You cannot check live information such as weather, news, prices or scores; say so if asked.
        You cannot look up anything else in the owner's notes, tasks, reminders or contacts in this chat, and you cannot set reminders, send messages, call or open anything: never say that you did.
        If the owner wants a reminder, note, task or event, offer it warmly (for example: want me to set that up?) and end your reply with one extra line: Offer|<the command in the owner's own words, for example: remind me tomorrow at 9 to call Arun>.
        For health, medicine, legal or money questions give general facts only and say to check with a professional.
        If you are not sure, say you are not sure. Do not make up facts.
    """.trimIndent().replace("\n", " ")
}

/** Cleans what the model wrote before it is shown: plain text only, a sane length, and no claim to have done something chat cannot do. */
object ChatText {
    const val NOT_DONE = "I did not do that. To make something happen, say it as a command, for example: remind me tomorrow at 9 to call Arun."
    const val NO_ANSWER = "I do not have an answer for that."
    const val CAUTION = "This is general information, not advice. Check with a professional before you rely on it."
    private const val MAX_CHARS = 1400

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
    fun partial(raw: String): String {
        val (shown, _) = splitOffer(hideUnfinishedOffer(raw))
        return if (claimsToHaveActed(shown)) "" else plain(shown)
    }

    private const val OFFER = "Offer|"

    /** A reply may end with one line "Offer|<command>": a command the owner could give. It is split off here and never shown as text. */
    fun splitOffer(raw: String): Pair<String, String?> {
        val lines = raw.lines()
        val at = lines.indexOfLast { it.trim().startsWith(OFFER, ignoreCase = true) }
        if (at < 0) return raw to null
        val offer = lines[at].trim().substring(OFFER.length).trim().take(300).ifEmpty { null }
        return lines.filterIndexed { i, _ -> i != at }.joinToString("\n").trimEnd() to offer
    }

    private fun hideUnfinishedOffer(raw: String): String {
        val last = raw.substringAfterLast('\n')
        return if (last.isNotEmpty() && last.length < OFFER.length && OFFER.startsWith(last, ignoreCase = true)) raw.dropLast(last.length) else raw
    }

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

    private const val TURN_CHARS = 160
    private const val TURNS = 6
    private const val MESSAGE_CHARS = 500

    /** The owner's message, with the last few exchanges before it so "make it shorter" has something to refer to. [history] must not contain the message itself. Briefs, plans and citations are left out. */
    fun message(history: List<Turn>, text: String, nowLine: String? = null, memoryBlock: String? = null): String {
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
        return listOfNotNull(nowLine, memoryBlock, body).joinToString("\n\n")
    }
}

/** Answers a general question with the language model. It has no tools and cannot look anything up: it can only produce text, which is shown as the model's words. */
data class ChatReply(val text: String, val offer: String?)

class ChatReplier(private val host: ModelHost) {
    suspend fun reply(history: List<Turn>, text: String, nowLine: String? = null, memoryBlock: String? = null, onPartial: (String) -> Unit = {}): String =
        replyWithOffer(history, text, nowLine, memoryBlock, onPartial).text

    suspend fun replyWithOffer(history: List<Turn>, text: String, nowLine: String? = null, memoryBlock: String? = null, onPartial: (String) -> Unit = {}): ChatReply {
        val engine = host.acquire()
        try {
            val request = GenerationRequest(
                ChatPrompt.SYSTEM, ChatText.message(history, text, nowLine, memoryBlock), maxOutputTokens = if (wantsMore(text)) LONG_OUTPUT_TOKENS else MAX_OUTPUT_TOKENS,
                temperature = TEMPERATURE, topK = TOP_K, onText = { onPartial(ChatText.partial(it)) },
            )
            val completion = engine.complete(request)
            currentCoroutineContext().ensureActive()
            val (shown, offer) = ChatText.splitOffer(completion.text)
            val answer = ChatText.clean(shown)
            if (answer == ChatText.NOT_DONE) return ChatReply(answer, null)
            return ChatReply(if (ChatText.needsCaution(text)) "$answer ${ChatText.CAUTION}" else answer, offer)
        } finally {
            host.release()
        }
    }

    private fun wantsMore(text: String) = MORE.containsMatchIn(text)

    private companion object {
        const val MAX_OUTPUT_TOKENS = 160
        const val LONG_OUTPUT_TOKENS = 320
        const val TEMPERATURE = 0.6
        const val TOP_K = 40
        val MORE = Regex("""\b(tell me more|explain|in detail|elaborate|more detail|go on|longer)\b""", RegexOption.IGNORE_CASE)
    }
}

/** An offer is a command the model suggested from what the owner said. It may only reuse the owner's own words, and only for plain records. */
object OfferCheck {
    private val allowed = setOf("CreateReminder", "CreateTask", "SaveNote", "CreateCalendarEvent")
    private val commandWords = setOf("remind", "reminder", "note", "task", "add", "save", "set", "create", "schedule", "book", "put", "event", "calendar")
    private val functionWords = setOf(
        "a", "an", "the", "me", "my", "to", "of", "in", "on", "at", "for", "by", "with", "and", "or", "that", "this", "about", "about", "please", "is", "am", "are", "was", "it", "i",
    )

    fun allows(action: String): Boolean = action in allowed

    fun grounded(offer: String, ownerMessage: String): Boolean {
        val said = tokens(ownerMessage).toSet()
        val content = tokens(offer).filter { it !in functionWords && it !in commandWords }
        if (content.isEmpty()) return false
        if (content.any { t -> t.any { it.isDigit() } && t !in said }) return false
        val missing = content.count { it !in said }
        return missing <= content.size / 4
    }

    private fun tokens(s: String) = s.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }
}
