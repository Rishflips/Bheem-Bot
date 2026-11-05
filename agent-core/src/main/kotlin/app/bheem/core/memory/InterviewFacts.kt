package app.bheem.core.memory

import app.bheem.models.GenerationRequest
import app.bheem.models.host.ModelHost

/**
 * Turns the interview answers, and the owner's own notes, into fact lines for one review card. The model reads only the
 * owner's own words, has no tools, and every line it writes is parsed by code before it can become a card.
 */
object InterviewFacts {
    const val ID = "interview-facts-v1"
    const val MAX_FACTS = 12
    const val MAX_SUGGESTIONS = 5
    private const val MAX_BLOCK_CHARS = 2400
    private const val MIN_NOTES_CHARS = 25

    val ANSWERS_SYSTEM: String = (
        "You read a short interview between a phone's owner and their assistant. Write the lasting facts the OWNER said about themselves, " +
            "in the owner's first person, as if the owner is speaking: name, city, work or study, people, routine, preferences, goals, languages. " +
            "Only things the owner actually said. Never use anything the assistant said and never invent. " +
            "One fact per line as: fact|<the fact in plain words>. Up to 12 facts. If there is nothing lasting, write: none"
        )

    val NOTES_SYSTEM: String = (
        "You read notes a phone's owner wrote for themselves. Suggest up to 5 lasting facts about the owner that would be useful later: " +
            "people, places, preferences, routines, goals. Only things the notes say. Write each in the owner's first person. " +
            "One fact per line as: fact|<the fact in plain words>. If there is nothing lasting, write: none"
        )

    fun answersBlock(pairs: List<Pair<String, String>>): String? {
        if (pairs.isEmpty()) return null
        return pairs.joinToString("\n") { (question, answer) ->
            "Question: ${question.replace(Regex("""\s+"""), " ").trim()}\nAnswer: ${answer.replace(Regex("""\s+"""), " ").trim()}"
        }.take(MAX_BLOCK_CHARS)
    }

    fun notesBlock(notes: List<String>): String? {
        val block = notes.map { "Note: " + it.replace(Regex("""\s+"""), " ").trim() }.joinToString("\n").take(MAX_BLOCK_CHARS)
        return if (block.length < MIN_NOTES_CHARS) null else block
    }

    fun parse(raw: String, existing: Set<String>): List<String> = Extraction.parse(raw, existing, MAX_FACTS)

    fun parseSuggestions(raw: String, existing: Set<String>): List<String> = Extraction.parse(raw, existing, MAX_SUGGESTIONS)
}

class InterviewFactExtractor(private val host: ModelHost) {
    suspend fun facts(pairs: List<Pair<String, String>>, existing: Set<String>): List<String> {
        val block = InterviewFacts.answersBlock(pairs) ?: return emptyList()
        val raw = ask(InterviewFacts.ANSWERS_SYSTEM, block, FACT_TOKENS)
        return InterviewFacts.parse(raw, existing)
    }

    suspend fun suggestions(notes: List<String>, existing: Set<String>): List<String> {
        val block = InterviewFacts.notesBlock(notes) ?: return emptyList()
        val raw = ask(InterviewFacts.NOTES_SYSTEM, block, SUGGESTION_TOKENS)
        return InterviewFacts.parseSuggestions(raw, existing)
    }

    private suspend fun ask(system: String, block: String, tokens: Int): String {
        val engine = host.acquire()
        try {
            return engine.complete(GenerationRequest(system, block, maxOutputTokens = tokens, temperature = 0.0)).text
        } finally {
            host.release()
        }
    }

    private companion object {
        const val FACT_TOKENS = 400
        const val SUGGESTION_TOKENS = 160
    }
}
