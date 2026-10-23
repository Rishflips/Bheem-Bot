package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.parse.AnswerFormat
import app.bheem.eval.run.CaseJudge
import app.bheem.eval.run.DeviceInfo
import app.bheem.eval.run.ResourceSample
import app.bheem.eval.run.ResourceSampler
import app.bheem.eval.run.evenlySpaced
import app.bheem.models.Wav
import java.nio.file.Files
import java.nio.file.Path
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.Serializable

@Serializable
data class SpeechCaseResult(
    val takeId: String,
    val speaker: String,
    val commandId: String,
    val condition: String,
    val transcriber: String,
    val audioMs: Long?,
    val raw: String,
    val transcript: String,
    val firstChunkMs: Long,
    val totalMs: Long,
    val error: String?,
    val plannerRaw: String? = null,
    val actionCorrect: Boolean? = null,
    val argsCorrect: Boolean? = null,
)

@Serializable
data class SpeechRunResult(
    val transcribers: List<String>,
    val device: DeviceInfo,
    val loadMs: Long?,
    val loadError: String?,
    val samples: List<ResourceSample>,
    val cases: List<SpeechCaseResult>,
    val complete: Boolean = false,
)

class ModelLifecycle(val load: suspend () -> Long, val unload: suspend () -> Unit)

class SpeechRunner(
    private val transcribers: List<Transcriber>,
    private val sampler: ResourceSampler,
    private val lifecycle: ModelLifecycle,
    private val planner: (suspend (String) -> String)? = null,
    private val onProgress: (SpeechRunResult) -> Unit = {},
) {
    suspend fun run(
        device: DeviceInfo,
        takes: List<Take>,
        audioDir: Path,
        commands: Map<String, CommandCase> = emptyMap(),
        limit: Int? = null,
        progressEvery: Int = 10,
    ): SpeechRunResult {
        val samples = mutableListOf(sampler.sample("start"))
        val cases = mutableListOf<SpeechCaseResult>()

        fun snapshot(loadMs: Long?, loadError: String?, complete: Boolean = false) =
            SpeechRunResult(transcribers.map { it.id }, device, loadMs, loadError, samples.toList(), cases.toList(), complete)

        val loadMs = try {
            lifecycle.load()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return snapshot(null, e.toString()).also(onProgress)
        }
        samples += sampler.sample("after_load")
        try {
            val selected = if (limit == null) takes else takes.evenlySpaced(limit)
            for (take in selected) {
                for (transcriber in transcribers) {
                    cases += attempt(take, transcriber, audioDir, commands[take.commandId])
                    if (cases.size % progressEvery == 0) {
                        samples += sampler.sample("case_${cases.size}")
                        onProgress(snapshot(loadMs, null))
                    }
                }
            }
        } finally {
            try {
                lifecycle.unload()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
            }
        }
        samples += sampler.sample("end")
        return snapshot(loadMs, null, complete = true).also(onProgress)
    }

    private suspend fun attempt(take: Take, transcriber: Transcriber, audioDir: Path, command: CommandCase?): SpeechCaseResult {
        fun result(
            audioMs: Long? = null, transcription: Transcription? = null, error: String? = null,
            plannerRaw: String? = null, action: Boolean? = null, args: Boolean? = null,
        ) = SpeechCaseResult(
            take.id, take.speaker, take.commandId, take.condition.name.lowercase(), transcriber.id, audioMs,
            transcription?.raw.orEmpty(), transcription?.text.orEmpty(), transcription?.firstChunkMs ?: 0, transcription?.totalMs ?: 0,
            error, plannerRaw, action, args,
        )

        val path = audioDir.resolve("${take.id}.wav")
        if (!Files.exists(path)) return result(error = "audio file not found: $path")
        val audio = try {
            Wav.read(Files.readAllBytes(path)).requireSpeechFormat()
        } catch (e: IllegalArgumentException) {
            return result(error = "bad audio: ${e.message}")
        }
        val transcription = try {
            transcriber.transcribe(path.toString())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return result(audioMs = audio.durationMs, error = e.toString(), action = judgedFalse(command), args = judgedFalse(command))
        }
        val expected = command?.expected
        if (planner == null || expected == null) return result(audio.durationMs, transcription)
        if (transcription.text.isBlank()) return result(audio.durationMs, transcription, action = false, args = false)
        return try {
            val raw = planner.invoke(transcription.text)
            val judged = CaseJudge.judge(raw, AnswerFormat.LINE, expected)
            result(audio.durationMs, transcription, plannerRaw = raw, action = judged.actionCorrect, args = judged.argsCorrect)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            result(audio.durationMs, transcription, error = "planner failed: $e", action = false, args = false)
        }
    }

    private fun judgedFalse(command: CommandCase?): Boolean? = if (planner != null && command != null) false else null
}
