package app.bheem.app

import android.content.Context
import app.bheem.core.gate.Gate
import app.bheem.core.gate.GatePolicy
import app.bheem.core.gate.SourceRateLimiter
import app.bheem.core.ledger.Ledger
import app.bheem.core.memory.LocalExecutors
import app.bheem.core.pipeline.CommandPipeline
import app.bheem.core.planner.LlmPlanner
import app.bheem.core.proposal.ProposalCoordinator
import app.bheem.models.BackendKind
import app.bheem.models.ModelSpec
import app.bheem.models.litertlm.LiteRtLmEngine
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

class AppGraph(context: Context) {
    private val db = BheemDb(context)
    val ledger = Ledger(SqliteLedgerStore(db))
    val proposals = SqliteProposalStore(db)
    val memory = SqliteMemoryStore(db)

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
        listOf(ModelSpec("gemma-4-e2b", modelFile.absolutePath, BackendKind.GPU), ModelSpec("gemma-4-e2b", modelFile.absolutePath, BackendKind.CPU)),
    )

    val pipeline = CommandPipeline(planner, Gate(GatePolicy(), SourceRateLimiter(maxPerWindow = 30, windowSeconds = 60)), ledger, proposals, newId)
    val coordinator = ProposalCoordinator(proposals, ledger, Gate(), executors, UNDO_WINDOW_SECONDS)

    companion object {
        const val MODEL_FILE = "gemma-4-E2B-it.litertlm"
        const val UNDO_WINDOW_SECONDS = 30L
    }
}
