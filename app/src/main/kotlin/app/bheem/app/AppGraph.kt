package app.bheem.app

import android.content.Context
import app.bheem.core.gate.Gate
import app.bheem.core.gate.GatePolicy
import app.bheem.core.gate.Provenance
import app.bheem.core.gate.SourceRateLimiter
import app.bheem.core.ledger.Ledger
import app.bheem.core.memory.LocalExecutors
import app.bheem.core.memory.PhoneBook
import app.bheem.core.memory.Recall
import app.bheem.core.pipeline.PhoneLookup
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.planner.LlmPlanner
import app.bheem.core.planner.PlanSource
import app.bheem.core.planner.RulesFallbackPlanner
import app.bheem.core.planner.ModelHarness
import app.bheem.core.planner.PromptCatalog
import app.bheem.core.memory.RecordKind
import app.bheem.core.proposal.HandOffExecutors
import app.bheem.core.speech.VoiceCommands
import app.bheem.core.speech.VoiceHints
import app.bheem.models.speech.Hints
import app.bheem.core.proposal.ProposalCoordinator
import app.bheem.core.proposal.ProposalMeta
import app.bheem.models.LlmEngine
import app.bheem.models.host.DefaultModelHost
import app.bheem.models.host.JsonFileHostStateStore
import app.bheem.models.Capability
import app.bheem.models.host.ModelRegistry
import app.bheem.models.host.ModelSelection
import app.bheem.models.host.ModelSupply
import app.bheem.models.host.RealFileFacts
import app.bheem.models.host.Sha256FileVerifier
import app.bheem.models.litertlm.LiteRtLmEngine
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.cancel

class AppGraph(context: Context) {
    val appContext: Context = context.applicationContext
    val storage = SharedStorage.get(context)
    val ledger = Ledger(storage.ledger, storage.transactions)
    val proposals = storage.proposals
    val memory = storage.memory
    val feed = storage.feed
    val recall = Recall(memory, storage.reminders, ZoneId.systemDefault(), { id -> proposals.get(id)?.provenance == Provenance.USER }, { BriefInputs.from(storage, LocalDateTime.now()) })

    private val registered = ModelSelection.pick(ModelRegistry.parse(context.assets.open("models.json").bufferedReader().use { it.readText() }), "llm", null)
    private val profile = registered.profile
    private val pinned = registered.files.first()
    val modelFile = File(context.filesDir, "models/${pinned.name}")
    private val sideloadFile = File(context.getExternalFilesDir(null) ?: File(context.filesDir, "sideload"), "models/${pinned.name}")
    private val supplyScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val supply = ModelSupply(modelFile, sideloadFile, pinned, Distribution.downloader(context), supplyScope)
    private val conditions = PhoneConditions(context)
    private val realEngine = LiteRtLmEngine(context.cacheDir.path, collectBenchmark = false, audio = Capability.AUDIO_IN in profile.capabilities)
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
        HandOffExecutors.call(handOff),
        HandOffExecutors.route(handOff),
        LocalExecutors.note(memory, newId, clock),
        LocalExecutors.task(memory, newId, clock),
        LocalExecutors.expense(memory, newId, clock),
    )
    private val llmPlanner: LlmPlanner = ModelHarness.planner(modelHost, profile)
    private val promptVersion = PromptCatalog.Default.get(profile.promptId).version
    private val planner: RulesFallbackPlanner = RulesFallbackPlanner(llmPlanner)

    private val gate = Gate(GatePolicy(), SourceRateLimiter(maxPerWindow = 30, windowSeconds = 60))
    private val phoneLookup = PhoneLookup { name -> PhoneBook(memory.ofKind(RecordKind.NOTE).filter { proposals.get(it.id)?.provenance == Provenance.USER }).find(name) }

    val pipeline = CommandPipeline(
        planner, gate, ledger, proposals, storage.transactions,
        {
            if (planner.lastSource == PlanSource.Rules) {
                ProposalMeta(RULES_VERSION, "rules", "none")
            } else {
                ProposalMeta(promptVersion, registered.id, llmPlanner.backendName())
            }
        },
        parser = ModelHarness.parser(profile),
        phones = phoneLookup,
        newId = newId,
    )
    private val speech = ModelHarness.speech(modelHost, profile)
    val voice = VoiceCommands(speech, pipeline) { voiceHints() }
    val coordinator = ProposalCoordinator(proposals, ledger, gate, executors, UNDO_WINDOW_SECONDS, storage.transactions) { action ->
        if (action.action == "PlaceCall" && phoneLookup.find(action.args["name"].orEmpty()) != action.args["number"]) {
            "the number saved for ${action.args["name"].orEmpty()} has changed or is gone"
        } else {
            null
        }
    }

    private fun voiceHints(): Hints {
        val records = listOf(RecordKind.TASK, RecordKind.NOTE, RecordKind.EXPENSE).flatMap { memory.ofKind(it) }.sortedByDescending { it.createdAt }.take(HINT_RECORDS)
        return VoiceHints.fromUserRows(records, feed.rows(HINT_ROWS))
    }

    companion object {
        private const val HINT_RECORDS = 100
        private const val HINT_ROWS = 500
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
