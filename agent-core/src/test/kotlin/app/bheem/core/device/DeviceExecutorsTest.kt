package app.bheem.core.device

import app.bheem.core.action.ProposedAction
import app.bheem.core.proposal.ExecutionResult
import app.bheem.core.proposal.HandOffLauncher
import app.bheem.core.proposal.HandOffRequest
import app.bheem.core.proposal.HandOffExecutors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DeviceExecutorsTest {
    private class FakeControls(var torchWorks: Boolean = true) : DeviceControls {
        val calls = mutableListOf<String>()

        override fun torch(on: Boolean): Boolean {
            calls += "torch:$on"
            return torchWorks
        }

        override fun volume(change: String): Boolean {
            calls += "volume:$change"
            return true
        }
    }

    private fun action(name: String, key: String, value: String) = ProposedAction(name, mapOf(key to value))

    @Test
    fun theTorchExecutorTurnsItOnAndUndoTurnsItBackOff() = runBlocking<Unit> {
        val controls = FakeControls()
        val executor = DeviceExecutors.flashlight(controls)
        val result = executor.execute(action("Flashlight", "state", "on"), "p1")
        assertTrue(result is ExecutionResult.Done)
        assertEquals("on", (result as ExecutionResult.Done).undoToken)
        assertTrue(executor.undo(action("Flashlight", "state", "on"), "on"))
        assertEquals(listOf("torch:true", "torch:false"), controls.calls)
    }

    @Test
    fun aTorchThatWillNotTurnOnIsAFailureWithAReason() = runBlocking<Unit> {
        val result = DeviceExecutors.flashlight(FakeControls(torchWorks = false)).execute(action("Flashlight", "state", "on"), "p1")
        assertTrue(result is ExecutionResult.Failed)
    }

    @Test
    fun volumeUpCanBeUndoneByGoingDownButMuteCannot() = runBlocking<Unit> {
        val controls = FakeControls()
        val executor = DeviceExecutors.volume(controls)
        assertEquals("down", (executor.execute(action("SetVolume", "change", "up"), "p1") as ExecutionResult.Done).undoToken)
        assertEquals(null, (executor.execute(action("SetVolume", "change", "mute"), "p2") as ExecutionResult.Done).undoToken)
        assertFalse(executor.undo(action("SetVolume", "change", "mute"), null))
    }

    @Test
    fun aTimerAndAnAppAreHandedToAnotherApp() = runBlocking<Unit> {
        val opened = mutableListOf<HandOffRequest>()
        val launcher = HandOffLauncher { opened += it; true }
        assertTrue(HandOffExecutors.timer(launcher).execute(action("SetTimer", "seconds", "600"), "p1") is ExecutionResult.Done)
        assertTrue(HandOffExecutors.openApp(launcher).execute(action("OpenApp", "app", "WhatsApp"), "p2") is ExecutionResult.Done)
        assertEquals(listOf<HandOffRequest>(HandOffRequest.Timer(600), HandOffRequest.OpenApp("WhatsApp")), opened)
        val none = HandOffLauncher { false }
        assertTrue(HandOffExecutors.openApp(none).execute(action("OpenApp", "app", "Nothing"), "p3") is ExecutionResult.Failed)
    }
}
