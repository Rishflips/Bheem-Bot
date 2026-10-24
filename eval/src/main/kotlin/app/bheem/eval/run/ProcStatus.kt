package app.bheem.eval.run

object ProcStatus {
    private val number = Regex("""(\d+)""")

    fun kb(status: String, key: String): Long? =
        status.lineSequence()
            .firstOrNull { it.startsWith("$key:") }
            ?.let { number.find(it)?.value?.toLong() }
}
