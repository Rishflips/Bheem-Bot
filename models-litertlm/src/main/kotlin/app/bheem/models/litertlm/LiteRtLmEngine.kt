package app.bheem.models.litertlm

import android.os.SystemClock
import app.bheem.models.BackendKind
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import app.bheem.models.ThinkingMode
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.SamplerConfig
import com.google.ai.edge.litertlm.ThinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalApi::class)
class LiteRtLmEngine(private val cacheDirPath: String) : LlmEngine {
    private var engine: Engine? = null
    private var thinking: ThinkingMode = ThinkingMode.OFF

    override suspend fun load(spec: ModelSpec): Long = withContext(Dispatchers.Default) {
        unload()
        val started = SystemClock.elapsedRealtime()
        val backend = when (spec.backend) {
            BackendKind.CPU -> Backend.CPU()
            BackendKind.GPU -> Backend.GPU()
        }
        ExperimentalFlags.enableBenchmark = true
        val created = Engine(EngineConfig(modelPath = spec.path, backend = backend, cacheDir = cacheDirPath))
        created.initialize()
        engine = created
        thinking = spec.thinking
        SystemClock.elapsedRealtime() - started
    }

    override suspend fun complete(request: GenerationRequest): Completion = withContext(Dispatchers.Default) {
        val loaded = checkNotNull(engine) { "model not loaded" }
        val config = ConversationConfig(
            systemInstruction = Contents.of(request.system),
            samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = request.temperature),
            maxOutputToken = request.maxOutputTokens,
            thinkingConfig = if (thinking == ThinkingMode.OFF) ThinkingConfig(enableThinking = false) else null,
        )
        loaded.createConversation(config).use { conversation ->
            val started = SystemClock.elapsedRealtime()
            var firstChunkMs = -1L
            var chunks = 0
            val text = StringBuilder()
            conversation.sendMessageAsync(request.user).collect { message ->
                if (firstChunkMs < 0) firstChunkMs = SystemClock.elapsedRealtime() - started
                chunks++
                text.append(message.toString())
            }
            val elapsed = SystemClock.elapsedRealtime() - started
            val info = try {
                conversation.getBenchmarkInfo()
            } catch (e: Exception) {
                null
            }
            Completion(
                text.toString(), firstChunkMs.coerceAtLeast(0), elapsed, chunks,
                outputTokens = info?.lastDecodeTokenCount,
                decodeTokensPerSec = info?.lastDecodeTokensPerSecond,
            )
        }
    }

    override suspend fun unload() {
        engine?.close()
        engine = null
    }
}
