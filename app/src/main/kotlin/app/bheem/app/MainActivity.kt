package app.bheem.app

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.bheem.core.feed.FeedItem
import app.bheem.core.feed.FeedStatus
import app.bheem.core.gate.RiskLevel
import app.bheem.storage.KeyLostException
import kotlinx.coroutines.launch

private sealed interface Startup {
    data object Starting : Startup

    data class Ready(val controller: AppController) : Startup

    data object NeedsErase : Startup

    data class Failed(val message: String) : Startup
}

class MainActivity : ComponentActivity() {
    private var exactAllowed by mutableStateOf(true)

    private fun canScheduleExact() = getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    override fun onResume() {
        super.onResume()
        exactAllowed = canScheduleExact()
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
                    val scope = rememberCoroutineScope()
                    var attempt by remember { mutableIntStateOf(0) }
                    val startup by produceState<Startup>(Startup.Starting, attempt) {
                        value = Startup.Starting
                        value = app.awaitGraph().fold(
                            { Startup.Ready(AppController(it)) },
                            { if (it is KeyLostException) Startup.NeedsErase else Startup.Failed(it.message ?: "unknown error") },
                        )
                    }
                    when (val state = startup) {
                        Startup.Starting -> Notice("Getting ready...")
                        is Startup.Failed -> Notice("Bheem could not start: ${state.message}")
                        Startup.NeedsErase -> Notice(
                            "Bheem cannot read its saved data on this phone (its key is gone, for example after moving to a new phone). " +
                                "Erasing starts fresh. Your notes, tasks and history here will be lost.",
                            "Erase and start again",
                        ) {
                            scope.launch {
                                app.eraseEverything()
                                attempt++
                            }
                        }
                        is Startup.Ready -> TodayScreen(state.controller, exactAllowed) {
                            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notice(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (action != null) Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
    }
}

@Composable
private fun TodayScreen(controller: AppController, exactAllowed: Boolean, onAllowExact: () -> Unit) {
    val feed by controller.feed.collectAsState()
    val message by controller.message.collectAsState()
    val busy by controller.busy.collectAsState()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Text("Today", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        if (!exactAllowed) ExactAlarmBanner(onAllowExact)
        message?.let { MessageBanner(it, controller::clearMessage) }
        if (feed.isEmpty()) {
            EmptyState(Modifier.weight(1f))
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(feed, key = { it.id }) { CardFor(it, controller) }
            }
        }
        Bar(busy, controller::submit)
    }
}

@Composable
private fun ExactAlarmBanner(onAllow: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Reminders may arrive up to an hour late until you allow exact times.", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onAllow) { Text("Allow exact reminders") }
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
            Text(item.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(4.dp))
            Text(item.detail, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            when (item.status) {
                FeedStatus.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.approve(item.id) }) { Text(if (item.level == RiskLevel.L2) "Confirm" else "OK") }
                    TextButton(onClick = { controller.dismiss(item.id) }) { Text("Dismiss") }
                }
                FeedStatus.DONE -> Row {
                    Text("Done", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 12.dp))
                    if (item.canUndo) TextButton(onClick = { controller.undo(item.id) }) { Text("Undo") }
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
private fun Bar(busy: Boolean, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(if (busy) "Thinking..." else "Tell Bheem what you need") },
            enabled = !busy,
            singleLine = true,
        )
        Button(
            onClick = {
                onSend(text)
                text = ""
            },
            enabled = !busy && text.isNotBlank(),
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Send") }
    }
}
