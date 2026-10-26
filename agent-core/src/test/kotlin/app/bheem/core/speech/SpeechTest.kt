package app.bheem.core.speech

import app.bheem.core.gate.Gate
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.memory.MemoryRecord
import app.bheem.core.memory.RecordKind
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.Outcome
import app.bheem.models.AudioForm
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelState
import app.bheem.models.host.ModelUnavailable
import app.bheem.models.speech.Hints
import app.bheem.models.speech.SpeechToText
import app.bheem.models.speech.Transcript
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking

class SpeechTest {
    private val now = LocalDateTime.of(2026, 10, 23, 8, 0)

    private class FakeEngine(var reply: String = "remind me tomorrow at 9 AM to call Arun", var fail: Exception? = null) : LlmEngine {
        var request: GenerationRequest? = null

        override suspend fun load(spec: ModelSpec) = 0L

        override suspend fun complete(request: GenerationRequest): Completion {
            this.request = request
            fail?.let { throw it }
            return Completion(reply, firstChunkMs = 700, totalMs = 2400, chunkCount = 9)
        }

        override suspend fun unload() = Unit
    }

    private class FakeHost(val engine: LlmEngine, var unavailable: ModelUnavailable? = null) : ModelHost {
        var acquired = 0
        var released = 0
        override val state: StateFlow<ModelState> = MutableStateFlow(ModelState.Unloaded)

        override suspend fun acquire(): LlmEngine {
            unavailable?.let { throw it }
            acquired++
            return engine
        }

        override fun release() {
            released++
        }

        override fun trim() = Unit

        override suspend fun shutdown() = Unit

        override fun abortLoad(reason: String) = Unit
    }

    private val wav = byteArrayOf(82, 73, 70, 70, 1, 2, 3)

    @Test
    fun theWavGoesToTheEngineAsBytesWithTheHintedPromptAndTheEngineIsReleased() = runBlocking<Unit> {
        val engine = FakeEngine("Transcript: \"remind me tomorrow at 9 AM to call Arun\"")
        val host = FakeHost(engine)
        val transcript = GemmaAudioSpeech(host).transcribe(wav, Hints(SpeechPrompts.commandWords, listOf("Arun", "Meena")))
        val sent = checkNotNull(engine.request)
        val clip = checkNotNull(sent.audio)
        assertEquals(AudioForm.WAV_BYTES, clip.form)
        assertContentEquals(wav, clip.bytes)
        assertEquals("", clip.path)
        assertTrue("Commands often start with these words: pay, remind me, schedule" in sent.user)
        assertTrue("These names may be spoken: Arun, Meena." in sent.user)
        assertEquals("remind me tomorrow at 9 AM to call Arun", transcript.text)
        assertEquals(700L, transcript.firstChunkMs)
        assertEquals(1, host.acquired)
        assertEquals(1, host.released)
    }

    @Test
    fun theEngineIsReleasedWhenTranscribingFails() = runBlocking<Unit> {
        val host = FakeHost(FakeEngine(fail = IllegalStateException("boom")))
        assertFailsWith<IllegalStateException> { GemmaAudioSpeech(host).transcribe(wav, Hints()) }
        assertEquals(1, host.released)
    }

    @Test
    fun anUnavailableModelIsPassedOnUnchanged() = runBlocking<Unit> {
        val host = FakeHost(FakeEngine(), ModelUnavailable("the model is not on this phone yet"))
        assertEquals("the model is not on this phone yet", assertFailsWith<ModelUnavailable> { GemmaAudioSpeech(host).transcribe(wav, Hints()) }.message)
    }

    @Test
    fun noHintsMeansTheBarePrompt() {
        assertEquals(SpeechPrompts.english, SpeechPrompts.forHints(Hints()))
        assertEquals("transcribe-v3-commands", SpeechPrompts.forHints(Hints(listOf("remind me"))).version)
        assertEquals("transcribe-v2-names", SpeechPrompts.forHints(Hints(names = listOf("Ravi"))).version)
        assertEquals("transcribe-v4-names-commands", SpeechPrompts.forHints(Hints(listOf("remind me"), listOf("Ravi"))).version)
    }

    @Test
    fun theCommandWordsStillIncludePaySoAPaymentRequestIsHeardAndRefusedRatherThanMisheardAsAMessage() {
        assertTrue(SpeechPrompts.commandWords.any { it.equals("pay", ignoreCase = true) })
        assertEquals("pay", SpeechPrompts.commandWords.first())
    }

    // ---- hints from the user's own records

    private fun record(kind: RecordKind, text: String, merchant: String? = null, at: Long = 0) =
        MemoryRecord("r$at$text".take(12), kind, text, now.plusMinutes(at), merchant)

    @Test
    fun namesComeFromTheUsersNotesTasksMerchantsAndRecentRecipients() {
        val hints = VoiceHints.build(
            records = listOf(
                record(RecordKind.NOTE, "Anita prefers tea without sugar"),
                record(RecordKind.NOTE, "the locker number is 27"),
                record(RecordKind.TASK, "call Ravi Kumar tomorrow"),
                record(RecordKind.EXPENSE, "Chai Point 90.00", merchant = "Chai Point"),
            ),
            recipients = listOf("Arun", "anita"),
        )
        assertEquals(SpeechPrompts.commandWords, hints.commandWords)
        assertEquals(listOf("Arun", "Anita", "Ravi", "Kumar", "Chai", "Point"), hints.names)
    }

    @Test
    fun commonWordsDaysMonthsAndTheWordIAreNotNames() {
        val hints = VoiceHints.build(
            listOf(record(RecordKind.NOTE, "Remember to pay on Monday in October, I think. The Tamil class is on Friday")),
            emptyList(),
        )
        assertEquals(listOf("Tamil"), hints.names)
    }

    @Test
    fun theListIsCappedAtSixtyAndRecipientsComeFirst() {
        val many = (1..100).joinToString(" ") { "N" + ('a' + it / 26) + ('a' + it % 26) + "q" }
        val hints = VoiceHints.build(listOf(record(RecordKind.NOTE, many)), listOf("Zed"))
        assertEquals(60, hints.names.size)
        assertEquals("Zed", hints.names.first())
    }

    @Test
    fun aSentenceStartingWithACommonCapitalisedWordDoesNotAddThatWord() {
        val hints = VoiceHints.build(listOf(record(RecordKind.TASK, "Buy milk. Call Meena")), emptyList())
        assertEquals(listOf("Meena"), hints.names)
    }

    // ---- the whole voice command

    private class FakeStt(var text: String = "remind me tomorrow at 9 AM to call Arun", var failure: Exception? = null) : SpeechToText {
        var hints: Hints? = null
        override suspend fun transcribe(wav: ByteArray, hints: Hints): Transcript {
            this.hints = hints
            failure?.let { throw it }
            return Transcript(text, text, 600, 2500)
        }
    }

    private val store = InMemoryLedgerStore()
    private var planned: String? = null
    private val pipeline = CommandPipeline({ text, _ -> planned = text; "CreateReminder|call Arun|2026-10-24T09:00" }, Gate(), Ledger(store)) { "p1" }

    @Test
    fun aSpokenCommandReachesTheSamePipelineAsATypedOneWithTheVoiceSource() = runBlocking<Unit> {
        val stt = FakeStt()
        val voice = VoiceCommands(stt, pipeline, { Hints(SpeechPrompts.commandWords, listOf("Arun")) })
        val result = assertIs<VoiceResult.Heard>(voice.handle(wav, now))
        assertEquals("remind me tomorrow at 9 AM to call Arun", result.transcript)
        assertIs<Outcome.Card>(result.outcome)
        assertEquals("remind me tomorrow at 9 AM to call Arun", planned)
        assertEquals(listOf("Arun"), stt.hints?.names)
        assertEquals("voice", store.all().single().source)
    }

    @Test
    fun anEmptyTranscriptNeverReachesThePipeline() = runBlocking<Unit> {
        for (text in listOf("", "   ", "...", "-")) {
            planned = null
            assertEquals(VoiceResult.NothingHeard, VoiceCommands(FakeStt(text), pipeline, { Hints() }).handle(wav, now), text)
            assertNull(planned)
        }
    }

    @Test
    fun aTranscriptThatRepeatsTheHintListIsNotAnUtterance() = runBlocking<Unit> {
        val echoes = listOf(
            "pay remind me schedule message save a note add a task I spent Ravi Priya Meena Suresh Arun Now Chai Point Locker",
            "Commands often start with these words: pay, remind me, schedule, message, save a note, add a task, I spent.",
            "remind me, schedule, message",
        )
        for (text in echoes) {
            planned = null
            val stt = FakeStt(text)
            assertEquals(VoiceResult.NothingHeard, VoiceCommands(stt, pipeline, { Hints(SpeechPrompts.commandWords, listOf("Ravi", "Arun")) }).handle(wav, now), text)
            assertNull(planned, text)
        }
    }

    @Test
    fun aRealCommandThatUsesTwoHintWordsStillReachesThePipeline() = runBlocking<Unit> {
        val voice = VoiceCommands(FakeStt("remind me to message Arun at 5 PM"), pipeline, { Hints(SpeechPrompts.commandWords, listOf("Arun")) })
        assertIs<VoiceResult.Heard>(voice.handle(wav, now))
    }

    @Test
    fun aMissingModelOrAFailedTranscriptionIsReportedInPlainWords() = runBlocking<Unit> {
        val unavailable = VoiceCommands(FakeStt(failure = ModelUnavailable("the model is not on this phone yet")), pipeline, { Hints() }).handle(wav, now)
        assertEquals(VoiceResult.Unavailable("the model is not on this phone yet"), unavailable)
        val failed = VoiceCommands(FakeStt(failure = IllegalStateException("decoder crashed")), pipeline, { Hints() }).handle(wav, now)
        assertEquals(VoiceResult.Unavailable("I could not understand the audio. Try again."), failed)
    }
}
