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

/**
 * LiteRT-LM 0.18.1 keeps state between conversations on one engine for hybrid models such as Qwen3.5
 * (verified on a Moto g86 power: identical inputs give drifting answers; a fresh engine or a plain-attention
 * model does not). [freshEngineEveryCase] rebuilds the engine before every request as a workaround.
 */
@OptIn(ExperimentalApi::class)
class LiteRtLmEngine(
    private val cacheDirPath: String,
    private val collectBenchmark: Boolean = true,
    private val freshEngineEveryCase: Boolean = false,
) : LlmEngine {
    private var engine: Engine? = null
    private var engineConfig: EngineConfig? = null
    private var thinking: ThinkingMode = ThinkingMode.OFF

    override suspend fun load(spec: ModelSpec): Long = withContext(Dispatchers.Default) {
        unload()
        val started = SystemClock.elapsedRealtime()
        val backend = when (spec.backend) {
            BackendKind.CPU -> Backend.CPU()
            BackendKind.GPU -> Backend.GPU()
        }
        ExperimentalFlags.enableBenchmark = collectBenchmark
        val config = EngineConfig(modelPath = spec.path, backend = backend, cacheDir = cacheDirPath)
        engineConfig = config
        val created = Engine(config)
        created.initialize()
        engine = created
        thinking = spec.thinking
        SystemClock.elapsedRealtime() - started
    }

    override suspend fun complete(request: GenerationRequest): Completion = withContext(Dispatchers.Default) {
        if (freshEngineEveryCase) {
            engine?.close()
            engine = Engine(checkNotNull(engineConfig) { "model not loaded" }).also { it.initialize() }
        }
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
            val info = if (!collectBenchmark) null else try {
                conversation.getBenchmarkInfo()
            } catch (e: Exception) {
                null
            }
            Completion(
                text.toString(), firstChunkMs.coerceAtLeast(0), elapsed, chunks,
                outputTokens = info?.lastDecodeTokenCount,
                decodeTokensPerSec = info?.lastDecodeTokensPerSecond,
                contextTokens = if (!collectBenchmark) null else try {
                    conversation.getTokenCount()
                } catch (e: Exception) {
                    null
                },
            )
        }
    }

    override suspend fun unload() {
        engine?.close()
        engine = null
    }
}
