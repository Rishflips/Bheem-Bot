package app.bheem.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bheem.core.assistant.DayLabel
import app.bheem.core.feed.NextUp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One line under the header: the next reminder and how many are coming. Tapping it opens the full list. */
@Composable
fun NextStrip(next: NextUp, onOpen: () -> Unit) {
    val line = stringResource(R.string.next_strip, next.first.detail)
    val description = stringResource(R.string.next_strip_description, next.first.detail, next.count)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(onClick = onOpen)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = description
            },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (next.count > 1) Text(stringResource(R.string.next_count, next.count), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpcomingSheet(next: NextUp, onClose: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.upcoming_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            next.all.forEach { Text(it.detail, style = MaterialTheme.typography.bodyLarge) }
        }
    }
}

/** Shown only while every card that waits for a tap is scrolled out of view. */
@Composable
fun WaitingChip(count: Int, onClick: () -> Unit) {
    val description = stringResource(R.string.waiting_chip_description, count)
    AssistChip(
        onClick = onClick,
        label = { Text(stringResource(R.string.waiting_chip, count)) },
        modifier = Modifier.semantics { contentDescription = description },
    )
}

@Composable
fun DayDivider(date: LocalDate, today: LocalDate) {
    val text = when (val label = DayLabel.of(date, today)) {
        DayLabel.Today -> stringResource(R.string.day_today)
        DayLabel.Yesterday -> stringResource(R.string.day_yesterday)
        is DayLabel.Other -> label.date.format(DAY_FORMAT)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).semantics { heading() },
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)
