package app.bheem.core.gate

import java.time.LocalDateTime

class SourceRateLimiter(private val maxPerWindow: Int, private val windowSeconds: Long) {
    private val seen = mutableMapOf<String, ArrayDeque<LocalDateTime>>()

    @Synchronized
    fun tryAcquire(source: String, at: LocalDateTime): Boolean {
        val times = seen.getOrPut(source) { ArrayDeque() }
        val cutoff = at.minusSeconds(windowSeconds)
        while (times.isNotEmpty() && !times.first().isAfter(cutoff)) times.removeFirst()
        if (times.size >= maxPerWindow) return false
        times.addLast(at)
        return true
    }
}
