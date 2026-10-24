package app.bheem.models.host

import app.bheem.models.BackendKind
import app.bheem.models.Completion
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.ModelSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ModelHostTest {
    private val good = "a".repeat(64)
    private val model = RegisteredModel("m", "llm", "litert-lm", "Apache-2.0", 6, listOf(ModelFile("m.litertlm", good, 100)))

    private class FakeEngine(val failOn: Set<BackendKind> = emptySet(), val loadDelay: Long = 0) : LlmEngine {
        val loads = mutableListOf<BackendKind>()
        var unloads = 0
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun load(spec: ModelSpec): Long {
            loads += spec.backend
            gate?.await()
            if (loadDelay > 0) delay(loadDelay)
            if (spec.backend in failOn) error("cannot load on ${spec.backend}")
            return 1234
        }

        override suspend fun complete(request: GenerationRequest) = Completion("x", 1, 1, 1)

        override suspend fun unload() {
            unloads++
        }
    }

    private class FakeFacts(var present: Boolean = true, var size: Long = 100, var modified: Long = 5) : FileFacts {
        override fun exists(path: String) = present
        override fun size(path: String) = size
        override fun lastModified(path: String) = modified
    }

    private class CountingVerifier(var hash: String, val startsSeen: () -> Int = { 0 }) : FileVerifier {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var startsAtCall = -1
        override suspend fun sha256(path: String): String {
            calls++
            startsAtCall = startsSeen()
            gate?.await()
            return hash
        }
    }

    private class Hooks : LoadHooks {
        var starts = 0
        var ends = 0
        var startsWhenVerifying = -1
        override fun loadStarted() { starts++ }
        override fun loadFinished() { ends++ }
    }

    private class Env(
        val scope: TestScope,
        val engine: FakeEngine = FakeEngine(),
        val facts: FakeFacts = FakeFacts(),
        val verifier: CountingVerifier = CountingVerifier("a".repeat(64)),
        val store: InMemoryHostStateStore = InMemoryHostStateStore(),
        val hooks: Hooks = Hooks(),
        var capability: Capability = Capability.Full,
        val version: String = "0.18.1",
        var appVersion: Int = 1,
    )

    private fun TestScope.host(env: Env, model: RegisteredModel = this@ModelHostTest.model) =
        DefaultModelHost(model, "/models/m.litertlm", env.engine, env.facts, env.verifier, env.store, env.version, env.appVersion, { env.capability }, backgroundScope, env.hooks)

    @Test
    fun aMissingFileIsAbsentAndNothingIsLoaded() = runTest {
        val env = Env(this, facts = FakeFacts(present = false))
        val host = host(env)
        assertFailsWith<ModelUnavailable> { host.acquire() }
        assertEquals(ModelState.Absent, host.state.value)
        assertEquals(emptyList(), env.engine.loads)
    }

    @Test
    fun theFirstUseVerifiesTheHashThenLoadsOnGpu() = runTest {
        val env = Env(this)
        val host = host(env)
        host.acquire()
        assertEquals(1, env.verifier.calls)
        assertEquals(listOf(BackendKind.GPU), env.engine.loads)
        assertEquals(ModelState.Ready(BackendKind.GPU), host.state.value)
        assertEquals(1, env.hooks.starts)
        assertEquals(1, env.hooks.ends)
        assertEquals(1234L, env.store.lastLoadMillis())
    }

    @Test
    fun aTamperedFileIsRefusedBeforeTheEngineIsTouched() = runTest {
        val env = Env(this, verifier = CountingVerifier("b".repeat(64)))
        val host = host(env)
        val error = assertFailsWith<ModelUnavailable> { host.acquire() }
        assertTrue("checksum" in error.message.orEmpty())
        assertEquals(emptyList(), env.engine.loads)
        assertIs<ModelState.Failed>(host.state.value)
        assertNull(env.store.verified())
    }

    @Test
    fun aLaterLaunchSkipsTheHashWhenSizeAndTimeAreUnchanged() = runTest {
        val env = Env(this)
        host(env).acquire()
        assertEquals(1, env.verifier.calls)
        host(env).acquire()
        assertEquals(1, env.verifier.calls)
        env.facts.modified = 6
        host(env).acquire()
        assertEquals(2, env.verifier.calls)
    }

    @Test
    fun aGpuFailureFallsBackToCpuAndIsRememberedForThisEngineVersionOnly() = runTest {
        val env = Env(this, engine = FakeEngine(failOn = setOf(BackendKind.GPU)))
        host(env).acquire()
        assertEquals(listOf(BackendKind.GPU, BackendKind.CPU), env.engine.loads)
        assertEquals("0.18.1", env.store.gpuFailedVersion())
        val second = FakeEngine(failOn = setOf(BackendKind.GPU))
        val next = Env(this, engine = second, store = env.store)
        host(next).acquire()
        assertEquals(listOf(BackendKind.CPU), second.loads)
        val upgraded = FakeEngine()
        host(Env(this, engine = upgraded, store = env.store, version = "0.19.0")).acquire()
        assertEquals(listOf(BackendKind.GPU), upgraded.loads)
    }

    @Test
    fun whenBothBackendsFailTheHostFailsWithTheReasonAndTriesAgainNextTime() = runTest {
        val env = Env(this, engine = FakeEngine(failOn = setOf(BackendKind.GPU, BackendKind.CPU)))
        val host = host(env)
        assertFailsWith<ModelUnavailable> { host.acquire() }
        assertIs<ModelState.Failed>(host.state.value)
        assertFailsWith<ModelUnavailable> { host.acquire() }
        assertEquals(listOf(BackendKind.GPU, BackendKind.CPU, BackendKind.GPU, BackendKind.CPU), env.engine.loads)
        assertNull(env.store.gpuFailedVersion())
    }

    @Test
    fun twoCallersAtOnceLoadOnceAndShareTheEngine() = runTest {
        val env = Env(this, engine = FakeEngine(loadDelay = 500))
        val host = host(env)
        val a = async { host.acquire() }
        val b = async { host.acquire() }
        a.await()
        b.await()
        assertEquals(1, env.engine.loads.size)
    }

    @Test
    fun theEngineStaysLoadedWhileAnyoneUsesItThenUnloadsAfterTheIdleTime() = runTest {
        val env = Env(this)
        val host = host(env)
        host.acquire()
        host.acquire()
        host.release()
        advanceTimeBy(DefaultModelHost.IDLE_MILLIS + 1000)
        runCurrent()
        assertEquals(0, env.engine.unloads)
        host.release()
        advanceTimeBy(DefaultModelHost.IDLE_MILLIS - 1000)
        runCurrent()
        assertEquals(0, env.engine.unloads)
        advanceTimeBy(2000)
        runCurrent()
        assertEquals(1, env.engine.unloads)
        assertEquals(ModelState.Unloaded, host.state.value)
    }

    @Test
    fun anAcquireDuringTheIdleWaitKeepsTheEngineLoaded() = runTest {
        val env = Env(this)
        val host = host(env)
        host.acquire()
        host.release()
        advanceTimeBy(DefaultModelHost.IDLE_MILLIS - 1000)
        host.acquire()
        advanceTimeBy(DefaultModelHost.IDLE_MILLIS * 2)
        runCurrent()
        assertEquals(0, env.engine.unloads)
        assertEquals(1, env.engine.loads.size)
    }

    @Test
    fun aCancelledLoadUnloadsWhatWasStartedAndLeavesTheHostUsable() = runTest {
        val env = Env(this, engine = FakeEngine().also { it.gate = CompletableDeferred() })
        val host = host(env)
        val job = launch { host.acquire() }
        runCurrent()
        assertEquals(ModelState.Loading(BackendKind.GPU), host.state.value)
        job.cancel()
        runCurrent()
        assertEquals(1, env.engine.unloads)
        assertEquals(ModelState.Unloaded, host.state.value)
        assertEquals(1, env.hooks.ends)
        env.engine.gate = null
        host.acquire()
        assertEquals(ModelState.Ready(BackendKind.GPU), host.state.value)
    }

    @Test
    fun theGovernorStopsLoadingUnderPressureAndPicksCpuWhenWarm() = runTest {
        val env = Env(this, capability = Capability.RulesOnly)
        val host = host(env)
        val refused = assertFailsWith<ModelUnavailable> { host.acquire() }
        assertTrue("battery or heat" in refused.message.orEmpty())
        assertEquals(emptyList(), env.engine.loads)
        env.capability = Capability.CpuOnly
        host.acquire()
        assertEquals(listOf(BackendKind.CPU), env.engine.loads)
    }

    @Test
    fun trimUnloadsAtOnceWhenIdleAndAfterTheLastReleaseWhenBusy() = runTest {
        val env = Env(this)
        val host = host(env)
        host.acquire()
        host.trim()
        assertEquals(0, env.engine.unloads)
        host.release()
        runCurrent()
        assertEquals(1, env.engine.unloads)
        host.acquire()
        host.release()
        host.trim()
        runCurrent()
        assertEquals(2, env.engine.unloads)
    }

    @Test
    fun releasingMoreOftenThanAcquiringIsAnError() = runTest {
        val env = Env(this)
        val host = host(env)
        assertFailsWith<IllegalStateException> { host.release() }
    }

    @Test
    fun aCancelledAcquireThatNeverLoadedDoesNotCountAsUse() = runTest {
        val env = Env(this, engine = FakeEngine(loadDelay = 1000))
        val host = host(env)
        val job = launch { try { host.acquire() } catch (e: CancellationException) { throw e } }
        runCurrent()
        job.cancel()
        runCurrent()
        host.acquire()
        host.release()
        advanceTimeBy(DefaultModelHost.IDLE_MILLIS + 1)
        runCurrent()
        assertEquals(ModelState.Unloaded, host.state.value)
    }

    @Test
    fun aTrimWhileNothingIsLoadedDoesNotMakeTheNextLoadShortLived() = runTest {
        val env = Env(this)
        val host = host(env)
        host.trim()
        host.acquire()
        host.release()
        advanceTimeBy(DefaultModelHost.IDLE_MILLIS / 2)
        runCurrent()
        assertEquals(0, env.engine.unloads)
        assertEquals(ModelState.Ready(BackendKind.GPU), host.state.value)
    }

    @Test
    fun shutdownUnloadsAtOnceEvenWhileInUseAndTheHostCanLoadAgain() = runTest {
        val env = Env(this)
        val host = host(env)
        host.acquire()
        host.shutdown()
        assertEquals(1, env.engine.unloads)
        assertEquals(ModelState.Unloaded, host.state.value)
        host.acquire()
        assertEquals(2, env.engine.loads.size)
        host.release()
    }

    @Test
    fun theLoadHookStartsBeforeTheHashSoABackgroundedHashIsProtectedToo() = runTest {
        val hooks = Hooks()
        val verifier = CountingVerifier("a".repeat(64)) { hooks.starts }
        val env = Env(this, verifier = verifier, hooks = hooks)
        host(env).acquire()
        assertEquals(1, verifier.startsAtCall)
        assertEquals(1, hooks.ends)
    }

    @Test
    fun aCancelDuringTheHashResetsTheStateAndEndsTheHook() = runTest {
        val verifier = CountingVerifier("a".repeat(64))
        verifier.gate = CompletableDeferred()
        val env = Env(this, verifier = verifier)
        val host = host(env)
        val job = launch { host.acquire() }
        runCurrent()
        assertEquals(ModelState.Verifying, host.state.value)
        job.cancel()
        runCurrent()
        assertEquals(ModelState.Unloaded, host.state.value)
        assertEquals(1, env.hooks.ends)
        assertEquals(emptyList(), env.engine.loads)
    }

    @Test
    fun aFileOfTheWrongSizeIsRefusedWithoutHashing() = runTest {
        val env = Env(this, facts = FakeFacts(size = 99))
        val error = assertFailsWith<ModelUnavailable> { host(env).acquire() }
        assertTrue("wrong size" in error.message.orEmpty())
        assertEquals(0, env.verifier.calls)
        assertEquals(emptyList(), env.engine.loads)
    }

    @Test
    fun aChangedPinnedHashOrANewAppVersionMakesTheCachedCheckStale() = runTest {
        val env = Env(this)
        host(env).acquire()
        assertEquals(1, env.verifier.calls)
        val repinned = model.copy(files = listOf(ModelFile("m.litertlm", "b".repeat(64), 100)))
        val verifier = CountingVerifier("b".repeat(64))
        host(Env(this, engine = FakeEngine(), verifier = verifier, store = env.store), repinned).acquire()
        assertEquals(1, verifier.calls)
    }

    @Test
    fun anAppUpdateMakesTheCachedCheckStale() = runTest {
        val first = Env(this)
        host(first).acquire()
        val same = Env(this, store = first.store)
        host(same).acquire()
        assertEquals(0, same.verifier.calls)
        val upgraded = Env(this, store = first.store, appVersion = 2)
        host(upgraded).acquire()
        assertEquals(1, upgraded.verifier.calls)
    }

    @Test
    fun aSecondReleaseRestartsTheIdleTimeInsteadOfLettingTheFirstTimerFire() = runTest {
        val env = Env(this)
        val host = host(env)
        host.acquire()
        host.release()
        advanceTimeBy(100_000)
        host.acquire()
        host.release()
        advanceTimeBy(100_000)
        runCurrent()
        assertEquals(0, env.engine.unloads)
        advanceTimeBy(81_000)
        runCurrent()
        assertEquals(1, env.engine.unloads)
    }

    @Test
    fun aTrimThatArrivesBeforeALaterAcquireStillLeavesTheEngineLoadedWhileItIsInUse() = runTest {
        val env = Env(this)
        val host = host(env)
        host.acquire()
        host.release()
        host.trim()
        host.acquire()
        runCurrent()
        assertEquals(0, env.engine.unloads)
        host.release()
        runCurrent()
        assertEquals(1, env.engine.unloads)
    }

    @Test
    fun aTrimDuringALoadUnloadsOnceTheLastUserLetsGo() = runTest {
        val env = Env(this, engine = FakeEngine(loadDelay = 500))
        val host = host(env)
        val job = async { host.acquire() }
        runCurrent()
        host.trim()
        job.await()
        host.release()
        runCurrent()
        assertEquals(1, env.engine.unloads)
    }

    @Test
    fun aReleaseThatArrivesAfterShutdownIsIgnored() = runTest {
        val env = Env(this)
        val host = host(env)
        host.acquire()
        host.shutdown()
        host.release()
        host.acquire()
        host.release()
        advanceTimeBy(DefaultModelHost.IDLE_MILLIS + 1)
        runCurrent()
        assertEquals(ModelState.Unloaded, host.state.value)
    }

    @Test
    fun aGpuFailureIsRememberedOnlyWhenTheCpuThenWorks() = runTest {
        val gpuOnly = Env(this, engine = FakeEngine(failOn = setOf(BackendKind.GPU)))
        host(gpuOnly).acquire()
        assertEquals("0.18.1", gpuOnly.store.gpuFailedVersion())
    }

    @Test
    fun abortingALoadCancelsItMarksTheStateFailedAndEndsTheHook() = runTest {
        val env = Env(this, engine = FakeEngine().also { it.gate = CompletableDeferred() })
        val host = host(env)
        val job = launch { host.acquire() }
        runCurrent()
        host.abortLoad("the model took too long to load")
        runCurrent()
        assertTrue(job.isCancelled)
        assertEquals(ModelState.Failed("the model took too long to load"), host.state.value)
        assertEquals(1, env.engine.unloads)
        assertEquals(1, env.hooks.ends)
    }

    @Test
    fun aPlainCancelAfterAnEarlierAbortIsNotReportedWithTheOldReason() = runTest {
        val env = Env(this, engine = FakeEngine().also { it.gate = CompletableDeferred() })
        val host = host(env)
        val first = launch { host.acquire() }
        runCurrent()
        host.abortLoad("the model took too long to load")
        runCurrent()
        assertTrue(first.isCancelled)
        env.engine.gate = CompletableDeferred()
        val second = launch { host.acquire() }
        runCurrent()
        second.cancel()
        runCurrent()
        assertEquals(ModelState.Unloaded, host.state.value)
    }

    @Test
    fun aTrimDuringAFailedLoadDoesNotShortenTheNextSuccessfulUse() = runTest {
        val env = Env(this, engine = FakeEngine(failOn = setOf(BackendKind.GPU, BackendKind.CPU), loadDelay = 100))
        val host = host(env)
        val failing = async { runCatching { host.acquire() } }
        runCurrent()
        host.trim()
        failing.await()
        val ok = Env(this, store = env.store)
        val second = host(ok)
        second.acquire()
        second.release()
        advanceTimeBy(DefaultModelHost.IDLE_MILLIS / 2)
        runCurrent()
        assertEquals(0, ok.engine.unloads)
    }

    @Test
    fun aStateStoreThatThrowsWhileSavingDoesNotLeaveTheHostStuck() = runTest {
        val broken = object : HostStateStore by InMemoryHostStateStore() {
            override fun saveVerified(file: VerifiedFile) = error("disk full")
        }
        val env = Env(this)
        val host = DefaultModelHost(model, "/models/m.litertlm", env.engine, env.facts, env.verifier, broken, "0.18.1", 1, { Capability.Full }, backgroundScope, env.hooks)
        assertFailsWith<Exception> { host.acquire() }
        assertIs<ModelState.Failed>(host.state.value)
        assertEquals(1, env.hooks.ends)
    }
}
