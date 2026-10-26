package app.bheem.app

import android.app.Application
import android.app.NotificationManager
import app.bheem.core.reminder.RearmReason
import app.bheem.storage.AppDataEraser
import app.bheem.storage.KeyLostException
import app.bheem.storage.LegacyDatabase
import app.bheem.storage.Storage
import java.io.File
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

    data class EraseFailed(val removeModel: Boolean) : Session

    data class Failed(val message: String) : Session
}

class BheemApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lifecycle = Mutex()
    private val _session = MutableStateFlow<Session>(Session.Starting)
    private val eraseMarker by lazy { File(noBackupFilesDir, ERASE_MARKER) }

    @Volatile
    private var erasing = false

    val session: StateFlow<Session> = _session

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        if (eraseMarker.exists()) {
            erasing = true
            _session.value = Session.Erasing
            scope.launch { lifecycle.withLock { erase(null, eraseMarker.readText().trim() == "true") } }
        } else {
            scope.launch { lifecycle.withLock { open() } }
        }
    }

    /** Shows the erasing screen at once. The marker file makes a half-finished erase resume at the next launch. */
    fun eraseEverything(removeModel: Boolean = false) {
        if (erasing) return
        erasing = true
        val previous = _session.value
        _session.value = Session.Erasing
        scope.launch { lifecycle.withLock { erase(previous, removeModel) } }
    }

    private suspend fun erase(previous: Session?, removeModel: Boolean) {
        try {
            eraseMarker.parentFile?.mkdirs()
            eraseMarker.writeText(removeModel.toString())
            (previous as? Session.Ready)?.controller?.close()
            runCatching { ReminderServices.lightweight(this@BheemApp).scheduler.cancelAll() }
            var stuck = 0
            SharedStorage.erase(this@BheemApp) {
                Storage.erase(this@BheemApp)
                stuck = wipeFiles(removeModel)
            }
            if (stuck > 0) {
                _session.value = Session.EraseFailed(removeModel)
                return
            }
            eraseMarker.delete()
            open()
        } catch (e: Exception) {
            _session.value = Session.EraseFailed(removeModel)
        } finally {
            erasing = false
        }
    }

    private fun wipeFiles(removeModel: Boolean): Int {
        getSystemService(NotificationManager::class.java).cancelAll()
        getSharedPreferences(MainActivity.PREFERENCES, MODE_PRIVATE).edit().clear().commit()
        val models = File(filesDir, "models")
        val external = getExternalFilesDir(null)
        val sideload = external?.let { File(it, "models") }
        val roots = listOfNotNull(
            filesDir, noBackupFilesDir, cacheDir, codeCacheDir, externalCacheDir, File(dataDir, "shared_prefs"), File(dataDir, "databases"), external,
        )
        return AppDataEraser(roots).erase(keep = { it == eraseMarker || (!removeModel && (it == models || it == sideload)) }).size
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

    private companion object {
        const val ERASE_MARKER = "erase-pending"
    }
}
