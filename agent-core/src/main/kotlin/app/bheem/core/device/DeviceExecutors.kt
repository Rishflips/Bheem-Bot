package app.bheem.core.device

import app.bheem.core.action.ProposedAction
import app.bheem.core.proposal.ActionExecutor
import app.bheem.core.proposal.ExecutionResult

/** What Bheem may do to the phone's own settings. The app implements it; nothing here reaches the network. */
interface DeviceControls {
    fun torch(on: Boolean): Boolean

    /** [change] is up, down or mute. */
    fun volume(change: String): Boolean
}

object DeviceExecutors {
    fun flashlight(controls: DeviceControls): ActionExecutor = object : ActionExecutor {
        override val handles = "Flashlight"

        override suspend fun execute(action: ProposedAction): ExecutionResult {
            val on = action.args.getValue("state") == "on"
            return if (controls.torch(on)) ExecutionResult.Done(action.args.getValue("state")) else ExecutionResult.Failed("This phone would not turn the torch ${if (on) "on" else "off"}.")
        }

        override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean = undoToken != null && controls.torch(undoToken != "on")
    }

    fun volume(controls: DeviceControls): ActionExecutor = object : ActionExecutor {
        override val handles = "SetVolume"

        override suspend fun execute(action: ProposedAction): ExecutionResult {
            val change = action.args.getValue("change")
            if (!controls.volume(change)) return ExecutionResult.Failed("This phone would not change the volume.")
            return ExecutionResult.Done(opposite[change])
        }

        override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean = undoToken != null && controls.volume(undoToken)

        private val opposite = mapOf("up" to "down", "down" to "up")
    }
}
