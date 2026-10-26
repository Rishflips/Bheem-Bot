package app.bheem.app

import android.content.Context
import app.bheem.core.shared.Shared
import app.bheem.storage.Storage

object SharedStorage {
    private var holder: Shared<Storage>? = null

    @Synchronized
    private fun holder(context: Context): Shared<Storage> {
        val app = context.applicationContext
        return holder ?: Shared({ Storage.open(app) }, { it.close() }).also { holder = it }
    }

    fun get(context: Context): Storage = holder(context).get()

    fun erase(context: Context, erase: () -> Unit) {
        holder(context).replacing(erase)
    }
}
