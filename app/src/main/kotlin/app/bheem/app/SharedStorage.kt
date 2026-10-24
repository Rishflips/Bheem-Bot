package app.bheem.app

import android.content.Context
import app.bheem.storage.Storage

object SharedStorage {
    private var storage: Storage? = null

    @Synchronized
    fun get(context: Context): Storage = storage ?: Storage.open(context.applicationContext).also { storage = it }

    @Synchronized
    fun close() {
        storage?.close()
        storage = null
    }
}
