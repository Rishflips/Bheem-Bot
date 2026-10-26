package app.bheem.core.speech

import app.bheem.core.feed.FeedRow
import app.bheem.core.gate.Provenance
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.Outcome
import app.bheem.models.AudioClip
import app.bheem.models.AudioForm
import app.bheem.models.GenerationRequest
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelUnavailable
import app.bheem.models.speech.Hints
import app.bheem.models.speech.SpeechToText
import app.bheem.models.speech.Transcript
import java.time.LocalDateTime
import kotlinx.coroutines.CancellationException

class GemmaAudioSpeech(private val host: ModelHost) : SpeechToText {
    override suspend fun transcribe(wav: ByteArray, hints: Hints): Transcript {
        val engine = host.acquire()
        try {
            val prompt = SpeechPrompts.forHints(hints.copy(names = hints.names.take(VoiceHints.MAX_NAMES)))
            val completion = engine.complete(
                GenerationRequest(prompt.system, prompt.user, maxOutputTokens = MAX_OUTPUT_TOKENS, temperature = 0.0, audio = AudioClip("", AudioForm.WAV_BYTES, beforeText = true, bytes = wav)),
            )
            return Transcript(TranscriptCleaner.clean(completion.text), completion.text, completion.firstChunkMs, completion.totalMs)
        } finally {
            host.release()
        }
    }

    private companion object {
        const val MAX_OUTPUT_TOKENS = 128
    }
}

/** Builds speech hints from the user's own records only. Never pass text that came from a notification or another person. */
object VoiceHints {
    const val MAX_NAMES = 60

    private val notNames = setOf(
        "i", "the", "a", "an", "and", "or", "to", "of", "in", "on", "at", "for", "my", "me", "it", "is", "do", "don't", "remember", "remind", "reminder", "call", "buy", "pay",
        "send", "note", "add", "save", "schedule", "message", "tell", "book", "put", "get", "take", "check", "renew", "file", "order", "clean", "water", "pick", "reply", "make",
        "update", "set", "today", "tomorrow", "tonight", "next", "this", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december",
    )
    private val word = Regex("""\p{L}[\p{L}'’-]*""")

    /** Only records and recipients that came from proposals with user provenance can become hint names. */
    fun fromUserRows(records: List<MemoryRecord>, rows: List<FeedRow>): Hints {
        val user = rows.map { it.proposal }.filter { it.provenance == Provenance.USER }
        val ids = user.map { it.id }.toSet()
        val recipients = user.filter { it.action.action == "DraftMessage" }.mapNotNull { it.action.args["to"] }
        return build(records.filter { it.id in ids }, recipients)
    }

    fun build(records: List<MemoryRecord>, recipients: List<String>): Hints {
        val names = LinkedHashMap<String, String>()
        fun add(name: String) {
            val shown = name.trim().replaceFirstChar { it.uppercase() }
            if (shown.length >= 2 && shown.lowercase() !in notNames) names.putIfAbsent(shown.lowercase(), shown)
        }
        recipients.forEach { add(it) }
        for (record in records) {
            for (m in word.findAll(record.text)) {
                val token = m.value
                if (token.first().isUpperCase()) add(token)
            }
        }
        return Hints(SpeechPrompts.commandWords, names.values.take(MAX_NAMES))
    }
}

sealed interface VoiceResult {
    data class Heard(val transcript: String, val outcome: Outcome) : VoiceResult

    data object NothingHeard : VoiceResult

    data class Unavailable(val reason: String) : VoiceResult
}

class VoiceCommands(
    private val speech: SpeechToText,
    private val pipeline: CommandPipeline,
    private val hints: () -> Hints,
) {
    suspend fun handle(wav: ByteArray, now: LocalDateTime): VoiceResult {
        val hint = hints()
        val transcript = try {
            speech.transcribe(wav, hint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ModelUnavailable) {
            return VoiceResult.Unavailable(e.message.orEmpty())
        } catch (e: Exception) {
            return VoiceResult.Unavailable("I could not understand the audio. Try again.")
        }
        val text = transcript.text.trim()
        if (text.none { it.isLetterOrDigit() } || repeatsHintList(text, hint)) return VoiceResult.NothingHeard
        return VoiceResult.Heard(text, pipeline.handle(text, now, source = "voice"))
    }

    /** On unclear audio the model reads the hint list back: at least two command words and almost nothing that is not on the list. */
    private fun repeatsHintList(text: String, hint: Hints): Boolean {
        if (text.contains(ECHO_LEAD_IN, ignoreCase = true)) return true
        val commandWords = hint.commandWords.count { Regex("""\b${Regex.escape(it)}\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) }
        if (commandWords < 2) return false
        val listed = (hint.commandWords + hint.names).flatMap { tokens(it) }.toSet()
        val spoken = tokens(text)
        return spoken.isNotEmpty() && spoken.count { it !in listed } <= spoken.size * ECHO_OTHER_SHARE
    }

    private fun tokens(text: String): List<String> = Regex("""[\p{L}\p{N}'’]+""").findAll(text).map { it.value.lowercase() }.toList()

    private companion object {
        const val ECHO_LEAD_IN = "Commands often start with"
        const val ECHO_OTHER_SHARE = 0.2
    }
}
