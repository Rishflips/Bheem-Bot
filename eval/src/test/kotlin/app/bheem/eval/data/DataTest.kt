package app.bheem.eval.data

import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataTest {
    private val reminder = CommandCase(
        id = "cmd-001", lang = "hi-latn", text = "kal subah 9 baje Arun ko call karna yaad dilana",
        expected = ExpectedAction("CreateReminder", mapOf("text" to "Arun ko call karna", "time" to "2026-10-24T09:00")),
    )

    @Test
    fun jsonlRoundTripKeepsDevanagari() {
        val path = createTempFile(suffix = ".jsonl")
        val item = reminder.copy(id = "cmd-002", text = "कल सुबह 9 बजे अरुण को फोन करना याद दिलाना")
        Jsonl.write(path, listOf(reminder, item))
        assertEquals(listOf(reminder, item), Jsonl.read<CommandCase>(path))
    }

    @Test
    fun validatorAcceptsAGoodDataset() {
        assertTrue(DatasetValidator.validate(Dataset(listOf(reminder), emptyList(), emptyList())).isEmpty())
    }

    @Test
    fun validatorRejectsDuplicateIdsUnknownActionsAndMissingArgs() {
        val dup = reminder.copy()
        val unknown = reminder.copy(id = "cmd-003", expected = ExpectedAction("Teleport"))
        val missing = reminder.copy(id = "cmd-004", expected = ExpectedAction("CreateReminder", mapOf("text" to "x")))
        val errors = DatasetValidator.validate(Dataset(listOf(reminder, dup, unknown, missing), emptyList(), emptyList()))
        assertTrue(errors.any { "duplicate id cmd-001" in it })
        assertTrue(errors.any { "unknown action Teleport" in it })
        assertTrue(errors.any { "cmd-004 missing arg time" in it })
    }

    @Test
    fun validatorRejectsBlankText() {
        val blank = reminder.copy(id = "cmd-005", text = "  ")
        assertTrue(DatasetValidator.validate(Dataset(listOf(blank), emptyList(), emptyList())).any { "blank text" in it })
    }
}
