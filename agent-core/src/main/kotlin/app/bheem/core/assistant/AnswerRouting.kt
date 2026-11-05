package app.bheem.core.assistant

import app.bheem.core.pipeline.Outcome

/** Decides what an owner's message means while the interview or a get-to-know-you question is waiting for an answer. */
object AnswerRouting {
    /** Only a message the pipeline would put in front of the owner is a command; a refusal or a plain answer is not. */
    fun isCommand(outcome: Outcome): Boolean = outcome is Outcome.Card || outcome is Outcome.Plan || outcome is Outcome.Clarify

    /** A pending gap question owns the next message only when the phone and the pipeline have nothing else for it. */
    fun isGapAnswer(deviceAnswer: String?, outcome: Outcome): Boolean = deviceAnswer == null && outcome === Outcome.Nothing
}
