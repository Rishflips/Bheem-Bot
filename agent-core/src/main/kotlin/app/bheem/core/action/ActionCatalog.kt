package app.bheem.core.action

object ActionCatalog {
    val required: Map<String, Set<String>> = mapOf(
        "LogExpense" to setOf("amount", "merchant", "direction"),
        "CreateReminder" to setOf("text", "time"),
        "CreateTask" to setOf("text"),
        "CreateCalendarEvent" to setOf("title", "start"),
        "DraftMessage" to setOf("channel", "to", "text"),
        "OpenUpiPayment" to setOf("payee", "amount"),
        "SaveNote" to setOf("text"),
        "AnswerFromMemory" to setOf("query"),
        "PlaceCall" to setOf("name", "number"),
        "OpenRoute" to setOf("destination"),
        "NoAction" to emptySet(),
    )

    val risky: Set<String> = setOf("OpenUpiPayment", "DraftMessage", "PlaceCall", "OpenRoute")

    /** Built by code from the owner's own words and notes; a line for one of these coming from the model is refused. */
    val codeOnly: Set<String> = setOf("PlaceCall", "OpenRoute")

    val names: Set<String> get() = required.keys

    val offeredToModel: Set<String> get() = names - "OpenUpiPayment" - codeOnly
}
