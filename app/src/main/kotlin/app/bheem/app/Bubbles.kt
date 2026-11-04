package app.bheem.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bheem.core.assistant.BubbleLook
import app.bheem.core.assistant.NoticeKind
import app.bheem.core.assistant.Turn
import app.bheem.core.assistant.ViewItem
import app.bheem.core.brief.BriefLayout
import app.bheem.core.feed.FeedItem
import java.time.format.DateTimeFormatter
import java.util.Locale

fun BheemBubbleShape(squareBottom: Boolean) =
    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = if (squareBottom) 6.dp else 20.dp, bottomStart = 6.dp)

private val OwnerBubbleShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
private val CITE_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

/** One turn of the conversation. Plans are drawn by [PlanCard], not here. */
@Composable
fun TurnBubble(turn: Turn, look: BubbleLook, reading: String?, onRead: (String) -> Unit) {
    when (turn) {
        is Turn.User -> OwnerBubble(turn)
        is Turn.Brief -> BriefCard(turn, reading, onRead)
        is Turn.Plan -> Unit
        else -> BheemBubble(turn, look, reading, onRead)
    }
}

@Composable
private fun OwnerBubble(turn: Turn.User) {
    val you = stringResource(R.string.turn_you_said, turn.text)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shape = OwnerBubbleShape, modifier = Modifier.widthIn(max = 280.dp)) {
            Text(turn.text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).semantics { contentDescription = you })
        }
    }
}

@Composable
private fun BheemBubble(turn: Turn, look: BubbleLook, reading: String?, onRead: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = BheemBubbleShape(look.squareBottom)
    Column(Modifier.fillMaxWidth().padding(end = 24.dp)) {
        if (look.showLabel && turn !is Turn.Chat) {
            Text(stringResource(R.string.turn_bheem), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        }
        when (turn) {
            is Turn.Chat -> Surface(color = Color.Transparent, border = BorderStroke(1.dp, colors.outline), shape = shape, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallIcon(R.drawable.ic_sparkle, colors.onSurfaceVariant)
                        Text(stringResource(R.string.turn_bheem_model), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(turn.text, style = MaterialTheme.typography.bodyLarge)
                    ReadAloud(turn.text, reading, onRead)
                }
            }
            is Turn.Notice -> {
                val clarify = NoticeKind.of(turn.text) == NoticeKind.CLARIFY
                Surface(color = if (clarify) colors.surfaceContainer else colors.surfaceContainerHigh, shape = shape, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallIcon(if (clarify) R.drawable.ic_help else R.drawable.ic_info, colors.primary, Modifier.padding(top = 3.dp))
                        Column {
                            Text(turn.text, style = MaterialTheme.typography.bodyLarge)
                            ReadAloud(turn.text, reading, onRead)
                        }
                    }
                }
            }
            is Turn.Answer -> Surface(color = colors.surfaceContainer, shape = shape, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(turn.text, style = MaterialTheme.typography.bodyLarge)
                    ReadAloud(turn.text, reading, onRead)
                    CitationRow(turn.citations)
                }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ReadAloud(text: String, reading: String?, onRead: (String) -> Unit) {
    TextButton(onClick = { onRead(text) }) { Text(stringResource(if (reading == text) R.string.bubble_stop_reading else R.string.bubble_read)) }
}

/** "From 2 records", which opens to the list with dates. */
@Composable
private fun CitationRow(citations: List<app.bheem.core.memory.Citation>) {
    if (citations.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val count = pluralStringResource(R.plurals.cite_count, citations.size, citations.size)
    Column(Modifier.clickable { open = !open }.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SmallIcon(R.drawable.ic_expand, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.padding(start = 4.dp))
        }
        if (open) citations.forEach {
            Text(stringResource(R.string.cite_item, it.kind, it.at.format(CITE_DAY)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The morning brief is a page Bheem prepared, not a reply: a headline, small sections, and when it was composed. */
@Composable
private fun BriefCard(turn: Turn.Brief, reading: String?, onRead: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val view = BriefLayout.parse(turn.text)
    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(view.headline, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            view.note?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            view.sections.forEach { section ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallIcon(sectionIcon(section.title), colors.primary)
                    Text(section.title, style = MaterialTheme.typography.titleMedium)
                }
                section.rows.forEach { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 24.dp)) }
            }
            ReadAloud(turn.text, reading, onRead)
            val from = if (turn.citations.isEmpty()) "" else ", " + pluralStringResource(R.plurals.brief_from, turn.citations.size, turn.citations.size)
            Text(stringResource(R.string.brief_composed, turn.at.format(CLOCK)) + from, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

private fun sectionIcon(title: String): Int = when {
    title.startsWith("Clash") -> R.drawable.ic_warning
    title.startsWith("Missed") -> R.drawable.ic_history
    title.startsWith("Not followed") -> R.drawable.ic_flag
    else -> R.drawable.ic_alarm
}

/** A plan: one container that says what it is, holds its steps, and offers Do all while more than one waits. */
@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun PlanCard(group: ViewItem.PlanGroup, controller: AppController, onApprove: (FeedItem) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallIcon(R.drawable.ic_more, colors.primary)
                Text(pluralStringResource(R.plurals.plan_header, group.turn.steps.size, group.turn.steps.size), style = MaterialTheme.typography.titleMedium)
            }
            if (group.turn.skipped.isNotEmpty()) {
                Text(stringResource(R.string.plan_skipped, group.turn.skipped.joinToString("; ")), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            group.cards.forEachIndexed { i, card -> ActionCard(card, controller, onApprove, step = i + 1, container = colors.surface) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val pending = group.cards.filter { it.status == app.bheem.core.feed.FeedStatus.PENDING }.map { it.id }
                if (pending.size > 1) Button(onClick = { controller.doAll(pending) }) { Text(stringResource(R.string.plan_do_all)) }
                Text(stringResource(R.string.plan_done_count, group.done, group.cards.size), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** The first thing on a fresh install: who Bheem is, and four things to try. Chips fill the input; nothing is sent. */
@androidx.compose.foundation.layout.ExperimentalLayoutApi
@Composable
fun Greeting(onChip: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.turn_bheem), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
        Surface(color = colors.surfaceContainer, shape = BheemBubbleShape(false), modifier = Modifier.fillMaxWidth().padding(end = 24.dp)) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.greeting_one), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.greeting_two), style = MaterialTheme.typography.bodyLarge)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(R.string.chip_remind, R.string.chip_note, R.string.chip_tomorrow, R.string.chip_brief).forEach { id ->
                val text = stringResource(id)
                AssistChip(onClick = { onChip(text) }, label = { Text(text) })
            }
        }
    }
}
