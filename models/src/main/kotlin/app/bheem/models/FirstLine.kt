package app.bheem.models

/** Collects streamed text up to the end of the first non-blank line, so an engine can stop decoding there. */
class FirstLine {
    private val collected = StringBuilder()
    private var done = false

    val text: String get() = collected.toString().trim()

    /** Returns true once the first non-blank line is complete. Text after its newline is dropped. */
    fun accept(chunk: String): Boolean {
        if (done) return true
        for (char in chunk) {
            if (char == '\n') {
                if (collected.isNotBlank()) {
                    done = true
                    return true
                }
                continue
            }
            collected.append(char)
        }
        return false
    }
}
