package app.bheem.eval.gen

import app.bheem.core.time.TimeReconciler
import app.bheem.core.time.TimeSource
import app.bheem.eval.data.CommandCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimeResolutionOnCommandSetsTest {
    private val scheduled = (CommandGenerator.generate().filter { it.lang == "en" } + AssistantCommands.cases())
        .filter { it.expected.action in setOf("CreateReminder", "CreateCalendarEvent") }

    private fun expectedTime(c: CommandCase) = c.expected.args.getValue(if (c.expected.action == "CreateReminder") "time" else "start")

    @Test
    fun codeAloneReproducesTheExpectedTimeOfEveryEnglishScheduledCommand() {
        assertTrue(scheduled.size >= 48, "scheduled cases: ${scheduled.size}")
        val wrong = scheduled.mapNotNull { c ->
            val r = TimeReconciler.reconcile(c.text, null, ReferenceClock.NOW)
            val got = r.value?.format(ReferenceClock.isoMinute)
            if (got == expectedTime(c)) null else "${c.id} '${c.text}' expected ${expectedTime(c)} got $got"
        }
        assertEquals(emptyList(), wrong)
    }

    @Test
    fun aWrongModelTimeIsCorrectedForEveryScheduledCommand() {
        for (c in scheduled) {
            val r = TimeReconciler.reconcile(c.text, "2026-12-31T23:59", ReferenceClock.NOW)
            assertEquals(expectedTime(c), r.value?.format(ReferenceClock.isoMinute), c.text)
            assertEquals(TimeSource.CODE, r.source, c.text)
            assertTrue(r.corrected, c.text)
        }
    }
}
