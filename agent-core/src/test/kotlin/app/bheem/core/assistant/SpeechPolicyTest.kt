package app.bheem.core.assistant

import app.bheem.core.memory.Citation
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpeechPolicyTest {
    private val at = LocalDateTime.of(2026, 10, 23, 8, 0)
    private val answer = Turn.Answer("You have nothing on Sat 24 Oct.", emptyList<Citation>(), at)
    private val chat = Turn.Chat("GST is a tax.", at)
    private val notice = Turn.Notice("I could not do that: payments come later.", at)
    private val brief = Turn.Brief("Your day, Fri 23 Oct:", emptyList(), at)
    private val user = Turn.User("hello", false, at)
    private val plan = Turn.Plan("plan", listOf("p1"), emptyList(), at)

    private fun should(mode: SpeakMode, spoken: Boolean, turn: Turn, call: Boolean = false, touch: Boolean = false, locked: Boolean = false) =
        SpeechPolicy.shouldSpeak(mode, spoken, turn, SpeechSituation(call, touch, locked))

    @Test
    fun neverMeansNever() {
        for (turn in listOf(answer, chat, notice)) assertFalse(should(SpeakMode.OFF, true, turn))
    }

    @Test
    fun whenIUseVoiceSpeaksOnlyAfterAVoiceQuestion() {
        for (turn in listOf(answer, chat, notice)) {
            assertTrue(should(SpeakMode.VOICE, true, turn), turn.toString())
            assertFalse(should(SpeakMode.VOICE, false, turn), turn.toString())
        }
    }

    @Test
    fun alwaysSpeaksAfterTypedQuestionsToo() {
        for (turn in listOf(answer, chat, notice)) assertTrue(should(SpeakMode.ALWAYS, false, turn))
    }

    @Test
    fun theOwnWordsBriefsAndPlansAreNeverSpokenAutomatically() {
        for (turn in listOf(user, brief, plan)) assertFalse(should(SpeakMode.ALWAYS, true, turn), turn.toString())
    }

    @Test
    fun nothingIsSpokenDuringACallWithTalkBackOrOnALockedPhone() {
        assertFalse(should(SpeakMode.ALWAYS, true, answer, call = true))
        assertFalse(should(SpeakMode.ALWAYS, true, answer, touch = true))
        assertFalse(should(SpeakMode.ALWAYS, true, answer, locked = true))
    }

    private fun voice(name: String, country: String = "US", network: Boolean = false, features: Set<String> = emptySet(), language: String = "en") =
        VoiceCandidate(name, language, country, network, features)

    @Test
    fun onlyAnInstalledEnglishVoiceThatWorksWithoutTheNetworkIsUsable() {
        assertTrue(SpeechPolicy.usable(voice("a")))
        assertFalse(SpeechPolicy.usable(voice("net", network = true)))
        assertFalse(SpeechPolicy.usable(voice("missing", features = setOf("notInstalled"))))
        assertFalse(SpeechPolicy.usable(voice("hindi", language = "hi")))
    }

    @Test
    fun anIndianEnglishVoiceIsPreferredAndNoUsableVoiceMeansSilence() {
        assertEquals("in", SpeechPolicy.pick(listOf(voice("us"), voice("in", country = "IN"), voice("net", country = "IN", network = true)))?.name)
        assertEquals("us", SpeechPolicy.pick(listOf(voice("us"), voice("net", country = "IN", network = true)))?.name)
        assertNull(SpeechPolicy.pick(listOf(voice("net", network = true), voice("missing", features = setOf("notInstalled")))))
        assertNull(SpeechPolicy.pick(emptyList()))
    }
}
