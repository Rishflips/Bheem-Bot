package app.bheem.models.speech

/** Words that help a speech model: the commands people say, and names from the user's own records. */
data class Hints(val commandWords: List<String> = emptyList(), val names: List<String> = emptyList())

data class Transcript(val text: String, val raw: String, val firstChunkMs: Long, val totalMs: Long)

interface SpeechToText {
    /** [wav] is a 16 kHz mono 16-bit WAV held in memory; implementations must not write it to disk. */
    suspend fun transcribe(wav: ByteArray, hints: Hints): Transcript
}
