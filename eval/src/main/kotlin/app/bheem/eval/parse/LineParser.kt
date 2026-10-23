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
            val value = values.getOrNull(i)?.trim().orEmpty()
            if (value.isEmpty()) return ParseResult.Invalid("missing arg $key")
            args[key] = value
        }
        return ParseResult.Valid(ProposedAction(name, args))
    }
}
