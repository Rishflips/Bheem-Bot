package app.bheem.eval.run

import app.bheem.eval.data.Dataset
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.parse.OutputParser
import app.bheem.eval.parse.ParseResult
import app.bheem.eval.prompt.PromptSet
import app.bheem.eval.prompt.Prompts
import app.bheem.eval.score.CaseScorer
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import kotlin.coroutines.cancellation.CancellationException

class BenchmarkRunner(
    private val engine: LlmEngine,
    private val sampler: ResourceSampler,
    private val prompts: PromptSet = Prompts.full,
    private val onProgress: (RunResult) -> Unit = {},
) {
    suspend fun run(
        spec: ModelSpec,
        device: DeviceInfo,
        dataset: Dataset,
        limitPerSuite: Int? = null,
        progressEvery: Int = 25,
    ): RunResult {
        val samples = mutableListOf(sampler.sample("start"))
        val cases = mutableListOf<CaseResult>()

        fun snapshot(loadMs: Long?, loadError: String?, complete: Boolean = false) = RunResult(
            prompts.version, spec.id, spec.backend.name, spec.thinking.name,
            device, loadMs, loadError, samples.toList(), cases.toList(), complete,
        )

        val loadMs = try {
            engine.load(spec)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return snapshot(null, e.toString()).also(onProgress)
        }
        samples += sampler.sample("after_load")

        fun <T> List<T>.limited() = if (limitPerSuite == null) this else evenlySpaced(limitPerSuite)

        try {
            for (c in dataset.commands.limited()) {
                cases += attempt(c.id, "command", prompts.command,Prompts.userCommand(c.text), c.expected)
                afterCase(cases.size, progressEvery, samples) { onProgress(snapshot(loadMs, null)) }
            }
            for (n in dataset.notifications.limited()) {
                cases += attempt(n.id, "notification", prompts.notification,Prompts.userNotification(n.source, n.text), n.expected)
                afterCase(cases.size, progressEvery, samples) { onProgress(snapshot(loadMs, null)) }
            }
            for (a in dataset.adversarial.limited()) {
                cases += attempt(a.id, "adversarial", prompts.notification,Prompts.userNotification(a.source, a.text), null)
                afterCase(cases.size, progressEvery, samples) { onProgress(snapshot(loadMs, null)) }
            }
        } finally {
            try {
                engine.unload()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
        samples += sampler.sample("end")
        return snapshot(loadMs, null, complete = true).also(onProgress)
    }

    private fun afterCase(count: Int, every: Int, samples: MutableList<ResourceSample>, publish: () -> Unit) {
        if (count % every == 0) {
            samples += sampler.sample("case_$count")
            publish()
        }
    }

    private suspend fun attempt(id: String, suite: String, system: String, user: String, expected: ExpectedAction?): CaseResult {
        val completion = try {
            engine.complete(GenerationRequest(system, user))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return CaseResult(
                id, suite, "", false,
                expected?.let { false }, expected?.let { false }, if (expected == null) false else null,
                0, 0, 0, 0, e.toString(),
            )
        }
        val parsed = OutputParser.parse(completion.text)
        val score = expected?.let { CaseScorer.score(parsed, it) }
        return CaseResult(
            id = id, suite = suite, raw = completion.text,
            jsonValid = score?.jsonValid ?: (parsed is ParseResult.Valid),
            actionCorrect = score?.actionCorrect, argsCorrect = score?.argsCorrect,
            obeyed = if (expected == null) CaseScorer.obeyedRiskyAction(parsed) else null,
            firstChunkMs = completion.firstChunkMs, totalMs = completion.totalMs,
            chunkCount = completion.chunkCount, outChars = completion.text.length, error = null,
            outputTokens = completion.outputTokens, decodeTokensPerSec = completion.decodeTokensPerSec,
            contextTokens = completion.contextTokens,
        )
    }
}
