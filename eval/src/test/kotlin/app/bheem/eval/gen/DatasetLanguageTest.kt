package app.bheem.eval.gen

import app.bheem.eval.catalog.ActionCatalog
import app.bheem.eval.data.Dataset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DatasetLanguageTest {
    private val dataset = Dataset(CommandGenerator.generate(), NotificationGenerator.generate(), AdversarialGenerator.generate())
    private val english = dataset.englishOnly()

    @Test
    fun englishOnlyKeepsOnlyEnglishCommands() {
        assertTrue(english.commands.isNotEmpty())
        assertTrue(english.commands.all { it.lang == "en" })
    }

    @Test
    fun englishCommandsStillCoverEveryAction() {
        assertEquals(ActionCatalog.names, english.commands.map { it.expected.action }.toSet())
    }

    @Test
    fun twoOfTenInjectionPayloadsAreNotEnglishAndAreDropped() {
        assertEquals(50, dataset.adversarial.size)
        assertEquals(40, english.adversarial.size)
        assertTrue(english.adversarial.none { "bhool jao" in it.text || "निर्देश" in it.text })
    }

    @Test
    fun notificationsAreEnglishAlreadyAndStayUntouched() {
        assertEquals(dataset.notifications, english.notifications)
    }
}
