package app.bheem.bench

import android.app.Activity
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.ScrollView
import android.widget.TextView
import app.bheem.eval.data.AdversarialCase
import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.Dataset
import app.bheem.eval.data.Jsonl
import app.bheem.eval.data.NotificationCase
import app.bheem.eval.run.BenchmarkRunner
import app.bheem.eval.run.DeviceInfo
import app.bheem.eval.run.RunResult
import app.bheem.models.BackendKind
import app.bheem.models.ModelSpec
import app.bheem.models.ThinkingMode
import app.bheem.models.litertlm.LiteRtLmEngine
import java.io.File
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
        val name = "${deviceLabel}__${modelId}__${backend.name.lowercase()}__${thinking.name.lowercase()}"
        val resultFile = File(resultsDir, "$name.json")
        val doneFile = File(resultsDir, "$name.done")
        doneFile.delete()

        scope.launch {
            val spec = ModelSpec(modelId, File(external, "models/$modelFile").absolutePath, backend, thinking)
            val memory = ActivityManager.MemoryInfo().also { getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
            val device = DeviceInfo(deviceLabel, Build.MODEL, Build.SOC_MODEL, memory.totalMem / (1024 * 1024), Build.VERSION.SDK_INT)
            say("loading ${spec.path}")
            val runner = BenchmarkRunner(LiteRtLmEngine(cacheDir.path), AndroidSampler(this@BenchActivity)) { snapshot ->
                resultFile.writeText(Jsonl.json.encodeToString(RunResult.serializer(), snapshot))
                say("progress: ${snapshot.cases.size} cases, loadError=${snapshot.loadError}")
            }
            runner.run(spec, device, loadDataset(), limit)
            doneFile.writeText("done")
            say("finished: ${resultFile.absolutePath}")
        }
    }

    private fun loadDataset(): Dataset {
        fun text(name: String) = assets.open(name).bufferedReader(Charsets.UTF_8).use { it.readText() }
        return Dataset(
            Jsonl.readText<CommandCase>(text("commands.jsonl")),
            Jsonl.readText<NotificationCase>(text("notifications.jsonl")),
            Jsonl.readText<AdversarialCase>(text("adversarial.jsonl")),
        )
    }

    private fun say(line: String) {
        runOnUiThread { log.append(line + "\n") }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
