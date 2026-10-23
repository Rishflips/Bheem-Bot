package app.bheem.eval.prompt

import app.bheem.eval.catalog.ActionCatalog
import app.bheem.eval.data.Dataset
import app.bheem.eval.gen.AdversarialGenerator
import app.bheem.eval.gen.CommandGenerator
import app.bheem.eval.gen.NotificationGenerator
import app.bheem.eval.parse.OutputParser
import app.bheem.eval.parse.ParseResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PromptsTest {
    private val extractionActions = setOf("LogExpense", "CreateReminder", "NoAction")
    private val v1CommandChars = 1454
    private val v1NotificationChars = 1706

    private fun fewShotOutputs(prompt: String) =
        Regex("""Output: (\{.*\})""").findAll(prompt).map { it.groupValues[1] }.toList()

    @Test
    fun commandPromptNamesEveryActionAndTheClock() {
        for (name in ActionCatalog.names) assertContains(Prompts.commandSystem(), name)
        assertContains(Prompts.commandSystem(), Prompts.NOW_TEXT)
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
        assertEquals("proposer-v2", Prompts.VERSION)
    }
}
