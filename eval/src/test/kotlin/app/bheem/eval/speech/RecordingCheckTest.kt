package app.bheem.eval.speech

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecordingCheckTest {
    private fun take(id: String, speaker: String = "S01", condition: RecordingCondition = RecordingCondition.QUIET) =
        Take(id, speaker, "cmd-001", "text", condition, 1)

    private fun dir(vararg files: Pair<String, ByteArray>): Path {
        val dir = createTempDirectory("check")
        for ((name, bytes) in files) Files.write(dir.resolve(name), bytes)
        return dir
    }

    private val one = take("S01-cmd-001-q")
    private val two = take("S02-cmd-001-n", speaker = "S02", condition = RecordingCondition.NOISY)

    @Test
    fun everyTakePresentAndInSpeechFormatIsReady() {
        val result = RecordingCheck.check(
            listOf(one, two),
            dir("S01-cmd-001-q.wav" to WavFixtures.wav(seconds = 2.0), "S02-cmd-001-n.wav" to WavFixtures.wav(seconds = 3.0)),
        )
        assertEquals(listOf(one.id, two.id), result.ready)
        assertTrue(result.complete)
        assertEquals(5_000L, result.audioMs)
    }

    @Test
    fun aMissingTakeIsListedAndTheSetIsNotComplete() {
        val result = RecordingCheck.check(listOf(one, two), dir("S01-cmd-001-q.wav" to WavFixtures.wav()))
        assertEquals(listOf(two.id), result.missing)
        assertFalse(result.complete)
    }

    @Test
    fun audioInTheWrongFormatIsBadWithTheReason() {
        val result = RecordingCheck.check(listOf(one), dir("S01-cmd-001-q.wav" to WavFixtures.wav(sampleRate = 44_100, channels = 2)))
        assertContains(result.bad.getValue(one.id), "16 kHz mono")
        assertTrue(result.ready.isEmpty())
        assertFalse(result.complete)
    }

    @Test
    fun aFileThatIsNotAWavIsBad() {
        val result = RecordingCheck.check(listOf(one), dir("S01-cmd-001-q.wav" to "not audio at all".toByteArray()))
        assertContains(result.bad.getValue(one.id), "RIFF")
    }

    @Test
    fun aWavThatIsNotInTheManifestIsReportedAsUnknown() {
        val result = RecordingCheck.check(listOf(one), dir("S01-cmd-001-q.wav" to WavFixtures.wav(), "Recording 7.wav" to WavFixtures.wav()))
        assertEquals(listOf("Recording 7.wav"), result.unknown)
        assertTrue(result.complete)
    }

    @Test
    fun aMissingFolderMeansEverythingIsMissing() {
        val result = RecordingCheck.check(listOf(one, two), createTempDirectory("none").resolve("audio"))
        assertEquals(listOf(one.id, two.id), result.missing)
    }

    @Test
    fun theReportCountsTakesPerSpeakerAndNamesWhatIsWrong() {
        val result = RecordingCheck.check(
            listOf(one, two, take("S02-cmd-002-q", speaker = "S02")),
            dir("S01-cmd-001-q.wav" to WavFixtures.wav(seconds = 60.0), "S02-cmd-002-q.wav" to "bad".toByteArray(), "stray.wav" to WavFixtures.wav()),
        )
        val report = RecordingCheck.report(result)
        assertContains(report, "1 of 3 takes ready")
        assertContains(report, "1 missing")
        assertContains(report, "1 bad")
        assertContains(report, "1 unknown")
        assertContains(report, "S01 1/1")
        assertContains(report, "S02 0/2")
        assertContains(report, "S02-cmd-001-n")
        assertContains(report, "S02-cmd-002-q: ")
        assertContains(report, "stray.wav")
        assertContains(report, "1.0 minutes")
        assertFalse('—' in report)
    }

    @Test
    fun theReportSaysSoWhenEverythingIsReady() {
        val result = RecordingCheck.check(listOf(one), dir("S01-cmd-001-q.wav" to WavFixtures.wav()))
        assertContains(RecordingCheck.report(result), "All 1 takes are ready")
    }
}
