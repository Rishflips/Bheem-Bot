package app.bheem.app

import android.app.Application

class BheemApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
        graph = AppGraph(this)
    }
}
