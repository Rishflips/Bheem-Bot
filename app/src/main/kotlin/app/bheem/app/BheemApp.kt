package app.bheem.app

import android.app.Application
import app.bheem.storage.LegacyDatabase

class BheemApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        LegacyDatabase.delete(this)
        graph = AppGraph(this)
    }
}
