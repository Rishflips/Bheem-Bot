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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.derivedStateOf
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
import app.bheem.core.assistant.Turn
import kotlinx.coroutines.launch
import app.bheem.core.feed.NextUp
import app.bheem.core.assistant.ThreadEntry
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
    val entries by controller.entries.collectAsState()
    val message by controller.message.collectAsState()
    val partial by controller.partial.collectAsState()
    val reading by controller.reading.collectAsState()
    val busy by controller.busy.collectAsState()
    val model by controller.model.collectAsState()
    val lastLoad by controller.lastLoadSeconds.collectAsState()
    val supply by controller.supply.collectAsState()
    val voice by controller.voice.collectAsState()
    val draft by controller.draft.collectAsState()

    var now by remember { mutableStateOf(java.time.LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            now = java.time.LocalDateTime.now()
        }
    }
    val next = remember(sections, now) { NextUp.of(sections.upcoming, now) }
    var showUpcoming by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Header(headerStatus(model, lastLoad, voice), onOpenSettings)
        next?.let {
            NextStrip(it) { showUpcoming = true }
            if (showUpcoming) UpcomingSheet(it) { showUpcoming = false }
        }

        val listState = rememberLazyListState()
        val headCount = issues.size + 1
        val total = headCount + entries.size + (if (partial != null) 1 else 0) + 1 + (if (message != null) 1 else 0) + (if (entries.isEmpty()) 1 else 0)
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(entries.size, partial != null, message) {
            if (entries.isEmpty() && message == null) return@LaunchedEffect
            if (placed) listState.animateScrollToItem(total - 1) else {
                listState.scrollToItem(total - 1)
                placed = true
            }
        }
        val waitingAt = entries.withIndex().filter { (_, e) -> e is ThreadEntry.CardEntry && e.item.status == FeedStatus.PENDING }.map { it.index + headCount }
        val waitingOffScreen by remember(waitingAt) {
            derivedStateOf {
                val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
                waitingAt.isNotEmpty() && waitingAt.none { it in visible }
            }
        }
        val scope = androidx.compose.runtime.rememberCoroutineScope()
        if (waitingOffScreen) WaitingChip(waitingAt.size) { scope.launch { listState.animateScrollToItem(waitingAt.first()) } }

        val today = now.toLocalDate()
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(issues, key = { "issue-$it" }) { HealthCard(it, onFix, onDismissBattery) }
            item(key = "supply") { SupplyCard(supply, controller::getModel, controller::useMobileData, controller::cancelDownload) }
            val waiting = sections.needsYou.filter { it.status == FeedStatus.PENDING }.map { it.id }.toSet()
            items(entries, key = { it.key }) { entry ->
                when (entry) {
                    is ThreadEntry.Day -> DayDivider(entry.date, today)
                    is ThreadEntry.TurnEntry -> TurnBubble(entry.turn, waiting, controller::doAll, reading, controller::readAloud)
                    is ThreadEntry.CardEntry -> ActionCard(entry.item, controller, onApprove)
                }
            }
            partial?.let { text -> item(key = "partial") { PartialBubble(text, controller::cancelSubmit) } }
            item(key = "model") { ModelCard(model, lastLoad, controller::cancelSubmit, controller::retryModel) }
            message?.let { text -> item(key = "message") { MessageBanner(text, controller::clearMessage) } }
            if (entries.isEmpty()) item(key = "empty") { EmptyState() }
        }
        Bar(busy, voice, draft, controller::submit, onStartTalk, controller::stopListening, controller::draftTaken)
    }
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
private fun TurnBubble(turn: Turn, waiting: Set<String>, onDoAll: (List<String>) -> Unit, reading: String?, onRead: (String) -> Unit) {
    val mine = turn is Turn.User
    val (label, text) = when (turn) {
        is Turn.User -> stringResource(R.string.turn_you) to turn.text
        is Turn.Answer -> stringResource(R.string.turn_bheem) to turn.text
        is Turn.Notice -> stringResource(R.string.turn_bheem) to turn.text
        is Turn.Chat -> stringResource(R.string.turn_bheem_model) to turn.text
        is Turn.Brief -> stringResource(R.string.turn_bheem) to turn.text
        is Turn.Plan -> stringResource(R.string.turn_bheem) to androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.plan_steps, turn.steps.size, turn.steps.size)
    }
    val colors = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (mine) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Surface(color = colors, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(if (mine) 0.85f else 0.95f)) {
            Column(Modifier.padding(12.dp)) {
                Text(text, style = MaterialTheme.typography.bodyLarge)
                if (turn is Turn.Plan) {
                    if (turn.skipped.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.plan_skipped, turn.skipped.joinToString("; ")), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val pending = turn.steps.filter { it in waiting }
                    if (pending.size > 1) TextButton(onClick = { onDoAll(pending) }) { Text(stringResource(R.string.plan_do_all)) }
                }
                val readable = when (turn) {
                    is Turn.Answer -> turn.text
                    is Turn.Chat -> turn.text
                    is Turn.Brief -> turn.text
                    is Turn.Notice -> turn.text
                    else -> null
                }
                if (readable != null) TextButton(onClick = { onRead(readable) }) { Text(stringResource(if (reading == readable) R.string.bubble_stop_reading else R.string.bubble_read)) }
                val citations = (turn as? Turn.Answer)?.citations ?: (turn as? Turn.Brief)?.citations ?: emptyList()
                if (citations.isNotEmpty()) {
                    val from = citations.joinToString("; ") { "${it.kind} ${it.at.format(CITE_DAY)}" }
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.turn_from, from), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun PartialBubble(text: String, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.turn_bheem_model), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth(0.95f)) {
            Column(Modifier.padding(12.dp)) {
                TextButton(onClick = onStop) { Text(stringResource(R.string.chat_stop)) }
                Text(text.ifEmpty { stringResource(R.string.bar_thinking) }, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

private val CITE_DAY: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)

@Composable
private fun MessageBanner(text: String, onClose: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
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

@Composable
private fun headerStatus(model: ModelState, lastLoadSeconds: Long?, voice: VoiceState): String = when {
    voice is VoiceState.Listening -> stringResource(R.string.header_listening)
    model is ModelState.Loading || model == ModelState.Verifying ->
        lastLoadSeconds?.let { stringResource(R.string.header_getting_ready_seconds, it.toInt()) } ?: stringResource(R.string.header_getting_ready)
    else -> stringResource(R.string.header_on_this_phone)
}
