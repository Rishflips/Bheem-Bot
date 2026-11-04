package app.bheem.app

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import app.bheem.core.assistant.SpeechPolicy
import app.bheem.core.assistant.VoiceCandidate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Speaks text with the phone's own text to speech engine. The text goes to that engine app (the phone's default, named in Settings), which is outside Bheem.
 * Only a voice that is installed and works without the network is used, and it is checked again before every utterance: if the engine swaps it for another,
 * Bheem stays silent instead of speaking through a network voice. The text spoken is only what is already on the screen.
 */
class Speaker(
    context: Context,
    private val onSpeaking: (Boolean) -> Unit,
    private val onAvailable: (Boolean) -> Unit,
    private val onEngine: (String?) -> Unit,
) {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private var engine: TextToSpeech? = null
    private val pending = AtomicReference<String?>(null)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focus: AudioFocusRequest? = null

    @Volatile
    private var ready = false

    @Volatile
    private var available = true

    @Volatile
    private var currentId: String? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) onReady() else markUnavailable()
        }
    }

    private fun candidate(voice: Voice) = VoiceCandidate(voice.name, voice.locale.language, voice.locale.country, voice.isNetworkConnectionRequired, voice.features.orEmpty())

    private fun onReady() {
        val tts = engine ?: return
        onEngine(tts.defaultEngine)
        val voices = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
        val chosen = SpeechPolicy.pick(voices.map(::candidate))?.let { pick -> voices.firstOrNull { it.name == pick.name } }
        if (chosen == null || tts.setVoice(chosen) != TextToSpeech.SUCCESS || !voiceIsStillUsable(tts)) {
            markUnavailable()
            return
        }
        tts.setAudioAttributes(attributes)
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    if (utteranceId == currentId) onSpeaking(true)
                }

                override fun onDone(utteranceId: String?) = finished(utteranceId)

                @Deprecated("Required by the abstract class")
                override fun onError(utteranceId: String?) = finished(utteranceId)

                override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
            },
        )
        ready = true
        pending.getAndSet(null)?.let { speakNow(it) }
    }

    private fun finished(utteranceId: String?) {
        if (utteranceId != currentId) return
        currentId = null
        onSpeaking(false)
        release()
    }

    private fun voiceIsStillUsable(tts: TextToSpeech): Boolean = tts.voice?.let { SpeechPolicy.usable(candidate(it)) } == true

    private fun markUnavailable() {
        available = false
        onAvailable(false)
        Log.w("BheemSpeak", "no installed voice, staying silent")
    }

    fun speak(text: String) {
        if (!available) return
        if (!ready) {
            pending.set(text)
            if (ready) pending.getAndSet(null)?.let { speakNow(it) }
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val tts = engine ?: return
        if (!voiceIsStillUsable(tts)) {
            markUnavailable()
            return
        }
        if (!take()) return
        val id = UUID.randomUUID().toString()
        currentId = id
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            Log.w("BheemSpeak", "the speech engine refused the text")
            finished(id)
        }
    }

    /** Asks other audio to duck while Bheem speaks; refused, for example during a call, means Bheem stays quiet. */
    private fun take(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attributes).build()
        focus = request
        return audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun release() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    fun stop() {
        pending.set(null)
        currentId = null
        engine?.stop()
        onSpeaking(false)
        release()
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
    }
}
