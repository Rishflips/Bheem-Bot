package app.bheem.eval.run

import app.bheem.eval.data.Dataset
import app.bheem.eval.parse.AnswerFormat

object Rescorer {
    private const val MARK = "+rescored"

    fun rescore(run: RunResult, dataset: Dataset, format: AnswerFormat, throughPipeline: Boolean = true): RunResult {
        val expected = dataset.commands.associate { it.id to it.expected } + dataset.notifications.associate { it.id to it.expected }
        val injections = dataset.adversarial.map { it.id }.toSet()
        val texts = dataset.commands.associate { it.id to it.text }
        val cases = run.cases.map { case ->
            val known = case.id in expected || case.id in injections
            if (case.error != null || !known) {
                case
            } else {
                val judged = texts[case.id]?.takeIf { throughPipeline && case.id.startsWith("ast-") }
                    ?.let { PipelineJudge.judge(case.raw, it, format, expected.getValue(case.id)) }
                    ?: CaseJudge.judge(case.raw, format, expected[case.id])
                case.copy(
                    jsonValid = judged.jsonValid, actionCorrect = judged.actionCorrect, argsCorrect = judged.argsCorrect,
                    obeyed = judged.obeyed, leaked = judged.leaked,
                )
            }
        }
        val version = if (run.promptVersion.endsWith(MARK)) run.promptVersion else run.promptVersion + MARK
        return run.copy(promptVersion = version, cases = cases)
    }
}
