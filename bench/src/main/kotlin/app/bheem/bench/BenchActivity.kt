package app.bheem.bench

import android.app.Activity
import android.app.ActivityManager
import ai.moonshine.voice.Transcriber as MoonshineEngine
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import app.bheem.eval.data.AdversarialCase
import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Dataset
import app.bheem.eval.data.Jsonl
import app.bheem.eval.data.NotificationCase
import app.bheem.eval.io.AtomicFile
import app.bheem.eval.prompt.Prompts
import app.bheem.eval.run.BenchmarkRunner
import app.bheem.eval.run.DeviceInfo
import app.bheem.eval.run.RunResult
import app.bheem.eval.speech.GemmaActionFromAudio
import app.bheem.eval.speech.GemmaAudioTranscriber
import app.bheem.eval.speech.KeyTermMode
import app.bheem.eval.speech.KeyTerms
import app.bheem.eval.speech.ModelLifecycle
import app.bheem.eval.speech.MoonshineModel
import app.bheem.eval.speech.SamplesTranscriber
import app.bheem.eval.speech.SpeechEngine
import app.bheem.eval.speech.SpeechOptions
import app.bheem.eval.speech.SpeechRunResult
import app.bheem.eval.speech.SpeechRunner
import app.bheem.eval.speech.StreamingTranscriber
import app.bheem.eval.speech.Take
import app.bheem.eval.speech.Transcriber
import app.bheem.eval.speech.TranscriptionPrompts
import app.bheem.models.BackendKind
import app.bheem.models.GenerationRequest
import app.bheem.models.ModelSpec
import app.bheem.models.ThinkingMode
import app.bheem.models.litertlm.LiteRtLmEngine
import java.io.File
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BenchActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var log: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        getExternalFilesDir(null)?.let { dir ->
            File(dir, "models").mkdirs()
            File(dir, "results").mkdirs()
            File(dir, "recordings/audio").mkdirs()
            File(dir, "models/${MoonshineModel.FOLDER}").mkdirs()
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        log = TextView(this).apply {
            textSize = 12f
            setPadding(24, 24, 24, 24)
        }
        setContentView(ScrollView(this).apply { addView(log) })

        val modelId = intent.getStringExtra("model_id") ?: return say("missing --es model_id")
        val modelFile = intent.getStringExtra("model_file") ?: return say("missing --es model_file")
        val deviceLabel = intent.getStringExtra("device_label") ?: return say("missing --es device_label")
        val backend = if (intent.getStringExtra("backend") == "gpu") BackendKind.GPU else BackendKind.CPU
        val thinking = if (intent.getStringExtra("thinking") == "default") ThinkingMode.DEFAULT else ThinkingMode.OFF
        val limit = intent.getIntExtra("limit", 0).takeIf { it > 0 }

        val external = getExternalFilesDir(null) ?: return say("no external files dir")
        val resultsDir = File(external, "results").apply { mkdirs() }
        val speech = intent.getStringExtra("mode") == "speech"
        val speechSuffix = when {
            !speech -> ""
            intent.getStringExtra("engine")?.lowercase() == "moonshine" && intent.getStringExtra("live") == "on" -> "__speech-moonshine-live"
            intent.getStringExtra("engine")?.lowercase() == "moonshine" -> "__speech-moonshine"
            else -> "__speech"
        }
        val keyTermSuffix = if (speech) SpeechOptions.keyTermMode(intent.getStringExtra("keyterms")).let { if (it == KeyTermMode.OFF) "" else "-${it.name.lowercase()}" } else ""
        val hintSuffix = if (speech && SpeechOptions.switch(intent.getStringExtra("command_hint"), default = false)) "-commands" else ""
        val actionSuffix = if (speech && SpeechOptions.switch(intent.getStringExtra("action"), default = false)) "-action" else ""
        val assistantSet = !speech && intent.getStringExtra("dataset") == "assistant"
        val setSuffix = if (assistantSet) "__assistant" else ""
        val name = "${deviceLabel}__${modelId}__${backend.name.lowercase()}__${thinking.name.lowercase()}$speechSuffix$keyTermSuffix$hintSuffix$actionSuffix$setSuffix"
        val resultFile = File(resultsDir, "$name.json")
        val doneFile = File(resultsDir, "$name.done")
        doneFile.delete()

        val errorFile = File(resultsDir, "$name.error")
        errorFile.delete()
        val crashHandler = CoroutineExceptionHandler { _, e ->
            Log.e("BenchActivity", "run failed", e)
            runCatching { AtomicFile.writeText(errorFile.toPath(), e.stackTraceToString()) }
            runCatching { doneFile.writeText("done") }
            say("failed: $e")
        }

        scope.launch(crashHandler) {
            val spec = ModelSpec(modelId, File(external, "models/$modelFile").absolutePath, backend, thinking)
            val memory = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            val device = DeviceInfo(deviceLabel, Build.MODEL, Build.SOC_MODEL, memory.totalMem / (1024 * 1024), Build.VERSION.SDK_INT)
            say("loading ${spec.path}")
            if (speech) {
                runSpeech(spec, device, external, resultFile, limit)
                doneFile.writeText("done")
                say("finished: ${resultFile.absolutePath}")
                return@launch
            }
            val engine = LiteRtLmEngine(
                cacheDir.path,
                collectBenchmark = intent.getStringExtra("benchmark") != "off",
                freshEngineEveryCase = intent.getStringExtra("fresh_engine") == "on",
            )
            val prompts = Prompts.named(intent.getStringExtra("prompt") ?: "full")
            val runner = BenchmarkRunner(engine, AndroidSampler(this@BenchActivity), prompts) { snapshot ->
                AtomicFile.writeText(resultFile.toPath(), Jsonl.json.encodeToString(RunResult.serializer(), snapshot))
                say("progress: ${snapshot.cases.size} cases, loadError=${snapshot.loadError}")
            }
            val dataset = if (assistantSet) loadAssistantSet() else loadDataset().let { if (intent.getStringExtra("lang") == "en") it.englishOnly() else it }
            val repeat = intent.getIntExtra("repeat_first", 0)
            val used = if (repeat > 0) {
                Dataset(List(repeat) { dataset.commands.first().copy(id = "rep-%02d".format(it + 1)) }, emptyList(), emptyList())
            } else {
                dataset
            }
            runner.run(spec, device, used, limit)
            doneFile.writeText("done")
            say("finished: ${resultFile.absolutePath}")
        }
    }

    private suspend fun runSpeech(spec: ModelSpec, device: DeviceInfo, external: File, resultFile: File, limit: Int?) {
        val speechEngine = SpeechOptions.engine(intent.getStringExtra("engine"))
        val audioFirst = SpeechOptions.switch(intent.getStringExtra("audio_first"), default = true)
        val plan = SpeechOptions.switch(intent.getStringExtra("plan"), default = true)
        val recordings = File(external, "recordings")
        val takes = Jsonl.read<Take>(File(recordings, "manifest.jsonl").toPath())
        val commands = loadDataset().commands.associateBy { it.id }
        val gemma = speechEngine == SpeechEngine.GEMMA
        val engine = LiteRtLmEngine(cacheDir.path, collectBenchmark = false, audio = gemma)
        val moonshine = MoonshineEngine()
        val keyTermMode = SpeechOptions.keyTermMode(intent.getStringExtra("keyterms"))
        val keyTerms = KeyTerms.forMode(keyTermMode, commands.values.filter { it.lang == "en" })
        val commandHint = SpeechOptions.switch(intent.getStringExtra("command_hint"), default = false)
        val oneCall = SpeechOptions.switch(intent.getStringExtra("action"), default = false)
        val hintTag = keyTermMode.tag + if (commandHint) "+commands" else ""
        val moonshineTerms = if (commandHint) KeyTerms.withCommandWords(keyTerms) else keyTerms
        val transcribers: List<Transcriber> = if (gemma) {
            SpeechOptions.forms(intent.getStringExtra("audio_forms")).map {
                if (oneCall) {
                    GemmaActionFromAudio(engine, it, audioFirst, keyTerms, keyTermMode.tag)
                } else {
                    GemmaAudioTranscriber(engine, it, audioFirst, TranscriptionPrompts.withHints(keyTerms, commandHint), hintTag)
                }
            }
        } else if (SpeechOptions.switch(intent.getStringExtra("live"), default = false)) {
            val streams = MoonshineStreams(moonshine)
            listOf(
                StreamingTranscriber(
                    "${MoonshineModel.FOLDER}$hintTag-live", streams::open, SystemClock::elapsedRealtime, { Thread.sleep(it) },
                    SpeechOptions.chunkMs(intent.getStringExtra("chunk_ms")),
                ),
            )
        } else {
            listOf(SamplesTranscriber(MoonshineModel.FOLDER + hintTag, SystemClock::elapsedRealtime) { samples, rate -> moonshineText(moonshine, samples, rate) })
        }
        val planCommand: suspend (String) -> String = { transcript ->
            engine.complete(GenerationRequest(Prompts.named("line").command, Prompts.userCommand(transcript))).text
        }
        val lifecycle = ModelLifecycle(
            load = {
                if (gemma) {
                    engine.load(spec)
                } else {
                    if (plan) say("planner engine loaded in ${engine.load(spec)} ms")
                    loadMoonshine(moonshine, File(external, "models/${MoonshineModel.FOLDER}"), moonshineTerms)
                }
            },
            unload = {
                if (moonshine.isLoaded) moonshine.close()
                if (gemma || plan) engine.unload()
            },
        )
        val runner = SpeechRunner(transcribers, AndroidSampler(this), lifecycle, if (plan) planCommand else null) { snapshot ->
            AtomicFile.writeText(resultFile.toPath(), Jsonl.json.encodeToString(SpeechRunResult.serializer(), snapshot))
            say("progress: ${snapshot.cases.size} takes, loadError=${snapshot.loadError}")
        }
        runner.run(device, takes, File(recordings, "audio").toPath(), commands, limit)
    }

    private fun loadMoonshine(moonshine: MoonshineEngine, dir: File, keyTerms: List<String>): Long {
        val missing = MoonshineModel.missing(dir.toPath())
        check(missing.isEmpty()) { "Moonshine files missing on the phone in $dir: $missing" }
        val started = SystemClock.elapsedRealtime()
        moonshine.loadFromFiles(dir.absolutePath, MoonshineModel.ARCH)
        val loaded = SystemClock.elapsedRealtime() - started
        if (keyTerms.isNotEmpty()) moonshine.setKeyterms(keyTerms)
        return loaded
    }

    private fun moonshineText(moonshine: MoonshineEngine, samples: FloatArray, rate: Int): String =
        moonshine.transcribeWithoutStreaming(samples, rate)?.lines?.joinToString("\n") { it.text.orEmpty() }.orEmpty()

    private fun loadDataset(): Dataset {
        fun text(name: String) = assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return Dataset(
            Jsonl.readText<CommandCase>(text("commands.jsonl")),
            Jsonl.readText<NotificationCase>(text("notifications.jsonl")),
            Jsonl.readText<AdversarialCase>(text("adversarial.jsonl")),
        )
    }

    private fun loadAssistantSet(): Dataset =
        Dataset(Jsonl.readText<CommandCase>(assets.open("assistant.jsonl").bufferedReader(Charsets.UTF_8).use { it.readText() }), emptyList(), emptyList())

    private fun say(line: String) {
        runOnUiThread { log.append(line + "\n") }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
