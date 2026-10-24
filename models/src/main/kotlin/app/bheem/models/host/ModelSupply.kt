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

    val state: StateFlow<SupplyState> = _state

    init {
        refresh()
    }

    @Synchronized
    fun refresh() {
        if (job?.isActive == true) return
        when {
            target.isFile -> _state.value = SupplyState.Present
            sideload.isFile -> {
                _state.value = SupplyState.Importing(0, pinned.bytes)
                launch { importSideloaded() }
            }
            else -> _state.value = needed()
        }
    }

    @Synchronized
    fun start() {
        if (downloader == null) {
            refresh()
            return
        }
        if (job?.isActive == true || target.isFile) return
        _state.value = SupplyState.Downloading(0, pinned.bytes)
        launch { download(cellularAllowed = false) }
    }

    @Synchronized
    fun useCellular() {
        if (downloader == null || job?.isActive == true || target.isFile) return
        _state.value = SupplyState.Downloading(0, pinned.bytes)
        launch { download(cellularAllowed = true) }
    }

    @Synchronized
    fun cancel() {
        job?.cancel()
        job = null
        if (!target.isFile) _state.value = needed()
    }

    private fun launch(block: suspend () -> Unit) {
        job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SupplyState.Failed(e.message ?: e::class.java.simpleName)
            }
        }
    }

    private fun needed() = SupplyState.Needed(canDownload = downloader != null, sideloadPath = sideload.path, bytes = pinned.bytes)

    private suspend fun importSideloaded() {
        val missing = pinned.bytes + spaceMargin
        val free = freeSpace()
        if (free < missing) {
            _state.value = SupplyState.Failed("not enough free space to import the model: need about ${missing / MIB} MB, have ${free / MIB} MB")
            return
        }
        var reported = 0L
        try {
            ModelFileWriter(target, pinned).use { writer ->
                ModelImport.copy(sideload, writer) { done ->
                    if (done - reported >= PROGRESS_STEP) {
                        reported = done
                        _state.value = SupplyState.Importing(done, pinned.bytes)
                    }
                }
            }
        } catch (e: ModelCorrupt) {
            _state.value = SupplyState.Failed("the file in the models folder failed its checksum, so it was not used (${e.message})")
            return
        }
        sideload.delete()
        _state.value = SupplyState.Present
    }

    private suspend fun download(cellularAllowed: Boolean) {
        val engine = checkNotNull(downloader)
        var reported = 0L
        val result = ModelFileWriter(target, pinned).use { writer ->
            engine.fetch(pinned, writer, cellularAllowed) { done, total ->
                if (done - reported >= PROGRESS_STEP) {
                    reported = done
                    _state.value = SupplyState.Downloading(done, total)
                }
            }
        }
        _state.value = when (result) {
            FetchResult.Done -> SupplyState.Present
            is FetchResult.Paused -> SupplyState.Paused(result.message, result.offerCellular)
            is FetchResult.Failed -> SupplyState.Failed(result.message)
        }
    }

    private companion object {
        const val MIB = 1L shl 20
        const val PROGRESS_STEP = 4L * MIB
    }
}
