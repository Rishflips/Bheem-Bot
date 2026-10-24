package app.bheem.app

import android.app.Application
import app.bheem.storage.LegacyDatabase
import app.bheem.storage.Storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class BheemApp : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var graph: Deferred<Result<AppGraph>>? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        graph = start()
    }

    suspend fun awaitGraph(): Result<AppGraph> = (graph ?: start().also { graph = it }).await()

    suspend fun eraseEverything() {
        withContext(Dispatchers.Default) {
            graph?.await()?.getOrNull()?.storage?.close()
            Storage.erase(this@BheemApp)
            graph = start()
        }
    }

    private fun start(): Deferred<Result<AppGraph>> = scope.async {
        LegacyDatabase.delete(this@BheemApp)
        runCatching { AppGraph(this@BheemApp) }
    }
}
