package app.bheem.core.action

data class ProposedAction(val action: String, val args: Map<String, String>)

sealed interface ParseResult {
    data class Valid(val action: ProposedAction) : ParseResult
    data class Invalid(val reason: String) : ParseResult
}
