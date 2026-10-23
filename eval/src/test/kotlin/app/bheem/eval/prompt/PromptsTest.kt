package app.bheem.eval.prompt

import app.bheem.eval.catalog.ActionCatalog
import app.bheem.eval.data.Dataset
import app.bheem.eval.gen.AdversarialGenerator
import app.bheem.eval.gen.CommandGenerator
import app.bheem.eval.gen.NotificationGenerator
import app.bheem.eval.parse.AnswerFormat
import app.bheem.eval.parse.OutputParser
import app.bheem.eval.parse.ParseResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PromptsTest {
    private val extractionActions = setOf("LogExpense", "CreateReminder", "NoAction")
    private val v1CommandChars = 1454
    private val v1NotificationChars = 1706

    // Measured with the Gemma 4 E2B tokenizer on the phone: 748 chars of rules were 243 tokens, 612 chars were 184 tokens,
    // and notification texts cost up to 0.45 tokens per char. The first token arrives in steps of 128 prompt tokens.
    private val systemTokensPerChar = 0.325
    private val userTokensPerChar = 0.45
    private val twoChunkTokens = 256

    private fun fewShotOutputs(prompt: String) =
        Regex("""Output: (\{.*\})""").findAll(prompt).map { it.groupValues[1] }.toList()

    @Test
    fun commandPromptNamesEveryActionAndTheClock() {
        for (name in ActionCatalog.names) assertContains(Prompts.commandSystem(), name)
        assertContains(Prompts.commandSystem(), Prompts.NOW_TEXT)
    }

    @Test
    fun theMemoryActionDescribesWhatItsQueryIs() {
        assertTrue(Regex("""AnswerFromMemory: query \([^)]{10,}\)""").containsMatchIn(Prompts.commandSystem()))
    }

    @Test
    fun untrustedPromptOffersOnlyTheExtractionActions() {
        val prompt = Prompts.notificationSystem()
        for (name in extractionActions) assertContains(prompt, name)
        for (name in ActionCatalog.names - extractionActions) assertFalse(name in prompt, "$name must not be offered for untrusted text")
        assertContains(prompt, Prompts.NOW_TEXT)
    }

    @Test
    fun onlyTheNotificationPromptWarnsAboutUntrustedInput() {
        assertTrue("untrusted" in Prompts.notificationSystem())
        assertFalse("untrusted" in Prompts.commandSystem())
    }

    @Test
    fun everyFewShotOutputIsAValidAction() {
        val command = fewShotOutputs(Prompts.commandSystem())
        val notification = fewShotOutputs(Prompts.notificationSystem())
        assertEquals(3, command.size)
        assertEquals(3, notification.size)
        for (output in command + notification) assertIs<ParseResult.Valid>(OutputParser.parse(output))
    }

    @Test
    fun untrustedFewShotsOnlyUseExtractionActions() {
        val actions = fewShotOutputs(Prompts.notificationSystem()).map { Regex(""""action":"(\w+)"""").find(it)!!.groupValues[1] }
        assertEquals(extractionActions, actions.toSet())
    }

    @Test
    fun fewShotInputsAreNotInTheDatasets() {
        val data = Dataset(CommandGenerator.generate(), NotificationGenerator.generate(), AdversarialGenerator.generate())
        val known = data.commands.map { it.text } + data.notifications.map { it.text } + data.adversarial.map { it.text }
        val inputs = Regex("""Input: (.*)""").findAll(Prompts.commandSystem()).map { it.groupValues[1] }.toList() +
            Regex("""Text: (.*)""").findAll(Prompts.notificationSystem()).map { it.groupValues[1] }.toList()
        assertEquals(6, inputs.size)
        for (input in inputs) assertFalse(known.any { input in it || it in input }, input)
    }

    @Test
    fun promptsHaveNoHindiOrHinglishExamples() {
        for (prompt in listOf(Prompts.commandSystem(), Prompts.notificationSystem())) {
            assertTrue(prompt.none { it in 'ऀ'..'ॿ' })
            assertFalse("parso" in prompt)
        }
    }

    @Test
    fun promptsStayWithinEightyPercentOfTheirV1Length() {
        val command = Prompts.commandSystem().length
        val notification = Prompts.notificationSystem().length
        assertTrue(command <= v1CommandChars * 0.8, "command prompt is $command chars")
        assertTrue(notification <= v1NotificationChars * 0.8, "notification prompt is $notification chars")
    }

    @Test
    fun userMessagesCarryTheTextVerbatim() {
        assertEquals("kal 9 baje call", Prompts.userCommand("kal 9 baje call"))
        assertEquals("App: com.phonepe.app\nText: Rs.10 debited", Prompts.userNotification("com.phonepe.app", "Rs.10 debited"))
    }

    @Test
    fun versionIsStamped() {
        assertEquals("proposer-v2.1", Prompts.VERSION)
    }

    @Test
    fun theDefaultSetIsTheFullPrompts() {
        assertEquals(Prompts.VERSION, Prompts.full.version)
        assertEquals(Prompts.commandSystem(), Prompts.full.command)
        assertEquals(Prompts.notificationSystem(), Prompts.full.notification)
    }

    @Test
    fun zeroShotHasNoExamplesButKeepsTheRules() {
        val set = Prompts.named("zero-shot")
        assertEquals("${Prompts.VERSION}-zero-shot", set.version)
        for (prompt in listOf(set.command, set.notification)) {
            assertFalse("Output:" in prompt)
            assertContains(prompt, Prompts.NOW_TEXT)
        }
        for (name in ActionCatalog.names) assertContains(set.command, name)
        for (name in ActionCatalog.names - extractionActions) assertFalse(name in set.notification)
        assertTrue("untrusted" in set.notification)
        assertFalse("untrusted" in set.command)
    }

    @Test
    fun oneShotKeepsExactlyOneExampleInEachPrompt() {
        val set = Prompts.named("one-shot")
        assertEquals("${Prompts.VERSION}-one-shot", set.version)
        assertEquals(1, fewShotOutputs(set.command).size)
        assertEquals(1, fewShotOutputs(set.notification).size)
        for (output in fewShotOutputs(set.command) + fewShotOutputs(set.notification)) assertIs<ParseResult.Valid>(OutputParser.parse(output))
    }

    @Test
    fun fewerExamplesMeanShorterPrompts() {
        val zero = Prompts.named("zero-shot")
        val one = Prompts.named("one-shot")
        assertTrue(zero.command.length < one.command.length && one.command.length < Prompts.full.command.length)
        assertTrue(zero.notification.length < one.notification.length && one.notification.length < Prompts.full.notification.length)
    }

    @Test
    fun compactPromptsStateTheJsonShape() {
        val compact = Prompts.named("compact")
        for (prompt in listOf(compact.command, compact.notification)) assertContains(prompt, """{"action":"<Name>","args":{...}}""")
    }

    @Test
    fun compactSetKeepsTheRulesOfTheFullSet() {
        val compact = Prompts.named("compact")
        assertEquals("proposer-v3.1", compact.version)
        for (name in ActionCatalog.names) assertContains(compact.command, name)
        for (name in extractionActions) assertContains(compact.notification, name)
        for (name in ActionCatalog.names - extractionActions) assertFalse(name in compact.notification)
        for (prompt in listOf(compact.command, compact.notification)) {
            assertContains(prompt, Prompts.NOW_TEXT)
            assertTrue(prompt.none { it in 'ऀ'..'ॿ' })
        }
        assertTrue("untrusted" in compact.notification)
        assertFalse("untrusted" in compact.command)
    }

    @Test
    fun compactAndLinePromptsFitTwoPrefillChunksWithTheLongestEnglishMessage() {
        val english = Dataset(CommandGenerator.generate(), NotificationGenerator.generate(), AdversarialGenerator.generate()).englishOnly()
        val longestCommand = english.commands.maxOf { Prompts.userCommand(it.text).length }
        val longestNotification = (
            english.notifications.map { Prompts.userNotification(it.source, it.text) } +
                english.adversarial.map { Prompts.userNotification(it.source, it.text) }
            ).maxOf { it.length }
        for (name in listOf("compact", "line")) {
            val set = Prompts.named(name)
            val command = set.command.length * systemTokensPerChar + longestCommand * userTokensPerChar
            val notification = set.notification.length * systemTokensPerChar + longestNotification * userTokensPerChar
            assertTrue(command <= twoChunkTokens, "$name command prompt is about ${command.toInt()} tokens")
            assertTrue(notification <= twoChunkTokens, "$name notification prompt is about ${notification.toInt()} tokens")
        }
    }

    @Test
    fun compactAndLinePromptsForbidCodeFencesAndNumericExamples() {
        for (name in listOf("compact", "line")) {
            val set = Prompts.named(name)
            for (prompt in listOf(set.command, set.notification)) {
                assertContains(prompt, "no code fences")
                assertFalse("840.00" in prompt, "$name must not show an example amount the model can copy")
            }
        }
    }

    @Test
    fun compactPromptsSpellOutTheArgumentKeysAndTheCalendarCue() {
        val compact = Prompts.named("compact")
        assertContains(compact.notification, "args: amount")
        assertContains(compact.notification, "args: text")
        assertTrue(Regex("""CreateCalendarEvent[^\n]*meeting""").containsMatchIn(compact.command))
    }

    private fun assertSignatures(prompt: String, names: Collection<String>) {
        for (name in names) {
            val keys = ActionCatalog.required.getValue(name).toList()
            val signature = prompt.lines().single { it.startsWith(name) && ("|" in it || it == name) }
            val shape = if (keys.isEmpty()) Regex("^$name$") else Regex("^$name(\\|<[^>]+>){${keys.size}}$")
            assertTrue(shape.matches(signature), "$name line must be only the name and ${keys.size} <placeholders>: $signature")
            var from = 0
            for (key in keys) {
                val found = Regex("""\b$key\b""").find(signature, from)
                assertTrue(found != null, "$name line must list $key after position $from: $signature")
                from = found.range.last + 1
            }
        }
    }

    @Test
    fun linePromptsAskForOneLineAndListEveryActionWithItsArgsInCatalogOrder() {
        val line = Prompts.named("line")
        assertEquals("proposer-v3.2-line", line.version)
        assertEquals(AnswerFormat.LINE, line.format)
        assertSignatures(line.command, ActionCatalog.names)
        assertSignatures(line.notification, extractionActions)
        for (name in ActionCatalog.names - extractionActions) assertFalse(name in line.notification)
        assertTrue("untrusted" in line.notification)
        assertFalse("untrusted" in line.command)
    }

    @Test
    fun theLineCommandPromptShowsOneFilledExampleThatParsesAndIsNotInTheDatasets() {
        val example = Regex("""Example: (.+)""").findAll(Prompts.named("line").command).map { it.groupValues[1] }.toList().single()
        val action = assertIs<ParseResult.Valid>(AnswerFormat.LINE.parse(example)).action
        val known = (CommandGenerator.generate().flatMap { it.expected.args.values } +
            NotificationGenerator.generate().flatMap { it.expected.args.values }).toSet()
        assertTrue(action.args.values.none { it in known }, example)
    }

    @Test
    fun linePromptsExplainWhenToUseEachActionOutsideTheSignatureLines() {
        val line = Prompts.named("line")
        assertTrue(Regex("""meeting or event""").containsMatchIn(line.command))
        assertTrue(Regex("""payment or credit alert""").containsMatchIn(line.notification))
        assertTrue(Regex("""due date""").containsMatchIn(line.notification))
    }

    @Test
    fun jsonSetsParseJsonAndTheLineSetParsesLines() {
        assertEquals(AnswerFormat.JSON, Prompts.full.format)
        assertEquals(AnswerFormat.JSON, Prompts.named("compact").format)
        assertIs<ParseResult.Valid>(AnswerFormat.LINE.parse("NoAction"))
        assertIs<ParseResult.Valid>(AnswerFormat.JSON.parse("""{"action":"NoAction"}"""))
    }

    @Test
    fun setsAreLookedUpByNameAndUnknownNamesFail() {
        assertEquals(Prompts.full, Prompts.named("full"))
        assertFailsWith<IllegalArgumentException> { Prompts.named("two-shot") }
    }
}
