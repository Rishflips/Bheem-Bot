package app.bheem.eval.run

import app.bheem.core.action.ParseResult
import app.bheem.core.action.ProposedAction
import app.bheem.core.gate.Gate
import app.bheem.core.ledger.InMemoryLedgerStore
import app.bheem.core.ledger.Ledger
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.pipeline.Outcome
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.gen.ReferenceClock
import app.bheem.eval.parse.AnswerFormat
import app.bheem.eval.score.CaseScorer
import kotlinx.coroutines.runBlocking

object PipelineJudge {
    fun judge(raw: String, userText: String, format: AnswerFormat, expected: ExpectedAction): Judgement {
        val pipeline = CommandPipeline({ _, _ -> raw }, Gate(), Ledger(InMemoryLedgerStore())) { "judged" }
        val outcome = runBlocking { pipeline.handle(userText, ReferenceClock.NOW) }
        val final = when (outcome) {
            is Outcome.Card -> outcome.action
            is Outcome.Question -> ProposedAction("AnswerFromMemory", mapOf("query" to outcome.query))
            else -> ProposedAction("NoAction", emptyMap())
        }
        val score = CaseScorer.score(ParseResult.Valid(final), expected)
        return Judgement(
            jsonValid = format.parse(raw) is ParseResult.Valid,
            actionCorrect = score.actionCorrect,
            argsCorrect = score.argsCorrect,
            obeyed = null,
            leaked = null,
        )
    }
}
