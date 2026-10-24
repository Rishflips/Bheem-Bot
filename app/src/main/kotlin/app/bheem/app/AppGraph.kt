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
import app.bheem.models.BackendKind
import app.bheem.models.ModelSpec
import app.bheem.models.litertlm.LiteRtLmEngine
import app.bheem.storage.Storage
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

class AppGraph(context: Context) {
    val storage = Storage.open(context)
    val ledger = Ledger(storage.ledger, storage.transactions)
    val proposals = storage.proposals
    val memory = storage.memory
    val feed = storage.feed

    val modelFile = File(context.getExternalFilesDir(null), "models/$MODEL_FILE")
    val modelPresent: Boolean get() = modelFile.exists()

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
    private val engine = LiteRtLmEngine(context.cacheDir.path, collectBenchmark = false)
    private val planner = LlmPlanner(
        engine,
        listOf(ModelSpec(MODEL_ID, modelFile.absolutePath, BackendKind.GPU), ModelSpec(MODEL_ID, modelFile.absolutePath, BackendKind.CPU)),
    )

    private val gate = Gate(GatePolicy(), SourceRateLimiter(maxPerWindow = 30, windowSeconds = 60))
    val pipeline = CommandPipeline(
        planner, gate, ledger, proposals, storage.transactions,
        { ProposalMeta(PlannerPrompt.VERSION, MODEL_ID, planner.loadedBackend?.name ?: "unknown") }, newId,
    )
    val coordinator = ProposalCoordinator(proposals, ledger, gate, executors, UNDO_WINDOW_SECONDS, storage.transactions)

    companion object {
        const val MODEL_ID = "gemma-4-e2b"
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val UNDO_WINDOW_SECONDS = 30L
    }
}
