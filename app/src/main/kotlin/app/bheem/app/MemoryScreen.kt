package app.bheem.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bheem.core.memory.FactKind
import app.bheem.core.memory.FactSlots
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.RecordKind

/** Everything Bheem remembers about the owner, in one place: what they said, and what Bheem only guessed. Both can be removed here. */
@Composable
fun MemoryScreen(controller: AppController, onBack: () -> Unit) {
    val records by controller.memory.collectAsState()
    val facts = records.filter { it.kind == RecordKind.FACT }
    val guesses = records.filter { it.kind == RecordKind.GUESS }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader(stringResource(R.string.memory_title), onBack)
        if (facts.isEmpty() && guesses.isEmpty()) {
            Text(stringResource(R.string.memory_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        }
        if (guesses.isNotEmpty()) {
            Heading(R.string.memory_guess_header)
            Text(stringResource(R.string.memory_guess_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                guesses.forEach { MemoryRow(it, confirm = { controller.confirmGuess(it.id) }, delete = { controller.deleteMemory(it.id) }) }
            }
            TextButton(onClick = controller::forgetGuesses) { Text(stringResource(R.string.memory_forget_guesses)) }
        }
        if (facts.isNotEmpty()) {
            val byKind = facts.groupBy { FactSlots.kind(it.text) }
            for (kind in FactKind.entries) {
                val group = byKind[kind].orEmpty()
                if (group.isEmpty()) continue
                Heading(kindLabel(kind))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.forEach { MemoryRow(it, confirm = null, delete = { controller.deleteMemory(it.id) }) }
                }
            }
        }
        Text(stringResource(R.string.memory_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
    }
}

private fun kindLabel(kind: FactKind): Int = when (kind) {
    FactKind.ABOUT_YOU -> R.string.memory_kind_about_you
    FactKind.PEOPLE -> R.string.memory_kind_people
    FactKind.PREFERENCES -> R.string.memory_kind_preferences
    FactKind.ROUTINE -> R.string.memory_kind_routine
    FactKind.GOALS -> R.string.memory_kind_goals
    FactKind.OTHER -> R.string.memory_kind_other
}

@Composable
private fun Heading(text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp).semantics { heading() })
}

@Composable
private fun MemoryRow(record: MemoryRecord, confirm: (() -> Unit)?, delete: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(record.text, style = MaterialTheme.typography.bodyLarge)
            androidx.compose.foundation.layout.Row {
                if (confirm != null) TextButton(onClick = confirm) { Text(stringResource(R.string.memory_confirm)) }
                TextButton(onClick = delete) { Text(stringResource(R.string.memory_delete)) }
            }
        }
    }
}
