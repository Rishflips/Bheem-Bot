package app.bheem.core.parse

import app.bheem.core.action.ActionCatalog
import app.bheem.core.action.ParseResult
import app.bheem.core.action.ProposedAction

object LineParser {
    fun parse(raw: String): ParseResult {
        val line = ThinkingFilter.strip(raw).lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("```") }
            ?: return ParseResult.Invalid("no answer line")
        val name = line.substringBefore('|').trim().takeWhile { !it.isWhitespace() }
        val keys = ActionCatalog.required[name]?.toList() ?: return ParseResult.Invalid("unknown action $name")
        val values = if ('|' in line) line.substringAfter('|').split('|', limit = keys.size) else emptyList()
        val args = mutableMapOf<String, String>()
        for ((i, key) in keys.withIndex()) {
            val raw = withoutTrailingNoise(values.getOrNull(i).orEmpty().trim())
            val value = withoutEchoedLabel(withoutEchoedBrackets(raw), key)
            if (value.isEmpty()) return ParseResult.Invalid("missing arg $key")
            args[key] = value
        }
        return ParseResult.Valid(ProposedAction(name, args))
    }

    private val trailingNoise = Regex("""\s*\|\s*(\d{4}-\d{2}-\d{2}([T ]\d{2}:\d{2}(:\d{2})?)?|\d{1,3})\s*$""")

    private fun withoutTrailingNoise(value: String): String {
        var out = value
        while (trailingNoise.containsMatchIn(out)) out = out.replace(trailingNoise, "")
        return out
    }

    private fun withoutEchoedBrackets(value: String): String {
        val inner = value.removePrefix("<").removeSuffix(">")
        val wrapped = value.length >= 2 && value.startsWith('<') && value.endsWith('>')
        return if (wrapped && inner.none { it == '<' || it == '>' }) inner else value
    }

    private fun withoutEchoedLabel(value: String, key: String): String =
        value.trim().replace(Regex("^${Regex.escape(key)}\\s*:", RegexOption.IGNORE_CASE), "").trim()
}
