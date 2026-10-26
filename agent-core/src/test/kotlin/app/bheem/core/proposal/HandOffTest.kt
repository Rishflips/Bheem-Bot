package app.bheem.core.proposal

import app.bheem.core.action.ProposedAction
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking

class HandOffTest {
    private class FakeLauncher(var works: Boolean = true, var throws: Exception? = null) : HandOffLauncher {
        val seen = mutableListOf<HandOffRequest>()

        override fun open(request: HandOffRequest): Boolean {
            seen += request
            throws?.let { throw it }
            return works
        }
    }

    private val launcher = FakeLauncher()
    private val calendar = HandOffExecutors.calendar(launcher)
    private val message = HandOffExecutors.message(launcher)

    private val dentist = ProposedAction("CreateCalendarEvent", mapOf("title" to "dentist", "start" to "2026-10-24T16:00"))
    private fun draft(channel: String) = ProposedAction("DraftMessage", mapOf("channel" to channel, "to" to "Arun", "text" to "I am late"))

    @Test
    fun theCalendarExecutorHandsTheEventOverAndCannotUndo() = runBlocking<Unit> {
        val result = assertIs<ExecutionResult.Done>(calendar.execute(dentist, "p1"))
        assertEquals(null, result.undoToken)
        assertEquals(HandOffRequest.Calendar("dentist", LocalDateTime.of(2026, 10, 24, 16, 0)), launcher.seen.single())
        assertEquals(false, calendar.undo(dentist, null))
    }

    @Test
    fun aMissingCalendarAppIsReportedInPlainWords() = runBlocking<Unit> {
        launcher.works = false
        val failed = assertIs<ExecutionResult.Failed>(calendar.execute(dentist, "p1"))
        assertEquals("No calendar app is available. Install or enable one, then try again.", failed.reason)
    }

    @Test
    fun theMessageExecutorHandsOverTheChannelTheRecipientAndTheText() = runBlocking<Unit> {
        assertIs<ExecutionResult.Done>(message.execute(draft("whatsapp"), "p1"))
        assertEquals(HandOffRequest.Message("whatsapp", "Arun", "I am late"), launcher.seen.single())
        assertEquals(false, message.undo(draft("whatsapp"), null))
    }

    @Test
    fun aMissingMessagingAppNamesTheChannel() = runBlocking<Unit> {
        launcher.works = false
        assertEquals("WhatsApp is not installed. Install it, then try again.", assertIs<ExecutionResult.Failed>(message.execute(draft("whatsapp"), "p1")).reason)
        assertEquals("No messaging app is available for SMS. Install or enable one, then try again.", assertIs<ExecutionResult.Failed>(message.execute(draft("sms"), "p2")).reason)
    }

    @Test
    fun aLauncherThatThrowsGivesAFailureNotACrash() = runBlocking<Unit> {
        launcher.throws = SecurityException("blocked")
        val failed = assertIs<ExecutionResult.Failed>(calendar.execute(dentist, "p1"))
        assertEquals("Android did not let Bheem open that app. Try again.", failed.reason)
    }

    @Test
    fun aRetryAfterAFailureOpensTheAppOnce() = runBlocking<Unit> {
        launcher.works = false
        assertIs<ExecutionResult.Failed>(calendar.execute(dentist, "p1"))
        launcher.works = true
        assertIs<ExecutionResult.Done>(calendar.execute(dentist, "p1"))
        assertEquals(2, launcher.seen.size)
    }

    @Test
    fun theOneArgumentExecuteIsNotUsedForHandOffs() = runBlocking<Unit> {
        assertIs<ExecutionResult.Failed>(calendar.execute(dentist))
        assertEquals(emptyList(), launcher.seen)
    }
}
