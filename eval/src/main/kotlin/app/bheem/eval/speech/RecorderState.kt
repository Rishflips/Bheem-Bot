package app.bheem.eval.speech

data class RecorderState(val takes: List<Take>, val done: Set<String>, val index: Int) {
    val total: Int get() = takes.size

    val doneCount: Int get() = takes.count { it.id in done }

    val finished: Boolean get() = doneCount == total

    val current: Take? get() = takes.getOrNull(index)

    val startsBlock: Boolean get() = startsBlockAt(index)

    fun blockLabel(): String {
        val take = current ?: return ""
        val number = (0..index).count { startsBlockAt(it) }
        val count = takes.indices.count { startsBlockAt(it) }
        return "Block $number of $count: ${take.condition.name.lowercase()}"
    }

    fun markDone(id: String): RecorderState {
        val nowDone = done + id
        val from = takes.indexOfFirst { it.id == id }
        val next = (from + 1 until total).firstOrNull { takes[it].id !in nowDone }
            ?: takes.indices.firstOrNull { takes[it].id !in nowDone }
        return copy(done = nowDone, index = next ?: index)
    }

    fun move(delta: Int): RecorderState = copy(index = (index + delta).coerceIn(0, maxOf(0, total - 1)))

    private fun startsBlockAt(at: Int) = at in takes.indices && (at == 0 || takes[at - 1].condition != takes[at].condition)

    companion object {
        fun start(takes: List<Take>, done: Set<String>): RecorderState =
            RecorderState(takes, done, takes.indexOfFirst { it.id !in done }.coerceAtLeast(0))

        fun speakers(takes: List<Take>): List<String> = takes.map { it.speaker }.distinct().sorted()

        fun forSpeaker(all: List<Take>, speaker: String, done: Set<String>): RecorderState =
            start(all.filter { it.speaker == speaker }.sortedBy { it.position }, done)
    }
}
