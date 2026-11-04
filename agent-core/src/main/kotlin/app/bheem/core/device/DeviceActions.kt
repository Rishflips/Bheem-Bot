package app.bheem.core.device

import app.bheem.core.action.ProposedAction
import java.util.Locale

/**
 * Things the phone itself can be asked to do: the torch, a timer, an app, the volume. Built by code from the owner's own words, so the
 * model never supplies them; each becomes a card that waits for a tap. Only a message that is nothing but one of these matches.
 */
object DeviceActions {
    fun parse(text: String): ProposedAction? {
        val original = text.trim().trimEnd('.', '!', '?', ' ')
        val q = original.lowercase(Locale.ENGLISH).replace(Regex("""\s+"""), " ").removePrefix("please ").removeSuffix(" please")
        return torch(q) ?: timer(q) ?: volume(q) ?: openApp(original)
    }

    private val torchA = Regex("""^turn (on|off) (?:the )?(?:torch|flashlight|flash light)$""")
    private val torchB = Regex("""^(?:torch|flashlight|flash light) (on|off)$""")
    private val torchC = Regex("""^turn (?:the )?(?:torch|flashlight|flash light) (on|off)$""")

    private fun torch(q: String): ProposedAction? {
        val state = (torchA.matchEntire(q) ?: torchB.matchEntire(q) ?: torchC.matchEntire(q))?.groupValues?.get(1) ?: return null
        return ProposedAction("Flashlight", mapOf("state" to state))
    }

    private const val UNIT = "(second|sec|minute|min|hour|hr)s?"
    private val timerA = Regex("""^(?:set |start )?(?:a |an )?(\d{1,4}) $UNIT timer$""")
    private val timerB = Regex("""^(?:set |start )?(?:a )?timer (?:for|of) (\d{1,4}) $UNIT$""")

    private fun timer(q: String): ProposedAction? {
        val m = timerA.matchEntire(q) ?: timerB.matchEntire(q) ?: return null
        val amount = m.groupValues[1].toInt()
        val seconds = when (m.groupValues[2]) {
            "second", "sec" -> amount
            "minute", "min" -> amount * 60
            else -> amount * 3600
        }
        return if (seconds in 1..86_400) ProposedAction("SetTimer", mapOf("seconds" to seconds.toString())) else null
    }

    private val volumeA = Regex("""^(?:turn )?(?:the )?volume (up|down)$""")
    private val volumeB = Regex("""^turn (up|down) (?:the )?volume$""")
    private val muteAsk = Regex("""^mute(?: the)?(?: phone| volume| sound)?$""")

    private fun volume(q: String): ProposedAction? {
        (volumeA.matchEntire(q) ?: volumeB.matchEntire(q))?.let { return ProposedAction("SetVolume", mapOf("change" to it.groupValues[1])) }
        if (muteAsk.matches(q)) return ProposedAction("SetVolume", mapOf("change" to "mute"))
        return null
    }

    private val openAsk = Regex("""^(?:please )?(?:open|launch|start) ([\p{L}\p{N}][\p{L}\p{N} .&'-]{0,38}[\p{L}\p{N}])$""", RegexOption.IGNORE_CASE)
    private val notAnApp = Regex("""\b(and|then|also|route|directions|to the|about|for|to)\b""", RegexOption.IGNORE_CASE)
    private val startsLikeAPhrase = Regex("""^(a|an|the|my|this|that|new|some|one)\b""", RegexOption.IGNORE_CASE)

    private fun openApp(original: String): ProposedAction? {
        val name = openAsk.matchEntire(original.removePrefix("Please ").removePrefix("please "))?.groupValues?.get(1)?.trim() ?: return null
        if (notAnApp.containsMatchIn(name) || startsLikeAPhrase.containsMatchIn(name) || name.split(' ').size > 4) return null
        return ProposedAction("OpenApp", mapOf("app" to name))
    }
}
