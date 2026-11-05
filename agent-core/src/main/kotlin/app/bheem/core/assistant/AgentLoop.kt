package app.bheem.core.assistant

import app.bheem.core.memory.Citation
import app.bheem.models.GenerationRequest
import app.bheem.models.LlmEngine
import app.bheem.models.host.ModelHost
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class ToolResult(val text: String, val citations: List<Citation>)

/** The two read-only tools the chat model may ask for. Both search the owner's own records; neither can change them. */
interface AgentTools {
    fun searchMemory(query: String): ToolResult

    fun schedule(whenWords: String): ToolResult
}

data class AgentReply(val text: String, val offer: String?, val citations: List<Citation>, val fact: String? = null)

/**
 * One owner message, answered in the persona's voice. The model may ask for one read tool, whose result is handed back for the
 * final answer; with [grounding] the tool step is skipped and the supplied result is used. It never executes a write action.
 */
class AgentLoop(private val host: ModelHost, private val tools: AgentTools) {
    suspend fun reply(
        history: List<Turn>,
        text: String,
        nowLine: String?,
        memoryBlock: String?,
        grounding: ToolResult? = null,
        onPartial: (String) -> Unit = {},
    ): AgentReply {
        val engine = host.acquire()
        try {
            val message = ChatText.message(history, text, nowLine, memoryBlock)
            val (raw, citations) = when {
                grounding != null -> stripToolLines(generate(engine, message + resultSuffix(SEARCH_MEMORY, grounding.text), text, onPartial)) to grounding.citations
                else -> {
                    val first = generate(engine, message, text, onPartial)
                    val call = parseTool(first)
                    if (call == null) {
                        stripToolLines(first) to emptyList()
                    } else {
                        val result = runTool(call)
                        val answer = generate(engine, message + resultSuffix(call.name, result.text), text, onPartial)
                        stripToolLines(answer) to result.citations
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            val (withoutFact, fact) = ChatText.splitFact(raw)
            val (shown, offer) = ChatText.splitOffer(withoutFact)
            val answer = ChatText.clean(shown)
            if (answer == ChatText.NOT_DONE) return AgentReply(answer, null, citations, null)
            return AgentReply(if (ChatText.needsCaution(text)) "$answer ${ChatText.CAUTION}" else answer, offer, citations, fact)
        } finally {
            host.release()
        }
    }

    private suspend fun generate(engine: LlmEngine, user: String, ownerText: String, onPartial: (String) -> Unit): String {
        val request = GenerationRequest(
            ChatPrompt.SYSTEM, user, maxOutputTokens = if (wantsMore(ownerText)) LONG_OUTPUT_TOKENS else MAX_OUTPUT_TOKENS,
            temperature = TEMPERATURE, topK = TOP_K, onText = { onPartial(partial(it)) },
        )
        val completion = engine.complete(request)
        currentCoroutineContext().ensureActive()
        return completion.text
    }

    private fun runTool(call: ToolCall): ToolResult = when (call.name) {
        SEARCH_MEMORY -> tools.searchMemory(call.argument)
        else -> tools.schedule(call.argument)
    }

    private fun parseTool(raw: String): ToolCall? {
        val line = raw.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: return null
        if (!line.startsWith(TOOL, ignoreCase = true)) return null
        val parts = line.split("|", limit = 3)
        if (parts.size < 3) return null
        val name = parts[1].trim().lowercase()
        if (name != SEARCH_MEMORY && name != SCHEDULE) return null
        val argument = parts[2].trim().take(MAX_ARGUMENT)
        if (argument.isEmpty()) return null
        return ToolCall(name, argument)
    }

    private fun stripToolLines(raw: String): String =
        raw.lineSequence().filterNot { it.trimStart().startsWith(TOOL, ignoreCase = true) }.joinToString("\n")

    private fun partial(raw: String): String = ChatText.partial(hideTools(raw))

    private fun hideTools(raw: String): String {
        val last = raw.takeLastWhile { !it.isWhitespace() }
        val withoutFragment =
            if (last.isNotEmpty() && last.length < TOOL.length && TOOL.startsWith(last, ignoreCase = true)) raw.dropLast(last.length).trimEnd()
            else raw
        return stripToolLines(withoutFragment)
    }

    private fun resultSuffix(name: String, text: String): String =
        "\n\nResult of $name (the owner's own records):\n$text\n\nAnswer the owner now from this result and what you know about them. Do not call a tool."

    private fun wantsMore(text: String) = MORE.containsMatchIn(text)

    private data class ToolCall(val name: String, val argument: String)

    private companion object {
        const val SEARCH_MEMORY = "search_memory"
        const val SCHEDULE = "schedule"
        const val TOOL = "Tool|"
        const val MAX_ARGUMENT = 200
        const val MAX_OUTPUT_TOKENS = 160
        const val LONG_OUTPUT_TOKENS = 320
        const val TEMPERATURE = 0.6
        const val TOP_K = 40
        val MORE = Regex("""\b(tell me more|explain|in detail|elaborate|more detail|go on|longer)\b""", RegexOption.IGNORE_CASE)
    }
}
