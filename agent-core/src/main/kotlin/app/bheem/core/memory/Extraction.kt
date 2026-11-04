package app.bheem.core.memory

import app.bheem.core.assistant.Turn
import app.bheem.core.parse.ThinkingFilter
import app.bheem.models.GenerationRequest
import app.bheem.models.host.ModelHost

/**
 * Overnight, while the phone charges, Bheem may read what the owner said that day and suggest lasting facts. The model only reads the owner's own
 * words, has no tools, and its lines are parsed by code and kept as guesses: nothing a guess says is ever put in a prompt until the owner confirms it.
 */
object Extraction {
    const val ID = "memory-extract-v1"
    private const val MAX_FACTS = 5
    private const val MAX_BLOCK_CHARS = 1800
    private const val MIN_BLOCK_CHARS = 25

    val SYSTEM: String = (
        "You read what a phone's owner said to their assistant today. List up to 5 lasting facts the OWNER said about themselves: people, places, preferences, routines. " +
            "Only things the owner said. Never use anything the assistant said. One fact per line, written as: fact|<the fact in plain words, starting with The owner>. " +
            "If there is nothing lasting, write: none"
        )

    fun userBlock(turns: List<Turn>): String? {
        val lines = turns.filterIsInstance<Turn.User>().map { "Owner: " + it.text.replace(Regex("""\s+"""), " ").trim() }
        val block = lines.joinToString("\n").take(MAX_BLOCK_CHARS)
        return if (block.length < MIN_BLOCK_CHARS) null else block
    }

    fun parse(raw: String, existing: Set<String>): List<String> {
        val seen = existing.map { it.lowercase() }.toMutableSet()
        val out = ArrayList<String>()
        for (line in ThinkingFilter.strip(raw).lines()) {
            val t = line.trim()
            if (!t.startsWith("fact|", ignoreCase = true)) continue
            val fact = t.substring(5).trim()
            if (fact.length < 5 || fact.length > 200 || fact.any { it.isISOControl() } || "http" in fact.lowercase()) continue
            if (!seen.add(fact.lowercase())) continue
            out += fact
            if (out.size == MAX_FACTS) break
        }
        return out
    }
}

class MemoryExtractor(private val host: ModelHost) {
    suspend fun extract(turns: List<Turn>, existing: Set<String>): List<String> {
        val block = Extraction.userBlock(turns) ?: return emptyList()
        val engine = host.acquire()
        try {
            val completion = engine.complete(GenerationRequest(Extraction.SYSTEM, block, maxOutputTokens = MAX_OUTPUT_TOKENS, temperature = 0.0))
            return Extraction.parse(completion.text, existing)
        } finally {
            host.release()
        }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 160
    }
}
