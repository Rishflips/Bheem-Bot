package app.bheem.app

import android.content.Context
import app.bheem.core.gate.Gate
import app.bheem.core.gate.GatePolicy
import app.bheem.core.gate.SourceRateLimiter
import app.bheem.core.ledger.Ledger
import app.bheem.core.memory.LocalExecutors
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.planner.LlmPlanner
import app.bheem.core.planner.PlannerPrompt
import app.bheem.core.proposal.ProposalCoordinator
import app.bheem.core.proposal.ProposalMeta
import app.bheem.models.host.DefaultModelHost
import app.bheem.models.host.JsonFileHostStateStore
import app.bheem.models.host.ModelRegistry
import app.bheem.models.host.RealFileFacts
import app.bheem.models.host.Sha256FileVerifier
import app.bheem.models.litertlm.LiteRtLmEngine
import app.bheem.storage.Storage
import java.io.File
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor

class AppGraph(context: Context) {
    val storage = Storage.open(context)
    val ledger = Ledger(storage.ledger, storage.transactions)
    val proposals = storage.proposals
    val memory = storage.memory
    val feed = storage.feed

    private val registered = ModelRegistry.parse(context.assets.open("models.json").bufferedReader().use { it.readText() }).single { it.id == MODEL_ID }
    val modelFile = File(context.getExternalFilesDir(null), "models/${registered.files.first().name}")
    private val conditions = PhoneConditions(context)
    private val engine = LiteRtLmEngine(context.cacheDir.path, collectBenchmark = false)
    private val hostState = JsonFileHostStateStore(File(context.noBackupFilesDir, "model-host.json"))
    val modelHost = DefaultModelHost(
        registered, modelFile.absolutePath, engine, RealFileFacts, Sha256FileVerifier, hostState, BuildConfig.LITERT_LM_VERSION,
        conditions::capability, CoroutineScope(SupervisorJob() + Dispatchers.Default), ForegroundLoadHooks(context.applicationContext),
    ).also { conditions.watchHeat(it, Dispatchers.Default.asExecutor()) }
    val lastLoadSeconds: Long? get() = hostState.lastLoadMillis()?.div(1000)

    private val clock = { LocalDateTime.now() }
    private val newId = { UUID.randomUUID().toString() }
    private val executors = listOf(
        AndroidReminderExecutor(context),
        AndroidCalendarExecutor(context),
        AndroidMessageExecutor(context),
        LocalExecutors.note(memory, newId, clock),
        LocalExecutors.task(memory, newId, clock),
        LocalExecutors.expense(memory, newId, clock),
    )
    private val planner = LlmPlanner(modelHost)

    private val gate = Gate(GatePolicy(), SourceRateLimiter(maxPerWindow = 30, windowSeconds = 60))
    val pipeline = CommandPipeline(
        planner, gate, ledger, proposals, storage.transactions,
        { ProposalMeta(PlannerPrompt.VERSION, MODEL_ID, planner.backendName()) }, newId,
    )
    val coordinator = ProposalCoordinator(proposals, ledger, gate, executors, UNDO_WINDOW_SECONDS, storage.transactions)

    companion object {
        const val MODEL_ID = "gemma-4-e2b"
        const val UNDO_WINDOW_SECONDS = 30L
    }
}
