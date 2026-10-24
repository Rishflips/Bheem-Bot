package app.bheem.core.memory

import java.math.BigDecimal
import java.time.LocalDateTime

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
    private val spendWords = setOf("spend", "spent", "expense", "expens")

    private const val CANDIDATES = 50

    fun answer(query: String, store: MemoryStore, now: LocalDateTime): Answer {
        val spending = MemorySearch.words(query).any { it in spendWords }
        return answer(query, if (spending) store.ofKind(RecordKind.EXPENSE) else store.search(query, CANDIDATES), now)
    }

    fun answer(query: String, records: List<MemoryRecord>, now: LocalDateTime): Answer {
        val words = MemorySearch.words(query)
        if (words.any { it in spendWords }) return spending(words.toSet(), records)
        val hits = MemorySearch.search(query, records, limit = 3)
        if (hits.isEmpty()) return Answer("I found nothing about that in your notes and tasks.", emptyList())
        val lines = hits.joinToString("\n") { "- ${it.record.text} (${it.record.kind.name.lowercase()})" }
        return Answer("Here is what I found:\n$lines", hits.map { it.record.id })
    }

    private val timeWords = setOf("last", "this", "week", "month", "year", "today", "yesterday", "total", "all", "everything", "so", "far", "now")

    private fun spending(words: Set<String>, records: List<MemoryRecord>): Answer {
        val debits = records.filter { it.kind == RecordKind.EXPENSE && it.direction == "debit" }
        val subject = words - spendWords - timeWords
        val byShop = debits.filter { r -> r.merchant != null && MemorySearch.words(r.merchant).any { it in subject } }
        val matching = when {
            byShop.isNotEmpty() -> byShop
            subject.isEmpty() -> debits
            else -> return Answer("I can only total spending by shop name so far.", emptyList())
        }
        if (matching.isEmpty()) return Answer("I have no expenses logged yet.", emptyList())
        val total = matching.fold(BigDecimal.ZERO) { sum, r -> sum + BigDecimal(r.amount.orEmpty().ifEmpty { "0" }) }
        return Answer("You spent ₹${total.setScale(2).toPlainString()} across ${matching.size} records.", matching.map { it.id })
    }
}
