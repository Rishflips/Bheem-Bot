package app.bheem.core.assistant

import app.bheem.core.memory.Citation
import java.time.LocalDateTime

/** One line of the conversation. Cards are not turns: they come from the feed and stay where the owner approves them. */
sealed interface Turn {
    val at: LocalDateTime

    data class User(val text: String, val spoken: Boolean, override val at: LocalDateTime) : Turn

    data class Answer(val text: String, val citations: List<Citation>, override val at: LocalDateTime) : Turn

    data class Notice(val text: String, override val at: LocalDateTime) : Turn

    /** The daily brief, composed by code from the owner's own records. */
    data class Brief(val text: String, val citations: List<Citation>, override val at: LocalDateTime) : Turn
}

/** Where the conversation is kept between launches. */
interface TurnStore {
    fun add(turn: Turn)

    /** The newest [limit] turns, oldest first. */
    fun recent(limit: Int): List<Turn>

    fun clear()
}

class Thread(private val limit: Int = DEFAULT_LIMIT, private val store: TurnStore? = null) {
    private val items = ArrayDeque<Turn>().apply { store?.recent(limit)?.forEach { addLast(it) } }

    @Synchronized
    fun add(turn: Turn) {
        items.addLast(turn)
        while (items.size > limit) items.removeFirst()
        store?.add(turn)
    }

    @Synchronized
    fun turns(): List<Turn> = items.toList()

    @Synchronized
    fun reload() {
        val saved = store?.recent(limit) ?: return
        items.clear()
        saved.forEach { items.addLast(it) }
    }

    @Synchronized
    fun clear() {
        items.clear()
        store?.clear()
    }

    private companion object {
        const val DEFAULT_LIMIT = 60
    }
}

/** Turns are stored as a kind, a text and a short citation string; ids and kinds are internal and never contain the separators. */
object TurnCodec {
    private const val ITEM = ";"
    private const val FIELD = "|"

    fun encode(citations: List<Citation>): String = citations.joinToString(ITEM) { "${it.recordId}$FIELD${it.kind}$FIELD${it.at}" }

    fun decode(text: String): List<Citation> =
        if (text.isBlank()) emptyList() else text.split(ITEM).mapNotNull { part ->
            val f = part.split(FIELD)
            if (f.size == 3) runCatching { Citation(f[0], f[1], LocalDateTime.parse(f[2])) }.getOrNull() else null
        }
}
