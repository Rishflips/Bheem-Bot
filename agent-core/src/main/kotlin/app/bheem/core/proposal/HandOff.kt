package app.bheem.core.proposal

import app.bheem.core.action.ProposedAction
import java.time.LocalDateTime

sealed interface HandOffRequest {
    data class Calendar(val title: String, val start: LocalDateTime) : HandOffRequest

    data class Message(val channel: String, val to: String, val text: String) : HandOffRequest

    /** Opens the dialer with the number typed in; it never places the call. */
    data class Call(val name: String, val number: String) : HandOffRequest

    data class Route(val destination: String) : HandOffRequest

    /** Opens the clock app with a timer filled in. */
    data class Timer(val seconds: Int) : HandOffRequest

    /** Opens an installed app by its name. */
    data class OpenApp(val name: String) : HandOffRequest
}

fun interface HandOffLauncher {
    fun open(request: HandOffRequest): Boolean
}

object HandOffExecutors {
    fun calendar(launcher: HandOffLauncher): ActionExecutor =
        HandOffExecutor("CreateCalendarEvent", launcher, { a -> HandOffRequest.Calendar(a.args.getValue("title"), LocalDateTime.parse(a.args.getValue("start"))) }) {
            "No calendar app is available. Install or enable one, then try again."
        }

    fun message(launcher: HandOffLauncher): ActionExecutor =
        HandOffExecutor("DraftMessage", launcher, { a -> HandOffRequest.Message(a.args.getValue("channel"), a.args.getValue("to"), a.args.getValue("text")) }) { a ->
            if (a.args.getValue("channel") == "sms") {
                "No messaging app is available for SMS. Install or enable one, then try again."
            } else {
                "WhatsApp is not installed. Install it, then try again."
            }
        }

    fun call(launcher: HandOffLauncher): ActionExecutor =
        HandOffExecutor("PlaceCall", launcher, { a -> HandOffRequest.Call(a.args.getValue("name"), a.args.getValue("number")) }) {
            "No phone app is available. Install or enable one, then try again."
        }

    fun route(launcher: HandOffLauncher): ActionExecutor =
        HandOffExecutor("OpenRoute", launcher, { a -> HandOffRequest.Route(a.args.getValue("destination")) }) {
            "No maps app is available. Install or enable one, then try again."
        }

    fun timer(launcher: HandOffLauncher): ActionExecutor =
        HandOffExecutor("SetTimer", launcher, { a -> HandOffRequest.Timer(a.args.getValue("seconds").toInt()) }) {
            "No clock app is available. Install or enable one, then try again."
        }

    fun openApp(launcher: HandOffLauncher): ActionExecutor =
        HandOffExecutor("OpenApp", launcher, { a -> HandOffRequest.OpenApp(a.args.getValue("app")) }) { a ->
            "I could not find an app called ${a.args.getValue("app")} on this phone."
        }

    private class HandOffExecutor(
        override val handles: String,
        private val launcher: HandOffLauncher,
        private val request: (ProposedAction) -> HandOffRequest,
        private val missing: (ProposedAction) -> String,
    ) : ActionExecutor {
        override suspend fun execute(action: ProposedAction): ExecutionResult = ExecutionResult.Failed("a hand-off needs its proposal id")

        override suspend fun execute(action: ProposedAction, proposalId: String): ExecutionResult =
            try {
                if (launcher.open(request(action))) ExecutionResult.Done(null) else ExecutionResult.Failed(missing(action))
            } catch (e: SecurityException) {
                ExecutionResult.Failed("Android did not let Bheem open that app. Try again.")
            }

        override suspend fun undo(action: ProposedAction, undoToken: String?): Boolean = false
    }
}
