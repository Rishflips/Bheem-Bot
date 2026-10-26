package app.bheem.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.os.PowerManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bheem.core.speech.VoiceState
import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.gate.RiskLevel
import app.bheem.core.reminder.OemHint
import app.bheem.core.reminder.ReminderHealth
import app.bheem.core.reminder.ReminderIssue
import app.bheem.models.host.ModelState
import app.bheem.models.host.SupplyState

class MainActivity : ComponentActivity() {
    private var issues by mutableStateOf<List<ReminderIssue>>(emptyList())
    private val preferences by lazy { getSharedPreferences("bheem", MODE_PRIVATE) }

    private fun reminderIssues(): List<ReminderIssue> {
        val notifications = getSystemService(NotificationManager::class.java)
        val channelOn = (notifications.getNotificationChannel(Notifications.REMINDERS)?.importance ?: NotificationManager.IMPORTANCE_DEFAULT) != NotificationManager.IMPORTANCE_NONE
        val found = ReminderHealth.issues(
            notificationsOn = notifications.areNotificationsEnabled() && channelOn,
            exactOn = getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            ignoringBatteryOptimizations = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName),
        )
        return if (preferences.getBoolean(BATTERY_HINT_SHOWN, false)) found - ReminderIssue.BatteryRestricted else found
    }

    private fun fix(issue: ReminderIssue) {
        when (issue) {
            ReminderIssue.NotificationsOff -> startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            ReminderIssue.ExactAlarmsOff -> startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            ReminderIssue.BatteryRestricted -> {
                preferences.edit().putBoolean(BATTERY_HINT_SHOWN, true).apply()
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun dismissBatteryHint() {
        preferences.edit().putBoolean(BATTERY_HINT_SHOWN, true).apply()
        issues = reminderIssues()
    }

    override fun onResume() {
        super.onResume()
        issues = reminderIssues()
        VoiceReplay.maybeRun(this)
        ((application as BheemApp).session.value as? Session.Ready)?.controller?.refreshSupply()
        ((application as BheemApp).session.value as? Session.Ready)?.controller?.reminderServices?.let { services ->
            Thread { runCatching { services.acknowledgeMissed() } }.start()
        }
    }

    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) readyController()?.say("The microphone is off for Bheem, so talking does not work. Turn it on in the app settings, or type instead.")
    }

    private fun readyController(): AppController? = ((application as BheemApp).session.value as? Session.Ready)?.controller

    private fun startTalking() {
        val controller = readyController() ?: return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.startListening() else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }

    override fun onPause() {
        readyController()?.cancelListening()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val app = application as BheemApp
                    val session by app.session.collectAsState()
                    when (val state = session) {
                        Session.Starting -> Notice("Getting ready...")
                        Session.Erasing -> Notice("Erasing...")
                        is Session.Failed -> Notice("Bheem could not start: ${state.message}")
                        Session.NeedsErase -> Notice(
                            "Bheem cannot read its saved data on this phone (its key is gone, for example after moving to a new phone). " +
                                "Erasing starts fresh. Your notes, tasks and history here will be lost.",
                            "Erase and start again",
                            app::eraseEverything,
                        )
                        is Session.Ready -> TodayScreen(state.controller, issues, ::fix, ::dismissBatteryHint, ::startTalking)
                    }
                }
            }
        }
    }
}

private const val BATTERY_HINT_SHOWN = "battery_hint_shown"

@Composable
private fun Notice(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (action != null) Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
    }
}

@Composable
private fun TodayScreen(controller: AppController, issues: List<ReminderIssue>, onFix: (ReminderIssue) -> Unit, onDismissBattery: () -> Unit, onStartTalk: () -> Unit) {
    val feed by controller.feed.collectAsState()
    val message by controller.message.collectAsState()
    val busy by controller.busy.collectAsState()
    val model by controller.model.collectAsState()
    val lastLoad by controller.lastLoadSeconds.collectAsState()
    val supply by controller.supply.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        issues.forEach { HealthCard(it, onFix, onDismissBattery) }
        message?.let { MessageBanner(it, controller::clearMessage) }
        SupplyCard(supply, controller::getModel, controller::useMobileData, controller::cancelDownload)
        ModelCard(model, lastLoad, controller::cancelSubmit)
        if (feed.isEmpty()) {
            EmptyState(Modifier.weight(1f))
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(feed.firstOrNull()?.id) { listState.animateScrollToItem(0) }
            LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(feed, key = { it.id }) { CardFor(it, controller) }
            }
        }
        val voice by controller.voice.collectAsState()
        val draft by controller.draft.collectAsState()
        Bar(busy, voice, draft, controller::submit, onStartTalk, controller::stopListening, controller::draftTaken)
    }
}

@Composable
private fun SupplyCard(state: SupplyState, onGet: () -> Unit, onMobileData: () -> Unit, onCancel: () -> Unit) {
    fun gb(bytes: Long) = "%.1f GB".format(bytes / 1_000_000_000.0)
    fun percent(done: Long, total: Long) = if (total > 0) done * 100 / total else 0L
    val rules = "Until then Bheem handles requests like \"remind me to ... at ...\", \"note ...\" and \"task ...\"."
    val text = when (state) {
        SupplyState.Present -> return
        is SupplyState.Needed ->
            if (state.canDownload) "Bheem needs its language model (${gb(state.bytes)}) to understand free-form requests. $rules"
            else "Bheem needs its language model (${gb(state.bytes)}) to understand free-form requests. Put the model file at ${state.sideloadPath} and open Bheem again. $rules"
        is SupplyState.Importing -> "Checking and moving the model into Bheem's private storage, ${percent(state.done, state.total)}%."
        is SupplyState.Downloading -> "Downloading the model, ${percent(state.done, state.total)}%. It is checked when it finishes."
        is SupplyState.Paused -> state.message
        is SupplyState.Failed -> state.message
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row {
                when (state) {
                    is SupplyState.Needed -> if (state.canDownload) TextButton(onClick = onGet) { Text("Download on Wi-Fi") }
                    is SupplyState.Downloading -> TextButton(onClick = onCancel) { Text("Cancel") }
                    is SupplyState.Paused -> {
                        TextButton(onClick = onGet) { Text("Resume") }
                        if (state.canUseCellular) TextButton(onClick = onMobileData) { Text("Use mobile data") }
                    }
                    is SupplyState.Failed -> TextButton(onClick = onGet) { Text("Try again") }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun ModelCard(state: ModelState, lastLoadSeconds: Long?, onCancel: () -> Unit) {
    val text = when (state) {
        ModelState.Verifying -> "Checking the model file. This happens once after an install or update."
        is ModelState.Loading -> "Getting ready" + (lastLoadSeconds?.let { ", about $it s" } ?: "") + ". The first answer waits for this."
        is ModelState.Failed -> state.reason
        else -> return
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            if (state !is ModelState.Failed) TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}

@Composable
private fun HealthCard(issue: ReminderIssue, onFix: (ReminderIssue) -> Unit, onDismissBattery: () -> Unit) {
    val (text, action) = when (issue) {
        ReminderIssue.NotificationsOff -> "Notifications for Bheem are off, so reminders cannot appear." to "Turn on notifications"
        ReminderIssue.ExactAlarmsOff -> "Reminders may arrive late until you allow exact times." to "Allow exact reminders"
        ReminderIssue.BatteryRestricted -> OemHint.text(Build.MANUFACTURER) to "Open battery settings"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = { onFix(issue) }) { Text(action) }
                if (issue == ReminderIssue.BatteryRestricted) TextButton(onClick = onDismissBattery) { Text("Not now") }
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        Text("Nothing needs you.", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Try: remind me tomorrow at 9 AM to call Ravi",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageBanner(text: String, onClose: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClose) { Text("Close") }
        }
    }
}

@Composable
private fun CardFor(item: FeedItem, controller: AppController) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            item.heardAs?.let { Text("Heard: \"$it\"", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text(item.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(item.detail, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            when (item.status) {
                FeedStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.approve(item.id) }) { Text(if (item.level == RiskLevel.L2) "Confirm" else "OK") }
                    if (item.heardAs != null) TextButton(onClick = { controller.editHeard(item) }) { Text("Edit") }
                    TextButton(onClick = { controller.dismiss(item.id) }) { Text("Dismiss") }
                }
                FeedStatus.WORKING -> Text("Working...", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                FeedStatus.DONE -> Column {
                    Row {
                        Text(item.doneLabel, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                        if (item.canUndo) TextButton(onClick = { controller.undo(item.id) }) { Text("Undo") }
                    }
                    item.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                FeedStatus.FAILED -> Column {
                    Text(item.failure ?: "Did not work", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    Row {
                        Button(onClick = { controller.approve(item.id) }) { Text("Try again") }
                        TextButton(onClick = { controller.dismiss(item.id) }) { Text("Dismiss") }
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
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        if (listening != null) {
            Text("Listening... let go to send", style = MaterialTheme.typography.labelMedium)
            LinearProgressIndicator(progress = { listening.level }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (busy) "Thinking..." else "Tell Bheem what you need") },
                enabled = !busy && listening == null,
                singleLine = true,
            )
            if (text.isBlank()) {
                Surface(
                    color = if (listening != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .semantics { contentDescription = "Hold to talk" }
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
                    Text(if (listening != null) "Release" else "Talk", modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp), color = MaterialTheme.colorScheme.onPrimary)
                }
            } else {
                Button(
                    onClick = {
                        onSend(text)
                        text = ""
                    },
                    enabled = !busy,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Send") }
            }
        }
    }
}
