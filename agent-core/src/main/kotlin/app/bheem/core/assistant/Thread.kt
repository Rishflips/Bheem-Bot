package app.bheem.core.assistant

import app.bheem.core.memory.Citation
import java.time.LocalDateTime

/** One line of the conversation. Cards are not turns: they come from the feed and stay where the owner approves them. */
sealed interface Turn {
    val at: LocalDateTime

    data class User(val text: String, val spoken: Boolean, override val at: LocalDateTime) : Turn

    data class Answer(val text: String, val citations: List<Citation>, override val at: LocalDateTime) : Turn

    data class Notice(val text: String, override val at: LocalDateTime) : Turn
}

class Thread(private val limit: Int = DEFAULT_LIMIT) {
    private val items = ArrayDeque<Turn>()

    @Synchronized
    fun add(turn: Turn) {
        items.addLast(turn)
        while (items.size > limit) items.removeFirst()
    }

    @Synchronized
    fun turns(): List<Turn> = items.toList()

    @Synchronized
    fun clear() = items.clear()

    private companion object {
        const val DEFAULT_LIMIT = 60
    }
}
