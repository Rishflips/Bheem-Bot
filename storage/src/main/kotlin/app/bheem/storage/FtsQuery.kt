package app.bheem.storage

import app.bheem.core.memory.MemorySearch

object FtsQuery {
    private val operators = setOf("and", "or", "not", "near")

    fun build(question: String): String? =
        MemorySearch.queryWords(question)
            .filter { it.length > 1 && it !in operators }
            .distinct()
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" OR ") { "\"$it\"*" }
}
