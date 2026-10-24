package app.bheem.eval.speech

import app.bheem.models.Wav
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

data class RecordingCheckResult(
    val takes: List<Take>,
    val ready: List<String>,
    val missing: List<String>,
    val bad: Map<String, String>,
    val unknown: List<String>,
    val audioMs: Long,
) {
    val complete: Boolean get() = missing.isEmpty() && bad.isEmpty()
}

object RecordingCheck {
    private const val LISTED = 10

    fun check(takes: List<Take>, audioDir: Path): RecordingCheckResult {
        val files = if (Files.isDirectory(audioDir)) Files.list(audioDir).use { list -> list.map { it.fileName.toString() }.toList() } else emptyList()
        val ready = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val bad = linkedMapOf<String, String>()
        var audioMs = 0L
        for (take in takes) {
            val path = audioDir.resolve("${take.id}.wav")
            if (!Files.exists(path)) {
                missing += take.id
                continue
            }
            try {
                audioMs += Wav.read(Files.readAllBytes(path)).requireSpeechFormat().durationMs
                ready += take.id
            } catch (e: IllegalArgumentException) {
                bad[take.id] = e.message.orEmpty()
            }
        }
        val wanted = takes.map { "${it.id}.wav" }.toSet()
        val unknown = files.filter { it.endsWith(".wav", ignoreCase = true) && it !in wanted }.sorted()
        return RecordingCheckResult(takes, ready, missing, bad, unknown, audioMs)
    }

    fun report(result: RecordingCheckResult): String = buildString {
        val total = result.takes.size
        if (result.complete) {
            appendLine("All $total takes are ready.")
        } else {
            appendLine("${result.ready.size} of $total takes ready, ${result.missing.size} missing, ${result.bad.size} bad, ${result.unknown.size} unknown files.")
        }
        appendLine("Audio: ${String.format(Locale.ROOT, "%.1f", result.audioMs / 60_000.0)} minutes in total.")
        val ready = result.ready.toSet()
        appendLine("Per speaker: " + result.takes.groupBy { it.speaker }.entries.joinToString(", ") { (speaker, takes) -> "$speaker ${takes.count { it.id in ready }}/${takes.size}" })
        if (result.missing.isNotEmpty()) appendLine("Missing (first $LISTED): " + result.missing.take(LISTED).joinToString(", "))
        for ((id, reason) in result.bad.entries.take(LISTED)) appendLine("Bad: $id: $reason")
        if (result.unknown.isNotEmpty()) appendLine("Unknown files (not in the manifest): " + result.unknown.take(LISTED).joinToString(", "))
    }
}
