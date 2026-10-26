package app.bheem.app

import android.app.Application
import android.app.NotificationManager
import java.io.File
import app.bheem.storage.AppDataEraser
import app.bheem.storage.KeyLostException
import app.bheem.storage.LegacyDatabase
import app.bheem.storage.Storage
import app.bheem.core.reminder.RearmReason
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface Session {
    data object Starting : Session

    data class Ready(val controller: AppController) : Session

    data object NeedsErase : Session

    data object Erasing : Session

    data class Failed(val message: String) : Session
}

class BheemApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycle = Mutex()
    private val _session = MutableStateFlow<Session>(Session.Starting)

    val session: StateFlow<Session> = _session

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        scope.launch { lifecycle.withLock { open() } }
    }

    fun eraseEverything(removeModel: Boolean = false) {
        scope.launch {
            lifecycle.withLock {
                if (_session.value == Session.Erasing) return@withLock
                (_session.value as? Session.Ready)?.controller?.close()
                _session.value = Session.Erasing
                runCatching { ReminderServices.lightweight(this@BheemApp).scheduler.cancelAll() }
                var stuck = 0
                SharedStorage.erase(this@BheemApp) {
                    Storage.erase(this@BheemApp)
                    stuck = wipeFiles(removeModel)
                }
                if (stuck > 0) {
                    _session.value = Session.Failed("$stuck files could not be deleted. Restart the phone and erase again.")
                    return@withLock
                }
                open()
            }
        }
    }

    private fun wipeFiles(removeModel: Boolean): Int {
        getSystemService(NotificationManager::class.java).cancelAll()
        getSharedPreferences(MainActivity.PREFERENCES, MODE_PRIVATE).edit().clear().commit()
        val models = File(filesDir, "models")
        val external = getExternalFilesDir(null)
        val sideload = external?.let { File(it, "models") }
        val roots = listOfNotNull(filesDir, noBackupFilesDir, codeCacheDir, File(dataDir, "shared_prefs"), File(dataDir, "databases"), external)
        return AppDataEraser(roots).erase(keep = { !removeModel && (it == models || it == sideload) }).size
    }

    private fun open() {
        _session.value = Session.Starting
        LegacyDatabase.delete(this)
        _session.value = runCatching { AppGraph(this) }.fold(
            { graph ->
                graph.coordinator.sweepInterrupted(LocalDateTime.now())
                runCatching { graph.reminderServices.rearm(RearmReason.Other) }
                Session.Ready(AppController(graph))
            },
            { error ->
                if (error is KeyLostException || error.message.orEmpty().contains("not a database", ignoreCase = true)) {
                    Session.NeedsErase
                } else {
                    Session.Failed(error.message ?: "unknown error")
                }
            },
        )
    }
}
