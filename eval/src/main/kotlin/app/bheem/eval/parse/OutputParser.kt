package app.bheem.eval.parse

import app.bheem.core.action.ActionCatalog
import app.bheem.core.action.ParseResult
import app.bheem.core.action.ProposedAction
import app.bheem.core.parse.ThinkingFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object OutputParser {
    fun parse(raw: String): ParseResult {
        val cleaned = ThinkingFilter.strip(raw)
            .replace("```json", "")
            .replace("```", "")
        val candidates = extractObjects(cleaned)
        if (candidates.isEmpty()) return ParseResult.Invalid("no JSON object")
        var lastReason = "no valid action object"
        for (candidate in candidates) {
            when (val result = validate(candidate)) {
                is ParseResult.Valid -> return result
                is ParseResult.Invalid -> lastReason = result.reason
            }
        }
        return ParseResult.Invalid(lastReason)
    }

    private fun extractObjects(text: String): List<String> {
        val found = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false
        for ((i, ch) in text.withIndex()) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> if (depth > 0) inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> if (depth > 0) {
                    depth--
                    if (depth == 0) found += text.substring(start, i + 1)
                }
            }
        }
        return found
    }

    private fun validate(candidate: String): ParseResult {
        val element = try {
            Json.parseToJsonElement(candidate)
        } catch (e: Exception) {
            return ParseResult.Invalid("not JSON")
        }
        val obj = element as? JsonObject ?: return ParseResult.Invalid("not an object")
        val name = (obj["action"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return ParseResult.Invalid("no action")
        val required = ActionCatalog.required[name] ?: return ParseResult.Invalid("unknown action $name")
        val argsObject = obj["args"] as? JsonObject
        val args = argsObject?.mapValues { (_, v) -> if (v is JsonPrimitive) v.content else v.toString() } ?: emptyMap()
        for (key in required) {
            if (args[key].isNullOrBlank()) return ParseResult.Invalid("missing arg $key")
        }
        return ParseResult.Valid(ProposedAction(name, args))
    }
}
