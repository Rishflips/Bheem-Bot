package app.bheem.core.assistant

/**
 * The first-chat interview: code-written questions, one at a time, with the owner's answer to the current question.
 * A command that the pipeline would turn into a card is not an answer, so the question stays where it was.
 */
class Interview {
    enum class Result { ANSWERED, SKIPPED, STOPPED, COMMAND }

    private val answers = mutableListOf<Pair<String, String>>()
    private var index = 0
    private var active = false
    private var nameRetry = false

    fun start() {
        answers.clear()
        index = 0
        nameRetry = false
        active = true
    }

    /** Continues an interview from the answers already given, so a restart does not ask question one again. */
    fun resume(pairs: List<Pair<String, String>>, nextIndex: Int) {
        answers.clear()
        answers += pairs.filter { (question, _) -> question in QUESTIONS }
        index = nextIndex.coerceIn(0, QUESTIONS.size)
        nameRetry = false
        active = index < QUESTIONS.size
    }

    fun answer(text: String, command: Boolean = isCommand(text)): Result {
        val clean = text.trim()
        val word = clean.trimEnd('.', '!', ' ').lowercase()
        if (word in STOP_WORDS) {
            stop()
            return Result.STOPPED
        }
        if (word in SKIP_WORDS) {
            skip()
            return Result.SKIPPED
        }
        if (command) return Result.COMMAND
        val question = currentQuestion() ?: return Result.STOPPED
        if (index == 0) {
            val name = nameFrom(clean)
            if (name == null) {
                if (nameRetry) {
                    skip()
                    return Result.SKIPPED
                }
                nameRetry = true
                return Result.ANSWERED
            }
            nameRetry = false
            answers += QUESTIONS[0] to name
        } else {
            answers += question to clean
        }
        index++
        if (index >= QUESTIONS.size) active = false
        return Result.ANSWERED
    }

    fun skip() {
        if (!active) return
        nameRetry = false
        index++
        if (index >= QUESTIONS.size) active = false
    }

    fun stop() {
        active = false
    }

    fun isDone(): Boolean = !active

    fun isActive(): Boolean = active

    fun currentQuestion(): String? = if (active) {
        if (index == 0 && nameRetry) NAME_RETRY else QUESTIONS.getOrNull(index)
    } else null

    fun pairs(): List<Pair<String, String>> = answers.toList()

    fun nextIndex(): Int = index

    companion object {
        val QUESTIONS = listOf(
            "Hi, I am Bheem. I would like to get to know you so I can actually be useful. What should I call you?",
            "Nice to meet you. Which city do you live in?",
            "What do you do, work or study?",
            "Who are the important people in your life? Family, friends, anyone I should know by name.",
            "What does a normal day look like for you? Wake up time, work hours, anything regular.",
            "Any preferences I should remember? Food, favourite places, things you dislike.",
            "What are you working towards right now?",
            "Which languages do you like to use?",
        )

        private val STOP_WORDS = setOf("stop", "later", "cancel")
        private val SKIP_WORDS = setOf("skip", "next")
        private val ignore = setOf(RegexOption.IGNORE_CASE)
        private val commands = listOf(
            Regex("""^(?:please\s+)?remind me\b""", ignore),
            Regex("""^(?:please\s+)?note\b""", ignore),
            Regex("""^(?:please\s+)?task\b""", ignore),
            Regex("""^(?:please\s+)?add (?:a |an |the )?(?:task|note|reminder|event)\b""", ignore),
            Regex("""^(?:please\s+)?save (?:this|that|it|my|the)\b""", ignore),
            Regex("""^(?:please\s+)?set (?:a |an |the )?(?:reminder|timer|alarm)\b""", ignore),
            Regex("""^(?:please\s+)?create (?:a |an |the )?(?:reminder|task|event|note)\b""", ignore),
            Regex("""^(?:please\s+)?(?:schedule|book) (?!reading\b|club\b|fair\b|review\b|store\b|shop\b|title\b)\S""", ignore),
            Regex("""^(?:please\s+)?(?:send|message|text|whatsapp)\b""", ignore),
            Regex("""^(?:please\s+)?(?:call|dial|phone|ring) (?!me\b|centre\b|center\b|work\b|back\b)\S""", ignore),
            Regex("""^(?:please\s+)?open (?!to\b|university\b)\S""", ignore),
            Regex("""^(?:please\s+)?(?:route|navigate|take me|drive me|guide me)\b""", ignore),
            Regex("""^(?:please\s+)?turn (?:on|off)\b""", ignore),
            Regex("""^(?:please\s+)?(?:mute|volume)\b""", ignore),
            Regex("""^(?:please\s+)?forget\b""", ignore),
            Regex("""^(?:please\s+)?remember (?:that|to)\b""", ignore),
        )

        fun isCommand(text: String): Boolean = commands.any { it.containsMatchIn(text.trim()) }

        fun fallbackFacts(pairs: List<Pair<String, String>>): List<String> {
            val seen = mutableSetOf<String>()
            return pairs.mapNotNull { (question, raw) ->
                val answer = raw.replace(Regex("""\s+"""), " ").trim().trimEnd('.', '!', ' ')
                if (answer.isEmpty()) return@mapNotNull null
                val fact = when (question) {
                    QUESTIONS[0] -> "My name is ${nameFrom(answer) ?: return@mapNotNull null}"
                    QUESTIONS[1] -> "I live in $answer"
                    else -> "${topic(question)}: $answer"
                }
                if (!seen.add(fact.lowercase())) null else fact.take(MAX_FACT_CHARS)
            }.take(MAX_FALLBACK_FACTS)
        }

        private fun topic(question: String): String = when (question) {
            QUESTIONS[2] -> "Work"
            QUESTIONS[3] -> "People"
            QUESTIONS[4] -> "Routine"
            QUESTIONS[5] -> "Preferences"
            QUESTIONS[6] -> "Goals"
            QUESTIONS[7] -> "Languages"
            else -> "About you"
        }

        /** Keeps a model fact only when the question and answer it came from ground it; otherwise code builds that answer's fact. */
        fun groundedFacts(pairs: List<Pair<String, String>>, proposed: List<String>): List<String> {
            val kept = mutableListOf<String>()
            for ((question, answer) in pairs) {
                val ground = "$question $answer"
                val matching = proposed.filter { fact ->
                    (question != QUESTIONS[0] || nameIn(listOf(fact)) != null) && OfferCheck.factGrounded(fact, ground)
                }
                if (matching.isEmpty()) {
                    fallbackFacts(listOf(question to answer)).firstOrNull()?.let { kept += it }
                } else {
                    kept += matching
                }
            }
            return kept.distinctBy { it.lowercase() }
        }

        /** A name is 1 to 3 words of letters, with apostrophes and hyphens allowed, and no word from the stop list. */
        fun nameFrom(text: String): String? {
            val clean = text.replace(Regex("""\s+"""), " ").trim().trimEnd('.', '!', '?', ' ')
            if (clean.isEmpty()) return null
            val phrase = namePhrases.firstNotNullOfOrNull { it.find(clean)?.groupValues?.get(1) }
            val candidate = if (phrase != null) {
                phrase.replace(Regex("""\s+"""), " ").trim().trimEnd('.', '!', '?', ' ').trim()
            } else {
                if (namePrefix.containsMatchIn(clean)) return null
                clean
            }
            val words = candidate.split(' ').filter { it.isNotEmpty() }
            if (words.size !in 1..3) return null
            if (words.any { it.lowercase() in NAME_STOP_WORDS }) return null
            if (!NAME_WORD.matches(candidate)) return null
            return candidate
        }

        fun nameIn(facts: List<String>): String? = facts.firstNotNullOfOrNull { fact ->
            val raw = Regex("""^my name is (.+)$""", RegexOption.IGNORE_CASE).find(fact)?.groupValues?.get(1) ?: return@firstNotNullOfOrNull null
            nameFrom(raw)?.take(60)
        }

        fun validNameFact(fact: String): Boolean = nameIn(listOf(fact)) != null

        private val namePhrases = listOf(
            Regex("""^(?:please\s+)?call me\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^i am\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^i'?m\s+(.+)$""", RegexOption.IGNORE_CASE),
            Regex("""^(?:my\s+)?name\s+is\s+(.+)$""", RegexOption.IGNORE_CASE),
        )

        private val namePrefix = Regex("""^(?:please\s+)?(?:call me|i am|i'?m|(?:my\s+)?name is)(?:\s|$)""", RegexOption.IGNORE_CASE)

        private val NAME_WORD = Regex("""[\p{L}]+(?:['-][\p{L}]+)*(?: +[\p{L}]+(?:['-][\p{L}]+)*){0,2}""")

        private val NAME_STOP_WORDS = setOf(
            "ready", "yes", "yeah", "yep", "ok", "okay", "sure", "hi", "hello", "hey", "fine", "good",
            "start", "go", "next", "skip", "no", "nope", "nothing", "idk", "later", "cancel", "stop", "bheem",
        )

        const val NAME_RETRY = "Sorry, just your name, what should I call you?"

        private const val MAX_FACT_CHARS = 120
        private const val MAX_FALLBACK_FACTS = 12
    }
}

/** Stores the interview's answers and next question next to the other preferences, so an app restart can pick it up. */
object InterviewStateCodec {
    private const val FIELD = "\t"
    private const val NEXT = "next="

    data class Resumed(val pairs: List<Pair<String, String>>, val nextIndex: Int)

    fun encode(pairs: List<Pair<String, String>>, nextIndex: Int): String = buildString {
        append(NEXT).append(nextIndex).append('\n')
        for ((question, answer) in pairs) {
            val index = Interview.QUESTIONS.indexOf(question)
            if (index < 0) continue
            append(index).append(FIELD).append(answer.replace(Regex("""[\t\r\n]+"""), " ")).append('\n')
        }
    }

    fun decode(raw: String): Resumed {
        var next: Int? = null
        val pairs = mutableListOf<Pair<String, String>>()
        for (line in raw.lineSequence()) {
            if (line.startsWith(NEXT)) {
                next = line.removePrefix(NEXT).trim().toIntOrNull()
                continue
            }
            val parts = line.split(FIELD, limit = 2)
            if (parts.size != 2) continue
            val index = parts[0].toIntOrNull() ?: continue
            val question = Interview.QUESTIONS.getOrNull(index) ?: continue
            val answer = parts[1].trim().takeIf { it.isNotEmpty() } ?: continue
            pairs += question to answer
        }
        return Resumed(pairs, (next ?: pairs.size).coerceIn(0, Interview.QUESTIONS.size))
    }
}
