package app.bheem.eval.speech

class StreamLines {
    private val streams = mutableMapOf<Int, LinkedHashMap<Long, String>>()

    fun update(stream: Int, line: Long, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        streams.getOrPut(stream) { linkedMapOf() }[line] = trimmed
    }

    fun take(stream: Int): String = streams.remove(stream)?.values?.joinToString(" ").orEmpty()
}
