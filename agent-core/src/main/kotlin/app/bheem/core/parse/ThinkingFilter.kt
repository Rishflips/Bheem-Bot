package app.bheem.core.parse

object ThinkingFilter {
    private val closedThink = Regex("(?s)<think>.*?</think>")
    private val openThink = Regex("(?s)<think>.*$")

    fun strip(raw: String): String = openThink.replace(closedThink.replace(raw, ""), "")
}
