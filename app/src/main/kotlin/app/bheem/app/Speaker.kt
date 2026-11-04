package app.bheem.app

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SpeakMode { OFF, VOICE, ALWAYS }

/**
 * Speaks text with the phone's own text to speech engine. Only a voice that is installed on the phone is used: if the only voices need the network, Bheem
 * stays silent instead of sending text to a server. The text spoken is only what is already on the screen.
 */
class Speaker(context: Context) {
    private var engine: TextToSpeech? = null

    @Volatile
    private var ready = false

    @Volatile
    private var pending: String? = null

    private val _available = MutableStateFlow(true)
    private val _speaking = MutableStateFlow(false)

    /** False once the engine started and no voice that works without the network is installed. */
    val available: StateFlow<Boolean> = _available
    val speaking: StateFlow<Boolean> = _speaking

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) onReady() else markUnavailable()
        }
    }

    private fun onReady() {
        val tts = engine ?: return
        val installed = runCatching { tts.voices.orEmpty() }.getOrDefault(emptySet())
            .filter { it.locale.language == "en" && !it.isNetworkConnectionRequired && !it.features.contains("notInstalled") }
        val voice = installed.firstOrNull { it.locale.country == "IN" } ?: installed.firstOrNull()
        if (voice == null) {
            markUnavailable()
            return
        }
        tts.voice = voice
        tts.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _speaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _speaking.value = false
                }

                @Deprecated("Required by the abstract class")
                override fun onError(utteranceId: String?) {
                    _speaking.value = false
                }
            },
        )
        ready = true
        pending?.let { speak(it) }
        pending = null
    }

    private fun markUnavailable() {
        _available.value = false
        Log.w("BheemSpeak", "no installed voice, staying silent")
    }

    fun speak(text: String) {
        if (!_available.value) return
        val tts = engine
        if (!ready || tts == null) {
            pending = text
            return
        }
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) != TextToSpeech.SUCCESS) Log.w("BheemSpeak", "the speech engine refused the text")
    }

    fun stop() {
        pending = null
        engine?.stop()
        _speaking.value = false
    }

    fun shutdown() {
        stop()
        engine?.shutdown()
        engine = null
    }
}
