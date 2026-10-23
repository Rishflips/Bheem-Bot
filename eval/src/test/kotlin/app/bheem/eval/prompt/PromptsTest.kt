package app.bheem.eval.prompt

import app.bheem.eval.catalog.ActionCatalog
import app.bheem.eval.parse.OutputParser
import app.bheem.eval.parse.ParseResult
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PromptsTest {
    @Test
    fun systemPromptsNameEveryActionAndTheClock() {
        for (prompt in listOf(Prompts.commandSystem(), Prompts.notificationSystem())) {
            for (name in ActionCatalog.names) assertContains(prompt, name)
            assertContains(prompt, Prompts.NOW_TEXT)
        }
    }

    @Test
    fun onlyTheNotificationPromptWarnsAboutUntrustedInput() {
        assertTrue("untrusted" in Prompts.notificationSystem())
        assertFalse("untrusted" in Prompts.commandSystem())
    }

    @Test
    fun everyFewShotOutputIsAValidAction() {
        val outputs = Regex("""Output: (\{.*\})""").findAll(Prompts.commandSystem()).map { it.groupValues[1] }.toList()
        assertEquals(3, outputs.size)
        for (output in outputs) assertIs<ParseResult.Valid>(OutputParser.parse(output))
    }

    @Test
    fun userMessagesCarryTheTextVerbatim() {
        assertEquals("kal 9 baje call", Prompts.userCommand("kal 9 baje call"))
        assertEquals("App: com.phonepe.app\nText: Rs.10 debited", Prompts.userNotification("com.phonepe.app", "Rs.10 debited"))
    }

    @Test
    fun versionIsStamped() {
        assertEquals("proposer-v1", Prompts.VERSION)
    }
}
