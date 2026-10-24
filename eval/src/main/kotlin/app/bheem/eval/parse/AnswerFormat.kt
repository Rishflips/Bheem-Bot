package app.bheem.eval.parse

import app.bheem.core.action.ParseResult
import app.bheem.core.parse.LineParser

enum class AnswerFormat(val parse: (String) -> ParseResult) {
    JSON(OutputParser::parse),
    LINE(LineParser::parse),
}
