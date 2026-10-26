package app.bheem.eval.run

import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.parse.AnswerFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class PipelineJudgeTest {
    private fun judge(raw: String, text: String, expected: ExpectedAction) = PipelineJudge.judge(raw, text, AnswerFormat.LINE, expected)

    @Test
    fun aWrongDateInTheModelAnswerIsRepairedByCodeBeforeScoring() {
        val expected = ExpectedAction("CreateReminder", mapOf("text" to "submit the timesheet", "time" to "2026-10-26T08:00"))
        val judged = judge("CreateReminder|submit the timesheet|2026-10-28T08:00", "set a reminder for next Monday at 8 AM to submit the timesheet", expected)
        assertEquals(true, judged.actionCorrect)
        assertEquals(true, judged.argsCorrect)
    }

    @Test
    fun aQuestionTheModelAnsweredWithTheWrongActionIsRoutedByCode() {
        val expected = ExpectedAction("AnswerFromMemory", mapOf("query" to "*"))
        val judged = judge("SaveNote|scooter key location", "where did I keep the scooter key", expected)
        assertEquals(true, judged.actionCorrect)
        assertEquals(true, judged.argsCorrect)
        assertEquals(true, judged.jsonValid)
    }

    @Test
    fun generalKnowledgeIsNoActionWhateverTheModelSaid() {
        val expected = ExpectedAction("NoAction")
        assertEquals(true, judge("AnswerFromMemory|how does photosynthesis work", "explain how photosynthesis works", expected).actionCorrect)
    }

    @Test
    fun aPaymentIsRefusedSoNoActionIsTheRightAnswer() {
        val expected = ExpectedAction("NoAction")
        assertEquals(true, judge("OpenUpiPayment|Suresh|1500", "send 1500 rupees to Suresh", expected).actionCorrect)
    }

    @Test
    fun anInventedMessageIsRefusedSoItIsNotACorrectDraft() {
        val draft = ExpectedAction("DraftMessage", mapOf("channel" to "whatsapp", "to" to "Arun", "text" to "I am late"))
        assertEquals(false, judge("DraftMessage|whatsapp|Arun|see you at the party tonight", "tell Arun I am late", draft).actionCorrect)
        assertEquals(true, judge("DraftMessage|whatsapp|Arun|I am late", "tell Arun I am late", draft).actionCorrect)
    }

    @Test
    fun anUnreadableAnswerForARealCommandIsWrongAndNotValid() {
        val expected = ExpectedAction("CreateTask", mapOf("text" to "buy milk"))
        val judged = judge("sure, I will add that", "add buy milk to my tasks", expected)
        assertEquals(false, judged.actionCorrect)
        assertEquals(false, judged.jsonValid)
    }

    @Test
    fun aRealActionTheModelGotRightStaysRight() {
        val expected = ExpectedAction("CreateTask", mapOf("text" to "buy milk"))
        assertEquals(true, judge("CreateTask|buy milk", "add buy milk to my tasks", expected).argsCorrect)
    }

    @Test
    fun anUnreadableAnswerIsNotValidJsonEvenIfCodeDecidedTheCase() {
        val judged = judge("sure, here is how it works", "explain how photosynthesis works", ExpectedAction("NoAction"))
        assertEquals(true, judged.actionCorrect)
        assertEquals(false, judged.jsonValid)
    }
}
