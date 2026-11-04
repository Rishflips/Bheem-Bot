package app.bheem.app

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bheem.core.assistant.Bubbles
import app.bheem.core.assistant.ThreadEntry
import app.bheem.core.assistant.ViewItem
import app.bheem.core.assistant.ViewItems
import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.feed.NextUp
import app.bheem.core.reminder.OemHint
import app.bheem.core.reminder.ReminderIssue
import app.bheem.core.speech.VoiceState
import app.bheem.models.host.ModelState
import app.bheem.models.host.SupplyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
            delay(60_000)
            now = java.time.LocalDateTime.now()
        }
    }
    val next = remember(sections, now) { NextUp.of(sections.upcoming, now) }
    var showUpcoming by remember { mutableStateOf(false) }

    var typed by remember { mutableStateOf("") }
    LaunchedEffect(draft) {
        draft?.let {
            typed = it
            controller.draftTaken()
        }
    }

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

        val items = remember(entries) { ViewItems.group(entries) }
        val listState = rememberLazyListState()
        val headCount = issues.size + 1
        val greeting = entries.isEmpty() && !busy
        val total = headCount + items.size + (if (busy) 1 else 0) + (if (message != null) 1 else 0) + (if (greeting) 1 else 0)
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(items.size, busy, message) {
            if (items.isEmpty() && message == null) return@LaunchedEffect
            if (placed) listState.animateScrollToItem(total - 1) else {
                listState.scrollToItem(total - 1)
                placed = true
            }
            listState.scrollBy(100_000f)
        }
        val waitingAt = items.withIndex().filter { (_, v) -> isWaiting(v) }.map { it.index + headCount }
        val waitingOffScreen by remember(waitingAt) {
            derivedStateOf {
                val visible = listState.layoutInfo.visibleItemsInfo.map { it.index }
                waitingAt.isNotEmpty() && waitingAt.none { it in visible }
            }
        }
        val scope = rememberCoroutineScope()
        if (waitingOffScreen) WaitingChip(waitingAt.size) { scope.launch { listState.animateScrollToItem(waitingAt.first()) } }

        val today = now.toLocalDate()
        LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(issues, key = { "issue-$it" }) { HealthCard(it, onFix, onDismissBattery) }
            item(key = "supply") { SupplyCard(supply, controller::getModel, controller::useMobileData, controller::cancelDownload) }
            items(items, key = { it.key }) { item ->
                when (item) {
                    is ViewItem.PlanGroup -> PlanCard(item, controller, onApprove)
                    is ViewItem.Single -> when (val entry = item.entry) {
                        is ThreadEntry.Day -> DayDivider(entry.date, today)
                        is ThreadEntry.TurnEntry -> TurnBubble(entry.turn, Bubbles.look(entries, item.index), reading, controller::readAloud)
                        is ThreadEntry.CardEntry -> ActionCard(entry.item, controller, onApprove)
                    }
                }
            }
            if (busy) item(key = "thinking") { ThinkingBubble(partial, controller::cancelSubmit) }
            message?.let { text -> item(key = "message") { MessageBanner(text, controller::clearMessage) } }
            if (greeting) item(key = "greeting") { Greeting { typed = it } }
        }
        LoadStrip(model, lastLoad, busy, controller::cancelSubmit, controller::retryModel)
        InputBar(typed, { typed = it }, busy, voice, { text ->
            controller.submit(text)
            typed = ""
        }, onStartTalk, controller::stopListening)
    }
}

private fun isWaiting(item: ViewItem): Boolean = when (item) {
    is ViewItem.PlanGroup -> item.waiting > 0
    is ViewItem.Single -> (item.entry as? ThreadEntry.CardEntry)?.item?.status == FeedStatus.PENDING
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
private fun HealthCard(issue: ReminderIssue, onFix: (ReminderIssue) -> Unit, onDismissBattery: () -> Unit) {
    val (text, action) = when (issue) {
        ReminderIssue.NotificationsOff -> stringResource(R.string.health_notifications_off) to stringResource(R.string.health_notifications_fix)
        ReminderIssue.ExactAlarmsOff -> stringResource(R.string.health_exact_off) to stringResource(R.string.health_exact_fix)
        ReminderIssue.BatteryRestricted -> OemHint.text(Build.MANUFACTURER) to stringResource(R.string.health_battery_fix)
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer), modifier = Modifier.fillMaxWidth()) {
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
private fun MessageBanner(text: String, onClose: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClose) { Text(stringResource(R.string.close)) }
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
