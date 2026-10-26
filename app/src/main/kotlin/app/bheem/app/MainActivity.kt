package app.bheem.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bheem.core.feed.FeedItem
import app.bheem.core.reminder.ReminderHealth
import app.bheem.core.reminder.ReminderIssue

class MainActivity : ComponentActivity() {
    private var issues by mutableStateOf<List<ReminderIssue>>(emptyList())
    private val preferences by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }
    private var afterNotificationPrompt: (() -> Unit)? = null

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
        readyController()?.refreshSupply()
        readyController()?.refreshFeed()
        readyController()?.reminderServices?.let { services ->
            Thread { runCatching { services.acknowledgeMissed() } }.start()
        }
    }

    private val microphone = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) readyController()?.say(getString(R.string.mic_denied))
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        issues = reminderIssues()
        afterNotificationPrompt?.invoke()
        afterNotificationPrompt = null
    }

    private fun readyController(): AppController? = ((application as BheemApp).session.value as? Session.Ready)?.controller

    private fun startTalking() {
        val controller = readyController() ?: return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) controller.startListening() else microphone.launch(Manifest.permission.RECORD_AUDIO)
    }

    /** The notification permission is asked when the first reminder is approved, not at launch, so the person has seen what it is for. */
    private fun approve(item: FeedItem) {
        val controller = readyController() ?: return
        val isReminder = item.dueAt != null
        val needsAsk = isReminder && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            !preferences.getBoolean(NOTIFICATION_ASKED, false)
        if (needsAsk) {
            preferences.edit().putBoolean(NOTIFICATION_ASKED, true).apply()
            afterNotificationPrompt = { controller.approve(item.id) }
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            controller.approve(item.id)
        }
    }

    override fun onPause() {
        readyController()?.cancelListening()
        super.onPause()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val dark = isSystemInDarkTheme()
            val context = LocalContext.current
            val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = scheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val app = application as BheemApp
                    val session by app.session.collectAsState()
                    when (val state = session) {
                        Session.Starting -> Notice(stringResource(R.string.starting))
                        Session.Erasing -> Notice(stringResource(R.string.erasing))
                        is Session.Failed -> Notice(stringResource(R.string.start_failed, state.message))
                        Session.NeedsErase -> Notice(stringResource(R.string.needs_erase), stringResource(R.string.needs_erase_action)) { app.eraseEverything() }
                        is Session.Ready -> Main(state, app)
                    }
                }
            }
        }
    }

    @Composable
    private fun Main(state: Session.Ready, app: BheemApp) {
        var onboarded by remember(state) { mutableStateOf(preferences.getBoolean(ONBOARDED, false)) }
        var screen by rememberSaveable { mutableStateOf(Screen.TODAY) }
        if (!onboarded) {
            Onboarding {
                preferences.edit().putBoolean(ONBOARDED, true).apply()
                onboarded = true
            }
            return
        }
        BackHandler(enabled = screen != Screen.TODAY) { screen = if (screen == Screen.LICENCES || screen == Screen.LEDGER) Screen.SETTINGS else Screen.TODAY }
        when (screen) {
            Screen.TODAY -> TodayScreen(state.controller, issues, ::fix, ::dismissBatteryHint, ::startTalking, ::approve) { screen = Screen.SETTINGS }
            Screen.SETTINGS -> SettingsScreen(
                state.controller,
                onBack = { screen = Screen.TODAY },
                onOpenLedger = { screen = Screen.LEDGER },
                onOpenLicences = { screen = Screen.LICENCES },
                onErase = { removeModel ->
                    screen = Screen.TODAY
                    app.eraseEverything(removeModel)
                },
            )
            Screen.LEDGER -> LedgerScreen(state.controller) { screen = Screen.SETTINGS }
            Screen.LICENCES -> LicencesScreen { screen = Screen.SETTINGS }
        }
    }

    companion object {
        const val PREFERENCES = "bheem"
        private const val BATTERY_HINT_SHOWN = "battery_hint_shown"
        private const val NOTIFICATION_ASKED = "notification_permission_asked"
        private const val ONBOARDED = "onboarded"
    }
}

private enum class Screen { TODAY, SETTINGS, LEDGER, LICENCES }

@Composable
private fun Notice(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
        if (action != null) Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
    }
}
