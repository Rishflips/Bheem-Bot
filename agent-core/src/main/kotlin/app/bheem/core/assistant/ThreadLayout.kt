package app.bheem.core.assistant

import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import java.time.LocalDate
import java.time.LocalDateTime

sealed interface ThreadEntry {
    /** Stable across reloads and across older turns dropping off the front, so the list does not jump. */
    val key: String

    data class Day(val date: LocalDate, override val key: String) : ThreadEntry

    data class TurnEntry(val turn: Turn, override val key: String) : ThreadEntry

    data class CardEntry(val item: FeedItem) : ThreadEntry {
        override val key: String get() = "card-${item.id}"
    }
}

sealed interface DayLabel {
    data object Today : DayLabel

    data object Yesterday : DayLabel

    data class Other(val date: LocalDate) : DayLabel

    companion object {
        fun of(date: LocalDate, today: LocalDate): DayLabel = when (date) {
            today -> Today
            today.minusDays(1) -> Yesterday
            else -> Other(date)
        }
    }
}

/**
 * The whole conversation as one list, oldest first: each card sits under the turn that made it, so a person reads
 * "I asked, Bheem proposed, I tapped" as one story. A plan's cards follow the plan turn in step order; any other card
 * follows the last turn at or before the moment its proposal was created.
 */
object ThreadLayout {
    fun build(turns: List<Turn>, cards: List<FeedItem>): List<ThreadEntry> {
        val byId = cards.associateBy { it.id }
        val planned = turns.filterIsInstance<Turn.Plan>().flatMap { it.steps }.toSet()
        val oldestTurn = turns.firstOrNull()?.at
        val loose = cards.filter { it.id !in planned }.sortedBy { it.createdAt }
            .filter { oldestTurn == null || !it.createdAt.isBefore(oldestTurn) || it.status != FeedStatus.DONE }
        val atTop = mutableListOf<FeedItem>()
        val after = List(turns.size) { mutableListOf<FeedItem>() }
        for (card in loose) {
            val index = turns.indexOfLast { !it.at.isAfter(card.createdAt) }
            if (index < 0) atTop += card else after[index] += card
        }

        val dated = mutableListOf<Pair<LocalDate, ThreadEntry>>()
        val seenAt = HashMap<LocalDateTime, Int>()
        atTop.forEach { dated += it.createdAt.toLocalDate() to ThreadEntry.CardEntry(it) }
        turns.forEachIndexed { index, turn ->
            val nth = seenAt.merge(turn.at, 1, Int::plus)!! - 1
            val date = turn.at.toLocalDate()
            dated += date to ThreadEntry.TurnEntry(turn, "turn-${turn.at}-$nth")
            if (turn is Turn.Plan) turn.steps.mapNotNull { byId[it] }.forEach { dated += date to ThreadEntry.CardEntry(it) }
            after[index].forEach { dated += date to ThreadEntry.CardEntry(it) }
        }

        val entries = ArrayList<ThreadEntry>(dated.size + 4)
        val dayCount = HashMap<LocalDate, Int>()
        var previous: LocalDate? = null
        for ((date, entry) in dated) {
            if (date != previous) {
                val nth = dayCount.merge(date, 1, Int::plus)!! - 1
                entries += ThreadEntry.Day(date, "day-$date-$nth")
                previous = date
            }
            entries += entry
        }
        return entries
    }

    fun firstWaiting(entries: List<ThreadEntry>): Int? =
        entries.indexOfFirst { it is ThreadEntry.CardEntry && it.item.status == FeedStatus.PENDING }.takeIf { it >= 0 }

    fun waitingCount(entries: List<ThreadEntry>): Int =
        entries.count { it is ThreadEntry.CardEntry && it.item.status == FeedStatus.PENDING }
}

/** How one turn is drawn: whether it carries the "Bheem" label and whether its bottom corners are squared to sit on a card. */
data class BubbleLook(val showLabel: Boolean, val squareBottom: Boolean)

object Bubbles {
    private fun isBheem(entry: ThreadEntry?) = entry is ThreadEntry.TurnEntry && entry.turn !is Turn.User

    fun look(entries: List<ThreadEntry>, index: Int): BubbleLook {
        val entry = entries[index]
        if (!isBheem(entry)) return BubbleLook(showLabel = false, squareBottom = false)
        return BubbleLook(
            showLabel = !isBheem(entries.getOrNull(index - 1)),
            squareBottom = entries.getOrNull(index + 1) is ThreadEntry.CardEntry,
        )
    }
}

/** A notice that asks something is drawn as a question; every other notice is plain information. */
enum class NoticeKind {
    CLARIFY, INFO;

    companion object {
        fun of(text: String): NoticeKind = if (text.trimEnd().endsWith("?")) CLARIFY else INFO
    }
}

/** What the list draws: one entry, or a plan together with the cards of its steps as a single container. */
sealed interface ViewItem {
    val key: String

    data class Single(val entry: ThreadEntry, val index: Int) : ViewItem {
        override val key: String get() = entry.key
    }

    data class PlanGroup(val turn: Turn.Plan, val cards: List<FeedItem>, val index: Int, override val key: String) : ViewItem {
        val done: Int get() = cards.count { it.status == FeedStatus.DONE }
        val waiting: Int get() = cards.count { it.status == FeedStatus.PENDING }
    }
}

object ViewItems {
    fun group(entries: List<ThreadEntry>): List<ViewItem> {
        val items = ArrayList<ViewItem>(entries.size)
        var i = 0
        while (i < entries.size) {
            val entry = entries[i]
            val plan = (entry as? ThreadEntry.TurnEntry)?.turn as? Turn.Plan
            if (plan == null) {
                items += ViewItem.Single(entry, i)
                i++
                continue
            }
            val steps = plan.steps.toSet()
            val cards = ArrayList<FeedItem>()
            var j = i + 1
            while (j < entries.size) {
                val next = entries[j]
                if (next is ThreadEntry.CardEntry && next.item.id in steps) {
                    cards += next.item
                    j++
                } else {
                    break
                }
            }
            items += ViewItem.PlanGroup(plan, cards, i, entry.key)
            i = j
        }
        return items
    }
}
