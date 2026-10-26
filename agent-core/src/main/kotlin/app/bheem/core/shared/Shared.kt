package app.bheem.core.shared

class Shared<T : Any>(private val open: () -> T, private val close: (T) -> Unit) {
    private var value: T? = null

    @Synchronized
    fun get(): T = value ?: open().also { value = it }

    @Synchronized
    fun close() {
        value?.let(close)
        value = null
    }

    @Synchronized
    fun <R> replacing(block: () -> R): R {
        close()
        return block()
    }
}
