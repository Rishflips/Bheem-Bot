package app.bheem.core.feed

import java.time.LocalDateTime

/** The reminders still to come, soonest first. The strip under the header shows [first] and how many there are. */
data class NextUp(val all: List<FeedItem>) {
    val first: FeedItem get() = all.first()
    val count: Int get() = all.size

    companion object {
        fun of(items: List<FeedItem>, now: LocalDateTime): NextUp? =
            items.filter { it.status == FeedStatus.DONE && it.dueAt?.isAfter(now) == true }
                .sortedBy { it.dueAt }
                .takeIf { it.isNotEmpty() }
                ?.let(::NextUp)
    }
}
