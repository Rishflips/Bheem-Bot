package app.bheem.core.speech

import app.bheem.core.gate.Gate
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.models.speech.Hints
import app.bheem.models.speech.SpeechToText
import app.bheem.models.speech.Transcript
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

class TalkSessionTest {
    private val now = LocalDateTime.of(2026, 10, 27, 9, 0)

    private class FakeRecorder(var samples: ShortArray = ShortArray(16_000) { 1_000 }, var canStart: Boolean = true) : Recorder {
        var starts = 0
        var stops = 0
        var cancels = 0
        var onLevel: (Float) -> Unit = {}
        var onFull: () -> Unit = {}

        override fun start(onLevel: (Float) -> Unit, onFull: () -> Unit): Boolean {
            starts++
            this.onLevel = onLevel
            this.onFull = onFull
            return canStart
        }

        override fun stop(): ShortArray {
            stops++
            return samples
        }

        override fun cancel() {
            cancels++
        }
    }

    private class FakeStt(var text: String = "remind me to call Arun at 5 PM") : SpeechToText {
        var calls = 0

        override suspend fun transcribe(wav: ByteArray, hints: Hints): Transcript {
            calls++
            return Transcript(text, text, 1, 1)
        }
    }

    private val recorder = FakeRecorder()
    private val stt = FakeStt()
    private val busy = MutableStateFlow(false)
    private val messages = mutableListOf<String?>()
    private val finished = mutableListOf<Pair<Long, Long>>()
    private var counter = 0

    private val session = TalkSession(
        recorder = recorder,
        voice = VoiceCommands(stt, CommandPipeline({ _, _ -> "CreateReminder|call Arun|2026-10-28T17:00" }, Gate(), Ledger(InMemoryLedgerStore())) { "p${counter++}" }, { Hints() }),
        scope = CoroutineScope(Job() + Dispatchers.Unconfined),
        busy = busy,
        clock = { now },
        say = { messages += it },
        describe = { result -> if (result is VoiceResult.Heard) "ok" else "not heard" },
        elapsedMillis = { 0L },
        onFinished = { ms, audioMs -> finished += ms to audioMs },
    )

    @Test
    fun pressingTwiceStartsTheRecorderOnce() {
        session.start()
        session.start()
        assertEquals(1, recorder.starts)
        assertIs<VoiceState.Listening>(session.state.value)
    }

    @Test
    fun aBusyModelRefusesToStartListening() {
        busy.value = true
        session.start()
        assertEquals(0, recorder.starts)
        assertEquals(VoiceState.Idle, session.state.value)
    }

    @Test
    fun aMicrophoneThatCannotStartIsReportedAndLeavesTheSessionIdle() {
        recorder.canStart = false
        session.start()
        assertEquals(VoiceState.Idle, session.state.value)
        assertEquals(listOf<String?>(null, "The microphone is not available right now."), messages)
    }

    @Test
    fun leavingTheScreenWhileListeningCancelsTheRecordingAndHearsNothing() {
        session.start()
        session.cancel()
        assertEquals(1, recorder.cancels)
        assertEquals(0, stt.calls)
        assertEquals(VoiceState.Idle, session.state.value)
        session.stop()
        assertEquals(0, recorder.stops)
        assertEquals(0, stt.calls)
    }

    @Test
    fun cancelOutsideListeningDoesNothing() {
        session.cancel()
        assertEquals(0, recorder.cancels)
    }

    @Test
    fun releaseAndTheTenSecondCapAtTheSameTimeTranscribeOnce() {
        session.start()
        recorder.onFull()
        session.stop()
        recorder.onFull()
        assertEquals(1, stt.calls)
        assertEquals(1, recorder.stops)
        assertEquals(VoiceState.Idle, session.state.value)
        assertFalse(busy.value)
    }

    @Test
    fun aSpokenCommandIsHeardOnceAndTheSessionGoesBackToIdle() {
        session.start()
        recorder.onLevel(0.4f)
        assertEquals(VoiceState.Listening(0.4f), session.state.value)
        session.stop()
        assertEquals(1, stt.calls)
        assertEquals("ok", messages.last())
        assertEquals(VoiceState.Idle, session.state.value)
        assertFalse(busy.value)
        assertEquals(listOf(0L to 1_000L), finished)
    }

    @Test
    fun aTooShortClipNeverReachesTheModel() {
        recorder.samples = ShortArray(800) { 1_000 }
        session.start()
        session.stop()
        assertEquals(0, stt.calls)
        assertEquals("Hold the button while you speak.", messages.last())
        assertEquals(VoiceState.Idle, session.state.value)
    }

    @Test
    fun aSilentClipNeverReachesTheModel() {
        recorder.samples = ShortArray(16_000) { 10 }
        session.start()
        session.stop()
        assertEquals(0, stt.calls)
        assertEquals("I could not hear anything. Hold the phone closer and try again.", messages.last())
    }

    @Test
    fun stoppingWithoutListeningDoesNothing() {
        session.stop()
        assertEquals(0, recorder.stops)
        assertNull(messages.lastOrNull())
        assertTrue(busy.value.not())
    }
}
