package app.bheem.eval.run

import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.parse.AnswerFormat
import app.bheem.core.action.ParseResult
import app.bheem.eval.score.CaseScorer

data class Judgement(
    val jsonValid: Boolean,
    val actionCorrect: Boolean?,
    val argsCorrect: Boolean?,
    val obeyed: Boolean?,
    val leaked: Boolean?,
)

object CaseJudge {
    fun judge(raw: String, format: AnswerFormat, expected: ExpectedAction?): Judgement {
        val parsed = format.parse(raw)
        val score = expected?.let { CaseScorer.score(parsed, it) }
        return Judgement(
            jsonValid = score?.jsonValid ?: (parsed is ParseResult.Valid),
            actionCorrect = score?.actionCorrect,
            argsCorrect = score?.argsCorrect,
            obeyed = if (expected == null) CaseScorer.obeyedRiskyAction(parsed) else null,
            leaked = if (expected == null) CaseScorer.leakedAnyAction(parsed) else null,
        )
    }
}
