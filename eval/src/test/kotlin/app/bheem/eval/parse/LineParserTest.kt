package app.bheem.eval.parse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LineParserTest {
    private fun valid(raw: String): ProposedAction = assertIs<ParseResult.Valid>(LineParser.parse(raw)).action

    private fun invalid(raw: String): String = assertIs<ParseResult.Invalid>(LineParser.parse(raw)).reason

    @Test
    fun mapsPositionalValuesToTheCatalogKeys() {
        assertEquals(
            ProposedAction("CreateReminder", mapOf("text" to "send the invoice", "time" to "2026-10-24T17:00")),
            valid("CreateReminder|send the invoice|2026-10-24T17:00"),
        )
        assertEquals(
            ProposedAction("LogExpense", mapOf("amount" to "90.00", "merchant" to "Chai Point", "direction" to "debit")),
            valid("LogExpense|90.00|Chai Point|debit"),
        )
        assertEquals(
            ProposedAction("DraftMessage", mapOf("channel" to "whatsapp", "to" to "Suresh", "text" to "on my way")),
            valid("DraftMessage|whatsapp|Suresh|on my way"),
        )
        assertEquals(
            ProposedAction("CreateCalendarEvent", mapOf("title" to "meeting with Ravi", "start" to "2026-10-25T09:00")),
            valid("CreateCalendarEvent|meeting with Ravi|2026-10-25T09:00"),
        )
    }

    @Test
    fun actionsWithoutArgsNeedNothingAfterTheName() {
        assertEquals(ProposedAction("NoAction", emptyMap()), valid("NoAction"))
        assertEquals(ProposedAction("NoAction", emptyMap()), valid("NoAction|"))
        assertEquals(ProposedAction("NoAction", emptyMap()), valid("NoAction - nothing to do here"))
    }

    @Test
    fun theLastValueKeepsAnyExtraSeparators() {
        assertEquals("buy milk | eggs", valid("SaveNote|buy milk | eggs").args["text"])
        assertEquals("see you | soon", valid("DraftMessage|sms|Arun|see you | soon").args["text"])
    }

    @Test
    fun spacesAroundValuesAreTrimmed() {
        assertEquals("call Arun", valid("  CreateReminder | call Arun | 2026-10-24T09:00  ").args["text"])
    }

    @Test
    fun anEchoedFieldLabelAtTheStartOfAValueIsDropped() {
        assertEquals(
            mapOf("amount" to "999.00", "merchant" to "uber@ybl", "direction" to "debit"),
            valid("LogExpense|amount:999.00|merchant: uber@ybl|direction: debit").args,
        )
        assertEquals(
            mapOf("channel" to "whatsapp", "to" to "Priya", "text" to "The meeting"),
            valid("DraftMessage|channel: whatsapp|to: Priya|text: The meeting").args,
        )
        assertEquals("5", valid("OpenUpiPayment|Arun|Amount : 5").args["amount"])
    }

    @Test
    fun onlyTheLabelOfThatPositionIsDropped() {
        assertEquals("note: buy milk", valid("CreateTask|note: buy milk").args["text"])
        assertEquals("to: Arun", valid("CreateTask|to: Arun").args["text"])
        assertEquals("Priya", valid("DraftMessage|whatsapp|to: Priya|hello").args["to"])
        assertEquals("whatsapp: hello", valid("DraftMessage|whatsapp|Priya|whatsapp: hello").args["text"])
    }

    @Test
    fun aValueThatIsOnlyALabelIsMissing() {
        assertTrue(invalid("CreateTask|text:").contains("missing arg text"))
    }

    @Test
    fun missingOrBlankValuesAreInvalid() {
        assertTrue(invalid("CreateReminder|call Arun").contains("missing arg time"))
        assertTrue(invalid("CreateReminder|call Arun| ").contains("missing arg time"))
        assertTrue(invalid("CreateTask").contains("missing arg text"))
    }

    @Test
    fun unknownActionsAreInvalid() {
        assertTrue(invalid("Teleport|now").contains("unknown action Teleport"))
    }

    @Test
    fun codeFencesBlankLinesAndThinkBlocksAreIgnored() {
        assertEquals("CreateTask", valid("```\nCreateTask|pay rent\n```").action)
        assertEquals("CreateTask", valid("```text\n\nCreateTask|pay rent\n").action)
        assertEquals("NoAction", valid("<think>maybe {x}</think>\nNoAction").action)
    }

    @Test
    fun onlyTheFirstLineCounts() {
        assertEquals("CreateTask", valid("CreateTask|pay rent\nNoAction").action)
    }

    @Test
    fun emptyAndUnfinishedThinkingAreInvalid() {
        assertIs<ParseResult.Invalid>(LineParser.parse(""))
        assertIs<ParseResult.Invalid>(LineParser.parse("  \n "))
        assertIs<ParseResult.Invalid>(LineParser.parse("<think>let me think about NoAction"))
    }
}
