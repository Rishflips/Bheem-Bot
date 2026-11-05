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
        "RememberFact" to setOf("text"),
        "ForgetFact" to setOf("text"),
        "Flashlight" to setOf("state"),
        "SetTimer" to setOf("seconds"),
        "OpenApp" to setOf("app"),
        "SetVolume" to setOf("change"),
        "NoAction" to emptySet(),
    )

    /** Arguments that may be present but are not required. `replaces` names the confirmed fact this new one supersedes. */
    val optional: Map<String, Set<String>> = mapOf(
        "RememberFact" to setOf("replaces"),
    )

    val risky: Set<String> = setOf("OpenUpiPayment", "DraftMessage", "PlaceCall", "OpenRoute", "SetTimer", "OpenApp")

    /** Built by code from the owner's own words and notes; a line for one of these coming from the model is refused. */
    val codeOnly: Set<String> = setOf("PlaceCall", "OpenRoute", "RememberFact", "ForgetFact", "Flashlight", "SetTimer", "OpenApp", "SetVolume")

    val names: Set<String> get() = required.keys

    val offeredToModel: Set<String> get() = names - "OpenUpiPayment" - codeOnly
}
