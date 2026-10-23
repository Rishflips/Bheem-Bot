package app.bheem.eval.parse

enum class AnswerFormat(val parse: (String) -> ParseResult) {
    JSON(OutputParser::parse),
    LINE(LineParser::parse),
}
