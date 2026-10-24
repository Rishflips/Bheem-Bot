package app.bheem.models.host

import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SupplyState {
    data object Present : SupplyState

    data class Needed(val canDownload: Boolean, val sideloadPath: String, val bytes: Long) : SupplyState

    data class Importing(val done: Long, val total: Long) : SupplyState

    data class Downloading(val done: Long, val total: Long) : SupplyState

    data class Paused(val message: String, val canUseCellular: Boolean) : SupplyState

    data class Failed(val message: String) : SupplyState
}

sealed interface FetchResult {
    data object Done : FetchResult

    data class Paused(val message: String, val offerCellular: Boolean) : FetchResult

    data class Failed(val message: String) : FetchResult
}

interface ModelDownloader {
    suspend fun fetch(file: ModelFile, writer: ModelFileWriter, cellularAllowed: Boolean, onProgress: (done: Long, total: Long) -> Unit): FetchResult
}

class ModelSupply(
    private val target: File,
    private val sideload: File,
    private val pinned: ModelFile,
    private val downloader: ModelDownloader?,
    private val scope: CoroutineScope,
    private val freeSpace: () -> Long = { generateSequence(target.parentFile) { it.parentFile }.firstOrNull { it.exists() }?.usableSpace ?: 0L },
    private val spaceMargin: Long = 512L shl 20,
) {
    private val _state = MutableStateFlow<SupplyState>(SupplyState.Present)
    private var job: Job? = null
    private var generation = 0
    private var rejectedSideload: Pair<Long, Long>? = null

    val state: StateFlow<SupplyState> = _state

    init {
        evaluate()
    }

    @Synchronized
    fun refresh() {
        if (_state.value == SupplyState.Present || _state.value is SupplyState.Needed) evaluate()
    }

    @Synchronized
    fun start() {
        if (downloader == null) {
            evaluate()
            return
        }
        if (job?.isActive == true || present()) return
        _state.value = SupplyState.Downloading(0, pinned.bytes)
        launch { gen -> download(gen, cellularAllowed = false) }
    }

    @Synchronized
    fun useCellular() {
        if (downloader == null || job?.isActive == true || present()) return
        _state.value = SupplyState.Downloading(0, pinned.bytes)
        launch { gen -> download(gen, cellularAllowed = true) }
    }

    @Synchronized
    fun cancel() {
        generation++
        job?.cancel()
        if (!present()) _state.value = needed()
    }

    @Synchronized
    fun discard() {
        generation++
        job?.cancel()
        target.delete()
        _state.value = needed()
    }

    private fun present() = target.isFile && target.length() == pinned.bytes

    private fun evaluate() {
        if (present()) {
            _state.value = SupplyState.Present
            return
        }
        if (target.isFile) target.delete()
        if (sideload.isFile && sideloadFingerprint() != rejectedSideload) {
            _state.value = SupplyState.Importing(0, pinned.bytes)
            launch { gen -> importSideloaded(gen) }
        } else {
            _state.value = needed()
        }
    }

    private fun sideloadFingerprint() = sideload.length() to sideload.lastModified()

    private fun needed() = SupplyState.Needed(canDownload = downloader != null, sideloadPath = sideload.path, bytes = pinned.bytes)

    private fun launch(block: suspend (generation: Int) -> Unit) {
        val previous = job
        val mine = ++generation
        job = scope.launch {
            try {
                previous?.join()
                block(mine)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publish(mine, SupplyState.Failed(e.message ?: e::class.java.simpleName))
            }
        }
    }

    private fun publish(gen: Int, next: SupplyState) {
        synchronized(this) {
            if (gen == generation) _state.value = next
        }
    }

    private suspend fun importSideloaded(gen: Int) {
        if (present()) {
            publish(gen, SupplyState.Present)
            return
        }
        val missing = pinned.bytes + spaceMargin
        val free = freeSpace()
        if (free < missing) {
            publish(gen, SupplyState.Failed("not enough free space to import the model: need about ${missing / MIB} MB, have ${free / MIB} MB"))
            return
        }
        val fingerprint = sideloadFingerprint()
        var reported = 0L
        try {
            ModelFileWriter(target, pinned, partSuffix = ".import").use { writer ->
                ModelImport.copy(sideload, writer) { done ->
                    if (done - reported >= PROGRESS_STEP) {
                        reported = done
                        publish(gen, SupplyState.Importing(done, pinned.bytes))
                    }
                }
            }
        } catch (e: ModelCorrupt) {
            synchronized(this) { rejectedSideload = fingerprint }
            publish(gen, SupplyState.Failed("the file in the models folder failed its checksum, so it was not used (${e.message})"))
            return
        }
        sideload.delete()
        File(target.path + ".part").delete()
        publish(gen, SupplyState.Present)
    }

    private suspend fun download(gen: Int, cellularAllowed: Boolean) {
        if (present()) {
            publish(gen, SupplyState.Present)
            return
        }
        val engine = checkNotNull(downloader)
        var reported = 0L
        val result = ModelFileWriter(target, pinned).use { writer ->
            engine.fetch(pinned, writer, cellularAllowed) { done, total ->
                if (done - reported >= PROGRESS_STEP) {
                    reported = done
                    publish(gen, SupplyState.Downloading(done, total))
                }
            }
        }
        publish(
            gen,
            when (result) {
                FetchResult.Done -> SupplyState.Present
                is FetchResult.Paused -> SupplyState.Paused(result.message, result.offerCellular)
                is FetchResult.Failed -> SupplyState.Failed(result.message)
            },
        )
    }

    private companion object {
        const val MIB = 1L shl 20
        const val PROGRESS_STEP = 4L * MIB
    }
}
