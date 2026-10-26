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
    private const val LISTED = 5
    private const val NL = "\n"
    private val day = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    private val kindWords = mapOf("task" to RecordKind.TASK, "note" to RecordKind.NOTE, "expense" to RecordKind.EXPENSE)
    private val chatter = setOf(
        "add", "added", "save", "saved", "pending", "still", "week", "month", "last", "today", "list", "everything", "all", "new", "recent", "are", "there",
        "got", "make", "made", "kept", "keep", "put", "noted", "wrote", "write", "so", "far", "now", "yet", "left", "open", "tell", "give",
    )

    fun answer(query: String, store: MemoryStore, now: LocalDateTime): Answer {
        val words = MemorySearch.words(query)
        val kind = words.firstNotNullOfOrNull { kindWords[it] }
        if (kind == null) return answer(query, store.search(query, CANDIDATES), now)
        val rest = words.filter { it !in kindWords && it !in chatter }
        val ofKind = store.ofKind(kind)
        if (rest.isEmpty()) return listing(kind, ofKind)
        return answer(rest.joinToString(" "), ofKind, now)
    }

    private fun listing(kind: RecordKind, records: List<MemoryRecord>): Answer {
        val label = kind.name.lowercase()
        if (records.isEmpty()) return Answer("You have no ${label}s.", emptyList())
        val newest = records.sortedByDescending { it.createdAt }.take(LISTED)
        val noun = if (records.size == 1) label else "${label}s"
        val more = if (records.size > LISTED) NL + "...and ${records.size - LISTED} more" else ""
        val lines = newest.joinToString(NL) { "- ${it.text}" }
        return Answer("You have ${records.size} $noun:" + NL + lines + more, newest.map { it.id })
    }

    fun answer(query: String, records: List<MemoryRecord>, now: LocalDateTime): Answer {
        val hits = MemorySearch.search(query, records, limit = 3)
        if (hits.isEmpty()) return Answer("I found nothing about that in your notes and tasks.", emptyList())
        val lines = hits.joinToString("\n") { "- ${it.record.text} (${it.record.kind.name.lowercase()}, ${it.record.createdAt.format(day)})" }
        return Answer("Here is what I found:\n$lines", hits.map { it.record.id })
    }
}
