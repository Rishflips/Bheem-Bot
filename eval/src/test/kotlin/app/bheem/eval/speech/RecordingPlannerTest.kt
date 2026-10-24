package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.gen.CommandGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RecordingPlannerTest {
    private val english: List<CommandCase> = CommandGenerator.generate().filter { it.lang == "en" }
    private val plan = RecordingPlanner.plan(english)

    private fun spread(counts: Collection<Int>) = (counts.maxOrNull() ?: 0) - (counts.minOrNull() ?: 0)

    @Test
    fun theDefaultPlanHasSixSpeakersAndEveryCommandIsReadTwice() {
        assertEquals((1..6).map { "S0$it" }, plan.speakers)
        assertEquals(english.size * 2, plan.takes.size)
        val byCommand = plan.takes.groupBy { it.commandId }
        assertEquals(english.map { it.id }.toSet(), byCommand.keys)
        for ((id, takes) in byCommand) {
            assertEquals(2, takes.size, id)
            assertEquals(2, takes.map { it.speaker }.toSet().size, "two different speakers read $id")
        }
    }

    @Test
    fun takeIdsAreUniqueAndNameTheSpeakerTheCommandAndTheCondition() {
        assertEquals(plan.takes.size, plan.takes.map { it.id }.toSet().size)
        val take = plan.takes.first()
        assertEquals("${take.speaker}-${take.commandId}-${take.condition.code}", take.id)
    }

    @Test
    fun everyTakeCarriesTheCommandTextToRead() {
        val text = english.associate { it.id to it.text }
        for (t in plan.takes) assertEquals(text.getValue(t.commandId), t.text)
    }

    @Test
    fun eachSpeakerReadsAboutTheSameNumberOfCommands() {
        assertTrue(spread(plan.takes.groupingBy { it.speaker }.eachCount().values) <= 1)
    }

    @Test
    fun eachSpeakerReadsAboutTheSameShareOfEveryKindOfCommand() {
        val action = english.associate { it.id to it.expected.action }
        for ((kind, takes) in plan.takes.groupBy { action.getValue(it.commandId) }) {
            val perSpeaker = plan.speakers.map { s -> takes.count { it.speaker == s } }
            assertTrue(spread(perSpeaker) <= 1, "$kind: $perSpeaker")
        }
    }

    @Test
    fun conditionsAreBalancedForEachSpeakerAndOverall() {
        for (s in plan.speakers) {
            val counts = RecordingCondition.entries.map { c -> plan.takes.count { it.speaker == s && it.condition == c } }
            assertTrue(spread(counts) <= 1, "$s: $counts")
        }
        val overall = RecordingCondition.entries.map { c -> plan.takes.count { it.condition == c } }
        assertTrue(spread(overall) <= 1, "$overall")
    }

    @Test
    fun aSpeakersSheetIsInBlocksOfOneConditionAndTheBlockOrderRotatesBySpeaker() {
        val firstBlocks = mutableSetOf<RecordingCondition>()
        for (s in plan.speakers) {
            val sheet = plan.forSpeaker(s)
            assertEquals((1..sheet.size).toList(), sheet.map { it.position })
            val blocks = sheet.map { it.condition }.fold(mutableListOf<RecordingCondition>()) { acc, c -> if (acc.lastOrNull() != c) acc += c; acc }
            assertEquals(RecordingCondition.entries.size, blocks.size, "$s has one block per condition")
            firstBlocks += blocks.first()
        }
        assertEquals(RecordingCondition.entries.toSet(), firstBlocks)
    }

    @Test
    fun theSamePlanComesBackForTheSameSeedAndTheOrderChangesWithAnotherSeed() {
        assertEquals(plan, RecordingPlanner.plan(english))
        val other = RecordingPlanner.plan(english, seed = 7)
        assertNotEquals(plan.takes.map { it.id }, other.takes.map { it.id })
        assertEquals(plan.takes.map { it.id }.toSet(), other.takes.map { it.id }.toSet())
    }

    @Test
    fun conditionCodesAreDistinctSingleLetters() {
        val codes = RecordingCondition.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it.length == 1 })
    }

    @Test
    fun badSettingsAreRejected() {
        assertFailsWith<IllegalArgumentException> { RecordingPlanner.plan(english, speakers = 1, readersPerCommand = 2) }
        assertFailsWith<IllegalArgumentException> { RecordingPlanner.plan(english, readersPerCommand = 0) }
        assertFailsWith<IllegalArgumentException> { RecordingPlanner.plan(emptyList()) }
    }

    @Test
    fun theSummaryListsTakesPerSpeakerAndPerCondition() {
        val summary = plan.summary()
        assertTrue("S01" in summary)
        assertTrue("${plan.takes.size} takes" in summary)
        for (c in RecordingCondition.entries) assertTrue(c.name.lowercase() in summary.lowercase(), c.name)
    }
}
