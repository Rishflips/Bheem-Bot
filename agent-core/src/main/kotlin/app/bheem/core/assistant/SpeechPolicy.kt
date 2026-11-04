package app.bheem.core.assistant

enum class SpeakMode { OFF, VOICE, ALWAYS }

/** What is going on around the phone that decides whether Bheem may speak on its own. */
data class SpeechSituation(val inCall: Boolean, val screenReaderOn: Boolean, val locked: Boolean)

data class VoiceCandidate(val name: String, val language: String, val country: String, val needsNetwork: Boolean, val features: Set<String>)

/** When Bheem speaks on its own and which voice it may use. A voice that needs the network or is not installed is never used. */
object SpeechPolicy {
    fun shouldSpeak(mode: SpeakMode, lastInputSpoken: Boolean, turn: Turn, situation: SpeechSituation): Boolean {
        if (turn !is Turn.Answer && turn !is Turn.Chat && turn !is Turn.Notice) return false
        if (situation.inCall || situation.screenReaderOn || situation.locked) return false
        return when (mode) {
            SpeakMode.OFF -> false
            SpeakMode.VOICE -> lastInputSpoken
            SpeakMode.ALWAYS -> true
        }
    }

    fun usable(voice: VoiceCandidate): Boolean = voice.language == "en" && !voice.needsNetwork && "notInstalled" !in voice.features

    fun pick(voices: List<VoiceCandidate>): VoiceCandidate? = voices.filter(::usable).let { found -> found.firstOrNull { it.country == "IN" } ?: found.firstOrNull() }
}
