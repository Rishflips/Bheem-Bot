package app.bheem.app

import app.bheem.core.assistant.SpeakMode
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bheem.core.feed.LedgerLine
import app.bheem.models.BackendKind
import app.bheem.models.host.ModelState
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Onboarding(onStart: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.onboarding_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(20.dp))
        OnboardingPoint(R.drawable.ic_alarm, stringResource(R.string.onboarding_what))
        Spacer(Modifier.height(16.dp))
        OnboardingPoint(R.drawable.ic_lock, stringResource(R.string.onboarding_private))
        Spacer(Modifier.height(16.dp))
        OnboardingPoint(R.drawable.ic_check, stringResource(R.string.onboarding_tap))
        Spacer(Modifier.height(28.dp))
        Button(onClick = onStart, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.onboarding_start)) }
    }
}

@Composable
private fun OnboardingPoint(icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.primary)
        }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    }
}

/** A titled block of related rows on a tonal surface, so Settings reads as groups instead of one long column. */
@Composable
private fun Group(title: Int, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp, start = 4.dp).semantics { heading() })
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp), content = content)
    }
}

@Composable
fun SettingsScreen(
    controller: AppController,
    onBack: () -> Unit,
    onOpenLedger: () -> Unit,
    onOpenMemory: () -> Unit,
    onStartInterview: () -> Unit,
    onOpenLicences: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onErase: (removeModel: Boolean) -> Unit,
) {
    val model by controller.model.collectAsState()
    var step by rememberSaveable { mutableStateOf(0) }
    var removeModel by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader(stringResource(R.string.settings_title), onBack)
        Group(R.string.settings_group_assistant) {
            SettingRow(stringResource(R.string.settings_model), modelStatus(model))
            HorizontalDivider()
            SettingRow(stringResource(R.string.settings_memory), stringResource(R.string.settings_memory_hint), onOpenMemory)
            HorizontalDivider()
            SettingRow(stringResource(R.string.settings_interview), stringResource(R.string.settings_interview_hint), onStartInterview)
            HorizontalDivider()
            SpeakChoice(controller)
        }
        Group(R.string.settings_group_activity) {
            SettingRow(stringResource(R.string.settings_history), stringResource(R.string.settings_history_hint), onOpenLedger)
            HorizontalDivider()
            SettingRow(stringResource(R.string.settings_diagnostics), stringResource(R.string.settings_diagnostics_hint), onOpenDiagnostics)
        }
        Group(R.string.settings_group_about) {
            SettingRow(stringResource(R.string.settings_licences), stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), onOpenLicences)
        }
        Spacer(Modifier.height(24.dp))
        Group(R.string.settings_group_erase) {
            SettingRow(
                stringResource(R.string.settings_erase), stringResource(R.string.settings_erase_hint),
                {
                    removeModel = false
                    step = 1
                },
                danger = true,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
    if (step == 1) {
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text(stringResource(R.string.erase_title)) },
            text = { Text(stringResource(R.string.erase_body)) },
            confirmButton = { TextButton(onClick = { step = 2 }) { Text(stringResource(R.string.erase_continue)) } },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.erase_keep)) } },
        )
    }
    if (step == 2) {
        AlertDialog(
            onDismissRequest = { step = 0 },
            title = { Text(stringResource(R.string.erase_final_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.erase_final_body))
                    Row(
                        Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).toggleable(value = removeModel, role = Role.Checkbox, onValueChange = { removeModel = it }).padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = removeModel, onCheckedChange = null)
                        Text(stringResource(R.string.erase_remove_model), modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    step = 0
                    onErase(removeModel)
                }) { Text(stringResource(R.string.erase_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { step = 0 }) { Text(stringResource(R.string.erase_keep)) } },
        )
    }
}

@Composable
private fun modelStatus(state: ModelState): String = when (state) {
    is ModelState.Ready -> stringResource(R.string.settings_model_ready, stringResource(if (state.backend == BackendKind.GPU) R.string.settings_backend_gpu else R.string.settings_backend_cpu))
    ModelState.Absent -> stringResource(R.string.settings_model_absent)
    is ModelState.Failed -> stringResource(R.string.settings_model_failed, state.reason)
    ModelState.Verifying -> stringResource(R.string.model_verifying)
    is ModelState.Loading -> stringResource(R.string.model_loading)
    ModelState.Unloaded -> stringResource(R.string.settings_model_idle)
}

@Composable
private fun SettingRow(title: String, detail: String, onClick: (() -> Unit)? = null, danger: Boolean = false) {
    val base = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
    Column(
        (if (onClick != null) base.clickable(role = Role.Button, onClick = onClick) else base.semantics(mergeDescendants = true) {}).padding(vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LedgerScreen(controller: AppController, onBack: () -> Unit) {
    val lines by controller.ledger.collectAsState()
    LaunchedEffect(Unit) { controller.loadLedger() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        ScreenHeader(stringResource(R.string.ledger_title), onBack)
        val shown = lines
        if (shown == null) {
            Text(stringResource(R.string.ledger_loading), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        } else if (shown.isEmpty()) {
            Text(stringResource(R.string.ledger_empty), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        } else {
            val today = java.time.LocalDate.now()
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shown.groupBy { it.at.toLocalDate() }.forEach { (date, group) ->
                    item(key = "day-$date") { DayDivider(date, today) }
                    items(group, key = { it.id }) { line ->
                        LedgerRow(line)
                        HorizontalDivider()
                    }
                }
                if (shown.size >= LEDGER_LIMIT) {
                    item(key = "limit") { Text(stringResource(R.string.ledger_limit, LEDGER_LIMIT), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 16.dp)) }
                }
            }
        }
    }
}

private val whenFormat = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH)

@Composable
private fun LedgerRow(line: LedgerLine) {
    val voice = if (line.viaVoice) " (${stringResource(R.string.ledger_by_voice)})" else ""
    val outcome = line.outcome + (line.reason?.let { ": $it" } ?: "")
    val spoken = stringResource(R.string.ledger_line, line.title, line.detail, outcome, line.at.format(whenFormat) + voice)
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clearAndSetSemantics { contentDescription = spoken }, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SmallIcon(if (line.reason == null && line.outcome == "Done") R.drawable.ic_check else R.drawable.ic_history, MaterialTheme.colorScheme.primary, Modifier.padding(top = 2.dp))
        Column {
            Text(line.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(line.detail, style = MaterialTheme.typography.bodyLarge)
            Text(outcome, style = MaterialTheme.typography.bodyMedium)
            Text(line.at.format(whenFormat) + voice, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DiagnosticsScreen(controller: AppController, onBack: () -> Unit, onShare: (String) -> Unit) {
    val report by controller.diagnostics.collectAsState()
    LaunchedEffect(Unit) { controller.loadDiagnostics() }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
        ScreenHeader(stringResource(R.string.diagnostics_title), onBack)
        val text = report
        if (text == null) {
            Text(stringResource(R.string.ledger_loading), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        } else if (text.isEmpty()) {
            Text(stringResource(R.string.diagnostics_error), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 16.dp))
        } else {
            Button(onClick = { onShare(text) }, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.diagnostics_share)) }
            Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 16.dp))
        }
    }
}

@Composable
fun LicencesScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        ScreenHeader(stringResource(R.string.licences_title), onBack)
        Text(stringResource(R.string.licences_body), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 24.dp))
    }
}

@Composable
private fun SpeakChoice(controller: AppController) {
    val mode by controller.speakMode.collectAsState()
    val available by controller.speechAvailable.collectAsState()
    val engine by controller.engineName.collectAsState()
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp).selectableGroup()) {
        Text(stringResource(R.string.settings_speak), style = MaterialTheme.typography.titleMedium)
        if (!available) Text(stringResource(R.string.settings_speak_none), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        engine?.let { Text(stringResource(R.string.settings_speak_engine, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        for ((choice, label) in listOf(
            SpeakMode.OFF to R.string.settings_speak_off,
            SpeakMode.VOICE to R.string.settings_speak_voice,
            SpeakMode.ALWAYS to R.string.settings_speak_always,
        )) {
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).selectable(selected = mode == choice, role = Role.RadioButton, onClick = { controller.setSpeakMode(choice) }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = mode == choice, onClick = null)
                Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
            }
        }
        Text(stringResource(R.string.settings_speak_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
