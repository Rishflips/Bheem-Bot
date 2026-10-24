package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase
import app.bheem.eval.data.ExpectedAction
import app.bheem.eval.gen.CommandGenerator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingSheetsTest {
    private val english = CommandGenerator.generate().filter { it.lang == "en" }
    private val plan = RecordingPlanner.plan(english)
    private val emDash = '—'

    @Test
    fun aSpeakerSheetListsEveryTakeOfThatSpeakerInOrderWithItsSentence() {
        val sheet = RecordingSheets.speakerSheet(plan, "S02")
        assertTrue("# Recording sheet S02" in sheet)
        var last = -1
        for (take in plan.forSpeaker("S02")) {
            val at = sheet.indexOf("| ${take.position} | `${take.id}` | ${take.text} |")
            assertTrue(at > last, "take ${take.id} is missing or out of order")
            last = at
        }
    }

    @Test
    fun aSpeakerSheetHasNothingFromOtherSpeakers() {
        val sheet = RecordingSheets.speakerSheet(plan, "S02")
        for (take in plan.takes.filter { it.speaker != "S02" }) assertFalse("`${take.id}`" in sheet, take.id)
    }

    @Test
    fun eachBlockStatesItsConditionAndHowToSetItUp() {
        val sheet = RecordingSheets.speakerSheet(plan, "S03")
        for (c in RecordingCondition.entries) assertTrue(c.instruction in sheet, c.name)
        assertTrue(Regex("""## Block 1 of 3: """).containsMatchIn(sheet))
        assertTrue(Regex("""## Block 3 of 3: """).containsMatchIn(sheet))
    }

    @Test
    fun theSheetCarriesTheProfileFieldsTheConsentAndTheFileRules() {
        val sheet = RecordingSheets.speakerSheet(plan, "S01")
        for (field in listOf("Native language:", "Gender:", "Age band", "Phone and how it is held:")) assertTrue(field in sheet, field)
        assertTrue("Do not write your name on this sheet" in sheet)
        assertTrue("deleted when the study ends" in sheet)
        assertTrue("16 kHz mono WAV" in sheet)
        assertTrue("<take ID>.wav" in sheet)
        assertTrue("normal speaking voice" in sheet)
    }

    @Test
    fun aSentenceWithAPipeDoesNotBreakTheTable() {
        val odd = CommandCase("cmd-900", "en", "save a note: a | b", ExpectedAction("SaveNote", mapOf("text" to "a | b")))
        val sheet = RecordingSheets.speakerSheet(RecordingPlanner.plan(listOf(odd), speakers = 2), "S01")
        assertTrue("save a note: a \\| b" in sheet)
    }

    @Test
    fun theReadmeExplainsTheStudyAndShowsTheBalance() {
        val readme = RecordingSheets.readme(plan)
        assertTrue(plan.summary().trim() in readme)
        assertTrue("audio/<take ID>.wav" in readme)
        assertTrue("never commit" in readme.lowercase())
        assertTrue("manifest.jsonl" in readme)
        for (s in plan.speakers) assertTrue("sheet-$s.md" in readme, s)
    }

    @Test
    fun theReadmeSaysHowToRunTheProbeOnThePhoneAndScoreIt() {
        val readme = RecordingSheets.readme(plan)
        assertTrue("scripts\\bench-speech.ps1" in readme)
        assertTrue("-AudioForms all" in readme)
        assertTrue(":eval:speechReport" in readme)
        for (form in listOf("file", "wav", "pcm")) assertTrue("`$form`" in readme, form)
    }

    @Test
    fun theReadmeRecommendsTheRecorderAppAndSaysHowToGetTheFilesBack() {
        val readme = RecordingSheets.readme(plan)
        assertTrue("## Recording with the phone app" in readme)
        assertTrue("scripts\\prepare-recorder.ps1" in readme)
        assertTrue("Bheem Recorder" in readme)
        assertTrue("Hold the big button" in readme)
        assertTrue("scripts\\pull-recordings.ps1" in readme)
        assertTrue(readme.indexOf("## Recording with the phone app") < readme.indexOf("## Running the probe on the phone"))
    }

    @Test
    fun theReadmeCoversConvertingCheckingAndTheSyntheticSmokeTest() {
        val readme = RecordingSheets.readme(plan)
        assertTrue("scripts\\convert-recordings.ps1" in readme)
        assertTrue(":eval:checkRecordings" in readme)
        assertTrue("scripts\\make-synthetic-audio.ps1" in readme)
        assertTrue("eval\\recordings-synthetic" in readme)
    }

    @Test
    fun noGeneratedTextContainsAnEmDash() {
        assertFalse(emDash in RecordingSheets.readme(plan))
        for (s in plan.speakers) assertFalse(emDash in RecordingSheets.speakerSheet(plan, s), s)
        assertFalse(RecordingCondition.entries.any { emDash in it.instruction })
    }
}
