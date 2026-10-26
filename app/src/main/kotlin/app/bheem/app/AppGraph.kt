package app.bheem.app

import android.content.Context
import app.bheem.core.gate.Gate
import app.bheem.core.gate.GatePolicy
import app.bheem.core.gate.SourceRateLimiter
import app.bheem.core.ledger.Ledger
import app.bheem.core.memory.LocalExecutors
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.planner.LlmPlanner
import app.bheem.core.planner.PlanSource
import app.bheem.core.planner.RulesFallbackPlanner
import app.bheem.core.planner.PlannerPrompt
import app.bheem.core.proposal.HandOffExecutors
import app.bheem.core.proposal.ProposalCoordinator
import app.bheem.core.proposal.ProposalMeta
import app.bheem.models.LlmEngine
import app.bheem.models.host.DefaultModelHost
import app.bheem.models.host.JsonFileHostStateStore
import app.bheem.models.host.ModelRegistry
import app.bheem.models.host.ModelSupply
import app.bheem.models.host.RealFileFacts
import app.bheem.models.host.Sha256FileVerifier
import app.bheem.models.litertlm.LiteRtLmEngine
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel

class AppGraph(context: Context) {
    val storage = SharedStorage.get(context)
    val ledger = Ledger(storage.ledger, storage.transactions)
    val proposals = storage.proposals
    val memory = storage.memory
    val feed = storage.feed

    private val registered = ModelRegistry.parse(context.assets.open("models.json").bufferedReader().use { it.readText() }).single { it.id == MODEL_ID }
    private val pinned = registered.files.first()
    val modelFile = File(context.filesDir, "models/${pinned.name}")
    private val sideloadFile = File(context.getExternalFilesDir(null) ?: File(context.filesDir, "sideload"), "models/${pinned.name}")
    private val supplyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val supply = ModelSupply(modelFile, sideloadFile, pinned, Distribution.downloader(context), supplyScope)
    private val conditions = PhoneConditions(context)
    private val realEngine = LiteRtLmEngine(context.cacheDir.path, collectBenchmark = false)
    private val engine: LlmEngine = debugSlowLoad(realEngine, File(context.noBackupFilesDir, "debug-slow-load-ms"))
    private val hostState = JsonFileHostStateStore(File(context.noBackupFilesDir, "model-host.json"))
    val modelHost = DefaultModelHost(
        registered, modelFile.absolutePath, engine, RealFileFacts, Sha256FileVerifier, hostState, BuildConfig.LITERT_LM_VERSION, BuildConfig.VERSION_CODE,
        conditions::capability, CoroutineScope(SupervisorJob() + Dispatchers.Default), ForegroundLoadHooks(context.applicationContext),
        onCorrupt = { supply.discard() },
    )
    private val heatWatch = conditions.watchHeat(modelHost, Dispatchers.Default.asExecutor())

    init {
        ModelLoadService.timeoutListener = { modelHost.abortLoad("the model took too long to load; try again") }
    }

    fun close() {
        heatWatch.close()
        supplyScope.cancel()
        ModelLoadService.timeoutListener = null
    }
    val lastLoadSeconds: Long? get() = hostState.lastLoadMillis()?.div(1000)

    val reminderServices = ReminderServices.lightweight(context)
    private val clock = { LocalDateTime.now() }
    private val newId = { UUID.randomUUID().toString() }
    private val handOff = AndroidHandOffLauncher(context.applicationContext)
    private val executors = listOf(
        AndroidReminderExecutor(reminderServices.scheduler),
        HandOffExecutors.calendar(handOff),
        HandOffExecutors.message(handOff),
        LocalExecutors.note(memory, newId, clock),
        LocalExecutors.task(memory, newId, clock),
        LocalExecutors.expense(memory, newId, clock),
    )
    private val llmPlanner = LlmPlanner(modelHost)
    private val planner = RulesFallbackPlanner(llmPlanner)

    private val gate = Gate(GatePolicy(), SourceRateLimiter(maxPerWindow = 30, windowSeconds = 60))
    val pipeline = CommandPipeline(
        planner, gate, ledger, proposals, storage.transactions,
        {
            if (planner.lastSource == PlanSource.Rules) {
                ProposalMeta(RULES_VERSION, "rules", "none")
            } else {
                ProposalMeta(PlannerPrompt.VERSION, MODEL_ID, llmPlanner.backendName())
            }
        }, newId,
    )
    val coordinator = ProposalCoordinator(proposals, ledger, gate, executors, UNDO_WINDOW_SECONDS, storage.transactions)

    companion object {
        const val MODEL_ID = "gemma-4-e2b"
        const val RULES_VERSION = "rules-v1"
        const val UNDO_WINDOW_SECONDS = 30L
    }
}

private fun debugSlowLoad(engine: LlmEngine, marker: File): LlmEngine {
    if (!BuildConfig.DEBUG || !marker.exists()) return engine
    val extra = marker.readText().trim().toLongOrNull() ?: return engine
    return object : LlmEngine by engine {
        override suspend fun load(spec: app.bheem.models.ModelSpec): Long {
            kotlinx.coroutines.delay(extra)
            return engine.load(spec) + extra
        }
    }
}
