package app.bheem.app

import android.os.Build
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.gate.RiskLevel
import app.bheem.core.reminder.OemHint
import app.bheem.core.reminder.ReminderIssue
import app.bheem.core.speech.VoiceState
import app.bheem.models.host.ModelState
import app.bheem.models.host.SupplyState

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    controller: AppController,
    issues: List<ReminderIssue>,
    onFix: (ReminderIssue) -> Unit,
    onDismissBattery: () -> Unit,
    onStartTalk: () -> Unit,
    onApprove: (FeedItem) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val sections by controller.sections.collectAsState()
    val message by controller.message.collectAsState()
    val busy by controller.busy.collectAsState()
    val model by controller.model.collectAsState()
    val lastLoad by controller.lastLoadSeconds.collectAsState()
    val supply by controller.supply.collectAsState()
    val voice by controller.voice.collectAsState()
    val draft by controller.draft.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                stringResource(R.string.today_title),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.weight(1f).padding(top = 16.dp, bottom = 8.dp).semantics { heading() },
            )
            TextButton(onClick = onOpenSettings, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) { Text(stringResource(R.string.settings_button)) }
        }
        val listState = rememberLazyListState()
        LaunchedEffect(sections.needsYou.firstOrNull()?.id) { listState.animateScrollToItem(0) }
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(issues, key = { "issue-$it" }) { HealthCard(it, onFix, onDismissBattery) }
            message?.let { text -> item(key = "message") { MessageBanner(text, controller::clearMessage) } }
            item(key = "supply") { SupplyCard(supply, controller::getModel, controller::useMobileData, controller::cancelDownload) }
            item(key = "model") { ModelCard(model, lastLoad, controller::cancelSubmit, controller::retryModel) }
            if (sections.needsYou.isEmpty() && sections.upcoming.isEmpty() && sections.recent.isEmpty()) {
                item(key = "empty") { EmptyState() }
            } else {
                section(R.string.section_needs_you, sections.needsYou) { CardFor(it, controller, onApprove) }
                section(R.string.section_upcoming, sections.upcoming) { CardFor(it, controller, onApprove) }
                section(R.string.section_recent, sections.recent) { CardFor(it, controller, onApprove) }
            }
        }
        Bar(busy, voice, draft, controller::submit, onStartTalk, controller::stopListening, controller::draftTaken)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: Int, items: List<FeedItem>, card: @Composable (FeedItem) -> Unit) {
    if (items.isEmpty()) return
    item(key = "header-$title") {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() },
        )
    }
    items(items, key = { it.id }) { card(it) }
}

@Composable
private fun SupplyCard(state: SupplyState, onGet: () -> Unit, onMobileData: () -> Unit, onCancel: () -> Unit) {
    fun gb(bytes: Long) = "%.1f GB".format(bytes / 1_000_000_000.0)
    fun percent(done: Long, total: Long) = if (total > 0) (done * 100 / total).toInt() else 0
    val rules = stringResource(R.string.supply_rules_only)
    val text = when (state) {
        SupplyState.Present -> return
        is SupplyState.Needed ->
            if (state.canDownload) stringResource(R.string.supply_needed_download, gb(state.bytes), rules)
            else stringResource(R.string.supply_needed_sideload, gb(state.bytes), state.sideloadPath, rules)
        is SupplyState.Importing -> stringResource(R.string.supply_importing, percent(state.done, state.total))
        is SupplyState.Downloading -> stringResource(R.string.supply_downloading, percent(state.done, state.total))
        is SupplyState.Paused -> state.message
        is SupplyState.Failed -> state.message
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row {
                when (state) {
                    is SupplyState.Needed -> if (state.canDownload) TextButton(onClick = onGet) { Text(stringResource(R.string.supply_download)) }
                    is SupplyState.Downloading -> TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
                    is SupplyState.Paused -> {
                        TextButton(onClick = onGet) { Text(stringResource(R.string.supply_resume)) }
                        if (state.canUseCellular) TextButton(onClick = onMobileData) { Text(stringResource(R.string.supply_mobile_data)) }
                    }
                    is SupplyState.Failed -> TextButton(onClick = onGet) { Text(stringResource(R.string.try_again)) }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ModelCard(state: ModelState, lastLoadSeconds: Long?, onCancel: () -> Unit, onRetry: () -> Unit) {
    val text = when (state) {
        ModelState.Verifying -> stringResource(R.string.model_verifying)
        is ModelState.Loading -> lastLoadSeconds?.let { stringResource(R.string.model_loading_seconds, it.toInt()) } ?: stringResource(R.string.model_loading)
        is ModelState.Failed -> state.reason
        else -> return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (state is ModelState.Failed) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
            } else {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    }
}

@Composable
private fun HealthCard(issue: ReminderIssue, onFix: (ReminderIssue) -> Unit, onDismissBattery: () -> Unit) {
    val (text, action) = when (issue) {
        ReminderIssue.NotificationsOff -> stringResource(R.string.health_notifications_off) to stringResource(R.string.health_notifications_fix)
        ReminderIssue.ExactAlarmsOff -> stringResource(R.string.health_exact_off) to stringResource(R.string.health_exact_fix)
        ReminderIssue.BatteryRestricted -> OemHint.text(Build.MANUFACTURER) to stringResource(R.string.health_battery_fix)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { onFix(issue) }) { Text(action) }
                if (issue == ReminderIssue.BatteryRestricted) TextButton(onClick = onDismissBattery) { Text(stringResource(R.string.not_now)) }
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MessageBanner(text: String, onClose: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CardFor(item: FeedItem, controller: AppController, onApprove: (FeedItem) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            val waiting = stringResource(R.string.card_waiting)
            Column(Modifier.semantics(mergeDescendants = true) { if (item.status == FeedStatus.PENDING) stateDescription = waiting }) {
                item.heardAs?.let { Text(stringResource(R.string.heard, it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text(item.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(item.detail, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(8.dp))
            when (item.status) {
                FeedStatus.PENDING -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onApprove(item) }) { Text(stringResource(if (item.level == RiskLevel.L2) R.string.confirm else R.string.ok)) }
                    if (item.heardAs != null) TextButton(onClick = { controller.editHeard(item) }) { Text(stringResource(R.string.edit)) }
                    TextButton(onClick = { controller.dismiss(item.id) }) { Text(stringResource(R.string.dismiss)) }
                }
                FeedStatus.WORKING -> Text(stringResource(R.string.working), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                FeedStatus.DONE -> Column {
                    Row {
                        Text(item.doneLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f, fill = false).padding(top = 12.dp))
                        if (item.canUndo) TextButton(onClick = { controller.undo(item.id) }) { Text(stringResource(R.string.undo)) }
                    }
                    item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                FeedStatus.FAILED -> Column {
                    Text(
                        item.failure?.let { stringResource(R.string.did_not_work_because, it) } ?: stringResource(R.string.did_not_work),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    FlowRow {
                        Button(onClick = { onApprove(item) }) { Text(stringResource(R.string.try_again)) }
                        TextButton(onClick = { controller.dismiss(item.id) }) { Text(stringResource(R.string.dismiss)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Bar(
    busy: Boolean,
    voice: VoiceState,
    draft: String?,
    onSend: (String) -> Unit,
    onStartTalk: () -> Unit,
    onStopTalk: () -> Unit,
    onDraftTaken: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    LaunchedEffect(draft) {
        draft?.let {
            text = it
            onDraftTaken()
        }
    }
    val listening = voice as? VoiceState.Listening
    val talkDescription = stringResource(R.string.talk_description)
    val listeningState = stringResource(R.string.listening_state)
    val toggleLabel = stringResource(R.string.talk_toggle)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (listening != null) {
            Text(stringResource(R.string.listening), style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { listening.level }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(if (busy) R.string.bar_thinking else R.string.bar_hint)) },
                enabled = !busy && listening == null,
                singleLine = true,
            )
            if (text.isBlank()) {
                Surface(
                    color = if (listening != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = talkDescription
                            if (listening != null) stateDescription = listeningState
                            onClick(label = toggleLabel) {
                                if (listening != null) onStopTalk() else onStartTalk()
                                true
                            }
                        }
                        .pointerInput(busy) {
                            if (!busy) {
                                detectTapGestures(onPress = {
                                    onStartTalk()
                                    tryAwaitRelease()
                                    onStopTalk()
                                })
                            }
                        },
                ) {
                    Text(
                        stringResource(if (listening != null) R.string.release else R.string.talk),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        color = if (listening != null) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                Button(
                    onClick = {
                        onSend(text)
                        text = ""
                    },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text(stringResource(R.string.send)) }
            }
        }
    }
}
