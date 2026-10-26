package app.bheem.core.speech

import app.bheem.models.speech.Utterance
import app.bheem.models.speech.Utterances
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface VoiceState {
    data object Idle : VoiceState

    data class Listening(val level: Float) : VoiceState

    data object Thinking : VoiceState
}

interface Recorder {
    /** Starts recording speech into memory. Returns false when the microphone cannot be opened. */
    fun start(onLevel: (Float) -> Unit, onFull: () -> Unit): Boolean

    fun stop(): ShortArray

    fun cancel()
}

/** The hold-to-talk state machine. [busy] is shared with typed commands so only one thing uses the model at a time. */
class TalkSession(
    private val recorder: Recorder,
    private val voice: VoiceCommands,
    private val scope: CoroutineScope,
    private val busy: MutableStateFlow<Boolean>,
    private val clock: () -> LocalDateTime,
    private val say: (String?) -> Unit,
    private val describe: (VoiceResult) -> String?,
    private val elapsedMillis: () -> Long,
    private val onFinished: (millisSinceRelease: Long, audioMillis: Long) -> Unit,
) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)

    val state: StateFlow<VoiceState> = _state

    fun start() {
        if (busy.value || !_state.compareAndSet(VoiceState.Idle, VoiceState.Listening(0f))) return
        say(null)
        val started = recorder.start(
            onLevel = { level -> setLevel(level) },
            onFull = { scope.launch { stop() } },
        )
        if (!started) {
            _state.value = VoiceState.Idle
            say("The microphone is not available right now.")
        }
    }

    fun stop() {
        if (!takeListening()) return
        val samples = recorder.stop()
        val released = elapsedMillis()
        busy.value = true
        scope.launch {
            try {
                val audioMillis = samples.size * 1000L / SAMPLE_RATE
                say(
                    when (val utterance = Utterances.of(samples)) {
                        Utterance.TooShort -> "Hold the button while you speak."
                        Utterance.Silent -> "I could not hear anything. Hold the phone closer and try again."
                        is Utterance.Captured -> describe(voice.handle(utterance.wav, clock())).also { onFinished(elapsedMillis() - released, audioMillis) }
                    },
                )
            } finally {
                busy.value = false
                _state.value = VoiceState.Idle
            }
        }
    }

    fun cancel() {
        if (!takeListening()) return
        recorder.cancel()
        _state.value = VoiceState.Idle
    }

    private fun takeListening(): Boolean {
        while (true) {
            val current = _state.value
            if (current !is VoiceState.Listening) return false
            if (_state.compareAndSet(current, VoiceState.Thinking)) return true
        }
    }

    private fun setLevel(level: Float) {
        while (true) {
            val current = _state.value
            if (current !is VoiceState.Listening || _state.compareAndSet(current, VoiceState.Listening(level))) return
        }
    }

    private companion object {
        const val SAMPLE_RATE = 16_000
    }
}
