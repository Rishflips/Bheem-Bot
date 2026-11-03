package app.bheem.core.parse

import app.bheem.core.action.ParseResult

/** Turns what a model wrote into a [ParseResult]. Which parser a model gets is decided by its profile. */
fun interface AnswerParser {
    fun parse(raw: String): ParseResult
}

object AnswerFormats {
    val LINE = AnswerParser(LineParser::parse)

    private val known = mapOf("line" to LINE)

    fun byId(id: String): AnswerParser = known[id] ?: throw IllegalArgumentException("unknown answer format $id; known: ${known.keys.sorted()}")
}
