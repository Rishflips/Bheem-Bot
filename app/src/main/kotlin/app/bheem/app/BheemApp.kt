package app.bheem.app

import android.app.Application
import app.bheem.storage.KeyLostException
import app.bheem.storage.LegacyDatabase
import app.bheem.storage.Storage
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

    fun eraseEverything() {
        scope.launch {
            lifecycle.withLock {
                if (_session.value == Session.Erasing) return@withLock
                (_session.value as? Session.Ready)?.controller?.close()
                _session.value = Session.Erasing
                Storage.erase(this@BheemApp)
                open()
            }
        }
    }

    private fun open() {
        _session.value = Session.Starting
        LegacyDatabase.delete(this)
        _session.value = runCatching { AppGraph(this) }.fold(
            { graph ->
                graph.coordinator.sweepInterrupted(LocalDateTime.now())
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
