package app.bheem.eval.parse

import app.bheem.core.action.ParseResult
import app.bheem.core.action.ProposedAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OutputParserTest {
    private val reminder = """{"action":"CreateReminder","args":{"text":"Arun ko call karna","time":"2026-10-24T09:00"}}"""

    private fun valid(raw: String): ProposedAction = assertIs<ParseResult.Valid>(OutputParser.parse(raw)).action

    @Test
    fun parsesPlainJson() {
        val a = valid(reminder)
        assertEquals("CreateReminder", a.action)
        assertEquals("2026-10-24T09:00", a.args["time"])
    }

    @Test
    fun stripsClosedThinkBlockAndCodeFences() {
        assertEquals("CreateReminder", valid("<think>user wants a reminder {not json}</think>\n```json\n$reminder\n```").action)
    }

    @Test
    fun truncatedThinkBlockIsInvalidNotACrash() {
        val result = OutputParser.parse("<think>let me think about {\"action\":\"NoAction\"")
        assertIs<ParseResult.Invalid>(result)
    }

    @Test
    fun ignoresProseAroundTheObject() {
        assertEquals("CreateReminder", valid("Sure! Here you go: $reminder Hope that helps.").action)
    }

    @Test
    fun firstValidObjectWinsWhenTwoAreEmitted() {
        val bad = """{"action":"Teleport","args":{}}"""
        assertEquals("CreateReminder", valid("$bad $reminder").action)
        assertEquals("CreateReminder", valid("$reminder {\"action\":\"NoAction\",\"args\":{}}").action)
    }

    @Test
    fun bracesInsideStringsDoNotBreakExtraction() {
        val raw = """{"action":"SaveNote","args":{"text":"use { and } carefully"}}"""
        assertEquals("use { and } carefully", valid(raw).args["text"])
    }

    @Test
    fun stringifiesNumbers() {
        val raw = """{"action":"OpenUpiPayment","args":{"payee":"Arun","amount":450}}"""
        assertEquals("450", valid(raw).args["amount"])
    }

    @Test
    fun noActionNeedsNoArgs() {
        assertEquals("NoAction", valid("""{"action":"NoAction"}""").action)
    }

    @Test
    fun rejectsUnknownActionMissingArgsBlankArgsAndFlatObjects() {
        assertTrue((OutputParser.parse("""{"action":"Teleport","args":{}}""") as ParseResult.Invalid).reason.contains("unknown action"))
        assertTrue((OutputParser.parse("""{"action":"CreateReminder","args":{"text":"x"}}""") as ParseResult.Invalid).reason.contains("missing arg time"))
        assertIs<ParseResult.Invalid>(OutputParser.parse("""{"action":"CreateReminder","args":{"text":" ","time":"t"}}"""))
        assertIs<ParseResult.Invalid>(OutputParser.parse("""{"action":"CreateReminder","text":"x","time":"t"}"""))
    }

    @Test
    fun emptyAndGarbageInputIsInvalid() {
        assertIs<ParseResult.Invalid>(OutputParser.parse(""))
        assertIs<ParseResult.Invalid>(OutputParser.parse("I cannot help with that."))
        assertIs<ParseResult.Invalid>(OutputParser.parse("{\"action\": \"NoAction\""))
    }
}
