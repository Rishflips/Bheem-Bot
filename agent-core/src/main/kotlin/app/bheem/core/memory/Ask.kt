package app.bheem.core.memory

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class SearchHit(val record: MemoryRecord, val score: Int)

data class Answer(val text: String, val citations: List<String>)

object MemorySearch {
    private val filler = setOf(
        "the", "a", "an", "is", "are", "was", "were", "did", "do", "does", "i", "my", "me", "where", "when", "what", "which", "how", "many",
        "much", "to", "of", "in", "on", "at", "for", "and", "or", "it", "that", "this", "have", "has", "any", "about", "show", "tell",
    )

    fun queryWords(text: String): List<String> = words(text)

    internal fun words(text: String): List<String> =
        text.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() && it !in filler }.map { it.removeSuffix("s") }

    fun search(query: String, records: List<MemoryRecord>, limit: Int): List<SearchHit> {
        val wanted = words(query).toSet()
        if (wanted.isEmpty()) return emptyList()
        return records
            .map { r -> SearchHit(r, words(r.text).toSet().count { it in wanted }) }
            .filter { it.score > 0 }
            .sortedWith(compareByDescending<SearchHit> { it.score }.thenByDescending { it.record.createdAt })
            .take(limit)
    }
}

object Ask {
    private const val CANDIDATES = 50
    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun answer(query: String, store: MemoryStore, now: LocalDateTime): Answer = answer(query, store.search(query, CANDIDATES), now)

    fun answer(query: String, records: List<MemoryRecord>, now: LocalDateTime): Answer {
        val hits = MemorySearch.search(query, records, limit = 3)
        if (hits.isEmpty()) return Answer("I found nothing about that in your notes and tasks.", emptyList())
        val lines = hits.joinToString("\n") { "- ${it.record.text} (${it.record.kind.name.lowercase()}, ${it.record.createdAt.format(day)})" }
        return Answer("Here is what I found:\n$lines", hits.map { it.record.id })
    }
}
