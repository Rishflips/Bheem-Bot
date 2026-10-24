package app.bheem.models.host

import app.bheem.models.BackendKind
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface ModelState {
    data object Unloaded : ModelState

    data object Absent : ModelState

    data object Verifying : ModelState

    data class Loading(val backend: BackendKind) : ModelState

    data class Ready(val backend: BackendKind) : ModelState

    data class Failed(val reason: String) : ModelState
}

class ModelUnavailable(message: String) : Exception(message)

interface ModelHost {
    val state: StateFlow<ModelState>

    suspend fun acquire(): LlmEngine

    fun release()

    fun trim()

    suspend fun shutdown()

    fun abortLoad(reason: String)
}

class DefaultModelHost(
    private val model: RegisteredModel,
    private val path: String,
    private val engine: LlmEngine,
    private val facts: FileFacts,
    private val verifier: FileVerifier,
    private val store: HostStateStore,
    private val engineVersion: String,
    private val appVersion: Int,
    private val capability: () -> Capability,
    private val scope: CoroutineScope,
    private val hooks: LoadHooks = LoadHooks.None,
) : ModelHost {
    private val mutex = Mutex()
    private val lock = Any()
    private val _state = MutableStateFlow<ModelState>(ModelState.Unloaded)
    private var uses = 0
    private var releaseCredit = 0
    private var idleJob: Job? = null
    private var loadingJob: Job? = null
    private var abortReason: String? = null

    @Volatile
    private var loaded = false

    @Volatile
    private var loading = false

    @Volatile
    private var trimWanted = false

    override val state: StateFlow<ModelState> = _state

    override suspend fun acquire(): LlmEngine = mutex.withLock {
        synchronized(lock) {
            idleJob?.cancel()
            idleJob = null
        }
        if (loaded) {
            synchronized(lock) { uses++ }
            return@withLock engine
        }
        val allowed = capability()
        if (allowed == Capability.RulesOnly) throw ModelUnavailable("the model is paused to save battery or heat")
        if (!facts.exists(path)) {
            _state.value = ModelState.Absent
            throw ModelUnavailable("the model is not on this phone yet")
        }
        val attempt = Job(coroutineContext[Job])
        synchronized(lock) {
            loading = true
            loadingJob = attempt
            abortReason = null
        }
        hooks.loadStarted()
        try {
            withContext(attempt) {
                verify()
                load(allowed)
            }
        } catch (e: CancellationException) {
            if (_state.value is ModelState.Loading) withContext(NonCancellable) { engine.unload() }
            val reason = synchronized(lock) { abortReason }
            _state.value = if (reason != null) ModelState.Failed(reason) else ModelState.Unloaded
            if (reason != null && coroutineContext[Job]?.isActive == true) throw ModelUnavailable(reason)
            throw e
        } catch (e: ModelUnavailable) {
            throw e
        } catch (e: Exception) {
            fail("the model could not be prepared (${e.message})")
        } finally {
            synchronized(lock) {
                loading = false
                loadingJob = null
                if (!loaded) trimWanted = false
                attempt.complete()
            }
            hooks.loadFinished()
        }
        synchronized(lock) { uses++ }
        engine
    }

    override fun release() {
        synchronized(lock) {
            if (releaseCredit > 0) {
                releaseCredit--
                return
            }
            check(uses > 0) { "release without a matching acquire" }
            uses--
            if (uses == 0) idleJob = scope.launch {
                if (!trimWanted) delay(IDLE_MILLIS)
                unloadIfIdle()
            }
        }
    }

    override fun trim() {
        synchronized(lock) {
            if (!loaded && !loading) return
            trimWanted = true
            if (uses == 0 && loaded) scope.launch { unloadIfIdle() }
        }
    }

    override fun abortLoad(reason: String) {
        val job = synchronized(lock) {
            if (!loading) return
            abortReason = reason
            loadingJob
        }
        job?.cancel()
    }

    override suspend fun shutdown() = mutex.withLock {
        synchronized(lock) {
            idleJob?.cancel()
            idleJob = null
            releaseCredit += uses
            uses = 0
            trimWanted = false
        }
        if (loaded) engine.unload()
        loaded = false
        _state.value = ModelState.Unloaded
    }

    private suspend fun verify() {
        val pinned = model.files.first()
        val size = facts.size(path)
        val modified = facts.lastModified(path)
        if (size != pinned.bytes) fail("the model file has the wrong size (expected ${pinned.bytes} bytes, found $size)")
        val known = store.verified()
        val fresh = known != null && known.path == path && known.size == size && known.modified == modified &&
            known.sha256 == pinned.sha256 && known.appVersion == appVersion
        if (fresh) return
        _state.value = ModelState.Verifying
        val hash = verifier.sha256(path)
        if (hash != pinned.sha256) fail("the model file failed its checksum and was not loaded")
        store.saveVerified(VerifiedFile(path, size, modified, hash, appVersion))
    }

    private fun fail(reason: String): Nothing {
        _state.value = ModelState.Failed(reason)
        throw ModelUnavailable(reason)
    }

    private suspend fun load(allowed: Capability) {
        val skipGpu = allowed == Capability.CpuOnly || store.gpuFailedVersion() == engineVersion
        val backends = if (skipGpu) listOf(BackendKind.CPU) else listOf(BackendKind.GPU, BackendKind.CPU)
        val failures = mutableListOf<String>()
        var gpuFailed = false
        for (backend in backends) {
            _state.value = ModelState.Loading(backend)
            val millis = try {
                engine.load(ModelSpec(model.id, path, backend))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (backend == BackendKind.GPU) gpuFailed = true
                failures += "$backend: ${e.message}"
                continue
            }
            if (gpuFailed) runCatching { store.saveGpuFailed(engineVersion) }
            runCatching { store.saveLastLoadMillis(millis) }
            loaded = true
            _state.value = ModelState.Ready(backend)
            return
        }
        fail("the model could not be loaded (${failures.joinToString("; ")})")
    }

    private suspend fun unloadIfIdle() = mutex.withLock {
        val idle = synchronized(lock) { uses == 0 && loaded }
        if (idle) {
            engine.unload()
            synchronized(lock) { trimWanted = false }
            loaded = false
            _state.value = ModelState.Unloaded
        }
    }

    companion object {
        const val IDLE_MILLIS = 180_000L
    }
}
