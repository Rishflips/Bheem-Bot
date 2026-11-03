package app.bheem.core.planner

import app.bheem.core.parse.AnswerFormats
import app.bheem.core.parse.AnswerParser
import app.bheem.core.speech.LlmAudioSpeech
import app.bheem.core.speech.NoAudioSpeech
import app.bheem.models.Capability
import app.bheem.models.host.ModelHost
import app.bheem.models.host.ModelProfile
import app.bheem.models.speech.SpeechToText

/** Builds the model-facing pieces of the harness from a model's profile, so a swap is a data change. Unknown names fail here, at wiring. */
object ModelHarness {
    fun planner(host: ModelHost, profile: ModelProfile, catalog: PromptCatalog = PromptCatalog.Default): LlmPlanner =
        LlmPlanner(host, catalog.get(profile.promptId), profile.nowSuffix)

    fun parser(profile: ModelProfile): AnswerParser = AnswerFormats.byId(profile.answerFormat)

    fun speech(host: ModelHost, profile: ModelProfile): SpeechToText =
        if (Capability.AUDIO_IN in profile.capabilities) LlmAudioSpeech(host) else NoAudioSpeech
}
