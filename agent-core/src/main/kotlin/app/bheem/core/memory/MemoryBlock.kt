package app.bheem.core.memory

import java.util.Locale

/**
 * The few facts that go in front of the model with a chat message. Only facts the owner confirmed (never a guess, never a note), the
 * ones that match the message first, the owner's name always, and a short cap so the prompt stays small: every prompt token costs time on
 * every turn. The block is data, introduced as the owner's own words.
 */
object MemoryBlock {
    const val HEADER = "Things the owner told you earlier:"
    private const val MAX_LINES = 4
    private const val MAX_LINE_CHARS = 120
    private const val MAX_CHARS = 320
    private const val AT_LEAST = 3

    private val nameFact = Regex("""^(my name is|call me)\b""", RegexOption.IGNORE_CASE)
    private val slotOrder = listOf("city", "work")
    private val stop = setOf("what", "whats", "is", "the", "and", "for", "you", "your", "are", "was", "were", "how", "who", "where", "when", "why", "can", "does", "did", "have", "has", "with", "that", "this", "from", "about", "tell", "know", "any", "not")

    fun build(records: List<MemoryRecord>, query: String): String? {
        val facts = records.filter { it.kind == RecordKind.FACT }
        if (facts.isEmpty()) return null
        val wanted = tokens(query)
        val scored = facts.map { f ->
            val words = tokens(f.text)
            val overlap = wanted.count { w -> words.any { it == w || it.startsWith(w) || w.startsWith(it) && it.length >= 4 } }
            Triple(f, overlap, nameFact.containsMatchIn(f.text))
        }
        val picked = LinkedHashSet<MemoryRecord>()
        scored.filter { it.third }.sortedByDescending { it.first.createdAt }.take(1).forEach { picked += it.first }
        for (slot in slotOrder) {
            scored.filter { FactSlots.slot(it.first.text) == slot }.sortedByDescending { it.first.createdAt }.take(1).forEach { picked += it.first }
        }
        scored.filter { it.second > 0 }.sortedWith(compareByDescending<Triple<MemoryRecord, Int, Boolean>> { it.second }.thenByDescending { it.first.createdAt }).forEach { picked += it.first }
        if (picked.size < AT_LEAST) scored.sortedByDescending { it.first.createdAt }.forEach { if (picked.size < AT_LEAST) picked += it.first }

        val lines = ArrayList<String>()
        var size = HEADER.length
        for (f in picked.take(MAX_LINES)) {
            val line = "- " + f.text.replace(Regex("""\s+"""), " ").trim().take(MAX_LINE_CHARS)
            if (size + 1 + line.length > MAX_CHARS) break
            lines += line
            size += 1 + line.length
        }
        return if (lines.isEmpty()) null else HEADER + "\n" + lines.joinToString("\n")
    }

    /** The confirmed facts that contain every meaningful word of what the owner asked to forget. Code does this match; the model never sees the list. */
    fun matching(records: List<MemoryRecord>, query: String): List<String> {
        val wanted = tokens(query)
        if (wanted.isEmpty()) return emptyList()
        return records.filter { it.kind == RecordKind.FACT }.filter { f ->
            val words = tokens(f.text)
            wanted.all { w -> words.any { it == w || it.startsWith(w) || w.startsWith(it) && it.length >= 4 } }
        }.map { it.text }
    }

    private fun tokens(text: String): List<String> =
        text.lowercase(Locale.ENGLISH).replace("'s", "").split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 3 && it !in stop }
}
