package app.bheem.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.bheem.core.feed.CardKind
import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.gate.RiskLevel

/** One card in the thread. Waiting and failed cards say what will happen and name it on the button; done cards fold to one line. */
@Composable
fun ActionCard(item: FeedItem, controller: AppController, onApprove: (FeedItem) -> Unit) {
    if (item.status == FeedStatus.DONE) DoneRow(item, controller) else OpenCard(item, controller, onApprove)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OpenCard(item: FeedItem, controller: AppController, onApprove: (FeedItem) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val failed = item.status == FeedStatus.FAILED
    val needsConfirm = item.level == RiskLevel.L2
    val container = when {
        failed -> colors.errorContainer
        needsConfirm -> colors.tertiaryContainer
        else -> colors.surfaceContainer
    }
    val onContainer = when {
        failed -> colors.onErrorContainer
        needsConfirm -> colors.onTertiaryContainer
        else -> colors.onSurface
    }
    val rail = if (failed) colors.error else colors.tertiary
    val waiting = stringResource(R.string.card_waiting)
    Surface(color = container, contentColor = onContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(rail))
            Column(Modifier.weight(1f).padding(12.dp)) {
                Row(
                    Modifier.semantics(mergeDescendants = true) { if (item.status == FeedStatus.PENDING) stateDescription = waiting },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    KindIcon(item.kind, if (failed) colors.error else colors.tertiaryContainer, if (failed) colors.onError else colors.onTertiaryContainer)
                    Column {
                        item.heardAs?.let { Text(stringResource(R.string.heard, it), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(item.detail, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                Spacer(Modifier.height(6.dp))
                val line = if (failed) {
                    item.failure?.let { stringResource(R.string.did_not_work_because, it) } ?: stringResource(R.string.did_not_work)
                } else {
                    hintFor(item)
                }
                Text(line, style = MaterialTheme.typography.bodyMedium, color = if (failed) colors.onErrorContainer else colors.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                when (item.status) {
                    FeedStatus.WORKING -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.working), style = MaterialTheme.typography.labelLarge)
                    }
                    FeedStatus.FAILED -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApprove(item) }) { Text(stringResource(R.string.try_again)) }
                        TextButton(onClick = { controller.dismiss(item.id) }) { Text(stringResource(R.string.dismiss)) }
                    }
                    else -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onApprove(item) }) { Text(verbFor(item)) }
                        if (item.heardAs != null) TextButton(onClick = { controller.editHeard(item) }) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = { controller.dismiss(item.id) }) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DoneRow(item: FeedItem, controller: AppController) {
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.secondaryContainer, contentColor = colors.onSecondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(Modifier.defaultMinSize(minHeight = 48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = colors.secondary, modifier = Modifier.size(20.dp))
                Text(doneSummary(item), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (item.canUndo) TextButton(onClick = { controller.undo(item.id) }) { Text(stringResource(R.string.undo)) }
            }
            if (item.isTask) {
                val doneTasks by controller.doneTasks.collectAsState()
                val finished = item.id in doneTasks
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (finished) Text(stringResource(R.string.task_is_done), style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                    else Spacer(Modifier.weight(1f))
                    TextButton(onClick = { if (finished) controller.reopenTask(item.id) else controller.completeTask(item.id) }) {
                        Text(stringResource(if (finished) R.string.task_reopen else R.string.task_mark_done))
                    }
                }
            }
            item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error, modifier = Modifier.padding(bottom = 8.dp)) }
        }
    }
}

@Composable
private fun KindIcon(kind: CardKind, background: Color, tint: Color) {
    Box(Modifier.size(32.dp).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(iconFor(kind)), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

private fun iconFor(kind: CardKind): Int = when (kind) {
    CardKind.REMINDER -> R.drawable.ic_alarm
    CardKind.TASK -> R.drawable.ic_task
    CardKind.NOTE, CardKind.EXPENSE -> R.drawable.ic_note
    CardKind.CALENDAR -> R.drawable.ic_calendar
    CardKind.WHATSAPP, CardKind.SMS -> R.drawable.ic_chat
    CardKind.CALL -> R.drawable.ic_call
    CardKind.ROUTE -> R.drawable.ic_route
    CardKind.PAYMENT, CardKind.OTHER -> R.drawable.ic_more
}

@Composable
private fun verbFor(item: FeedItem): String = stringResource(
    when {
        item.level == RiskLevel.L2 -> R.string.card_verb_payment
        else -> when (item.kind) {
            CardKind.REMINDER -> R.string.card_verb_reminder
            CardKind.TASK -> R.string.card_verb_task
            CardKind.NOTE -> R.string.card_verb_note
            CardKind.EXPENSE -> R.string.card_verb_expense
            CardKind.CALENDAR -> R.string.card_verb_calendar
            CardKind.WHATSAPP -> R.string.card_verb_whatsapp
            CardKind.SMS -> R.string.card_verb_sms
            CardKind.CALL -> R.string.card_verb_call
            CardKind.ROUTE -> R.string.card_verb_route
            CardKind.PAYMENT -> R.string.card_verb_payment
            CardKind.OTHER -> R.string.ok
        }
    },
)

@Composable
private fun hintFor(item: FeedItem): String = stringResource(
    when (item.kind) {
        CardKind.REMINDER -> R.string.card_hint_reminder
        CardKind.TASK -> R.string.card_hint_task
        CardKind.NOTE -> R.string.card_hint_note
        CardKind.EXPENSE -> R.string.card_hint_expense
        CardKind.CALENDAR -> R.string.card_hint_calendar
        CardKind.WHATSAPP -> R.string.card_hint_whatsapp
        CardKind.SMS -> R.string.card_hint_sms
        CardKind.CALL -> R.string.card_hint_call
        CardKind.ROUTE -> R.string.card_hint_route
        CardKind.PAYMENT -> R.string.card_hint_payment
        CardKind.OTHER -> R.string.card_hint_other
    },
)

@Composable
private fun doneSummary(item: FeedItem): String = when (item.kind) {
    CardKind.REMINDER -> stringResource(R.string.done_reminder, item.detail)
    CardKind.TASK -> stringResource(R.string.done_task, item.detail)
    CardKind.NOTE -> stringResource(R.string.done_note, item.detail)
    else -> stringResource(R.string.done_with_title, item.doneLabel, item.title)
}
