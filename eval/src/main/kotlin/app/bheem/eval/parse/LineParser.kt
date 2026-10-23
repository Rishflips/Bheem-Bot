package app.bheem.eval.parse

import app.bheem.eval.catalog.ActionCatalog

object LineParser {
    fun parse(raw: String): ParseResult {
        val line = OutputParser.withoutThinking(raw).lines()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("```") }
            ?: return ParseResult.Invalid("no answer line")
        val name = line.substringBefore('|').trim().takeWhile { !it.isWhitespace() }
        val keys = ActionCatalog.required[name]?.toList() ?: return ParseResult.Invalid("unknown action $name")
        val values = if ('|' in line) line.substringAfter('|').split('|', limit = keys.size) else emptyList()
        val args = mutableMapOf<String, String>()
        for ((i, key) in keys.withIndex()) {
            val value = withoutEchoedLabel(withoutEchoedBrackets(values.getOrNull(i).orEmpty().trim()), key)
            if (value.isEmpty()) return ParseResult.Invalid("missing arg $key")
            args[key] = value
        }
        return ParseResult.Valid(ProposedAction(name, args))
    }

    private fun withoutEchoedBrackets(value: String): String {
        val inner = value.removePrefix("<").removeSuffix(">")
        val wrapped = value.length >= 2 && value.startsWith('<') && value.endsWith('>')
        return if (wrapped && inner.none { it == '<' || it == '>' }) inner else value
    }

    private fun withoutEchoedLabel(value: String, key: String): String =
        value.trim().replace(Regex("^${Regex.escape(key)}\\s*:", RegexOption.IGNORE_CASE), "").trim()
}
