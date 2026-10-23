package app.bheem.eval.speech

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class StreamingTranscriberTest {
    private class World(val addCost: Long = 0, val finishCost: Long = 0, val finalText: String = "pay ravi 840 rupees", val failAt: Int? = null) {
        var now = 5_000L
        val deliveries = mutableListOf<Pair<Long, Int>>()
        var finished = false
        var closed = false

        val stream = object : AudioStream {
            override fun add(samples: FloatArray, sampleRate: Int) {
                if (failAt == deliveries.size) throw IllegalStateException("boom")
                deliveries += now to samples.size
                now += addCost
            }

            override fun finish(): String {
                finished = true
                now += finishCost
                return finalText
            }

            override fun close() {
                closed = true
            }
        }

        fun transcriber(chunkMs: Int = 100) = StreamingTranscriber("m-live", open = { stream }, clock = { now }, pause = { now += it }, chunkMs = chunkMs)
    }

    private fun wavFile(seconds: Double): String {
        val file = createTempDirectory("live").resolve("take.wav")
        Files.write(file, WavFixtures.wav(seconds = seconds))
        return file.toString()
    }

    @Test
    fun feedsTheClipInOrderInChunksAndThenFinishes() = runBlocking {
        val world = World()
        world.transcriber().transcribe(wavFile(1.0))
        assertEquals(10, world.deliveries.size)
        assertTrue(world.deliveries.all { it.second == 1_600 })
        assertTrue(world.finished)
        assertTrue(world.closed)
    }

    @Test
    fun chunksArriveAtTheSpeedOfSpeechNotFasterThanTheAudioExists() = runBlocking {
        val world = World()
        world.transcriber().transcribe(wavFile(1.0))
        assertEquals((1..10).map { 5_000L + it * 100L }, world.deliveries.map { it.first })
    }

    @Test
    fun theWaitAfterTheLastChunkIsTheTimeToFinishWhenTheEngineKeepsUp() = runBlocking {
        val world = World(addCost = 20, finishCost = 300)
        val result = world.transcriber().transcribe(wavFile(1.0))
        assertEquals(320L, result.firstChunkMs)
        assertEquals(320L, result.totalMs)
    }

    @Test
    fun anEngineSlowerThanSpeechFallsBehindAndTheBacklogCountsFromTheEndOfTheAudio() = runBlocking {
        val world = World(addCost = 150, finishCost = 300)
        val result = world.transcriber().transcribe(wavFile(1.0))
        assertEquals(450L, result.firstChunkMs)
        assertEquals(900L, result.totalMs)
    }

    @Test
    fun aShorterLastChunkIsDeliveredWhenTheAudioEnds() = runBlocking {
        val world = World(finishCost = 300)
        val result = world.transcriber().transcribe(wavFile(1.05))
        assertEquals(11, world.deliveries.size)
        assertEquals(800, world.deliveries.last().second)
        assertEquals(6_050L, world.deliveries.last().first)
        assertEquals(300L, result.totalMs)
    }

    @Test
    fun theChunkSizeIsASetting() = runBlocking {
        val world = World()
        world.transcriber(chunkMs = 250).transcribe(wavFile(1.0))
        assertEquals(4, world.deliveries.size)
        assertTrue(world.deliveries.all { it.second == 4_000 })
    }

    @Test
    fun theFinalTextIsCleaned() = runBlocking {
        val result = World(finalText = "Pay Ravi 840 rupees.\n").transcriber().transcribe(wavFile(1.0))
        assertEquals("Pay Ravi 840 rupees.", result.text)
        assertEquals("Pay Ravi 840 rupees.\n", result.raw)
    }

    @Test
    fun audioNotInSpeechFormatIsRejectedBeforeAStreamIsOpened() {
        val file = createTempDirectory("live").resolve("take.wav")
        Files.write(file, WavFixtures.wav(sampleRate = 44_100, channels = 2))
        val transcriber = StreamingTranscriber("m-live", open = { error("must not open") }, clock = { 0 }, pause = {})
        val message = assertFailsWith<IllegalArgumentException> { runBlocking { transcriber.transcribe(file.toString()) } }.message.orEmpty()
        assertContains(message, "16 kHz mono")
    }

    @Test
    fun theStreamIsClosedEvenWhenFeedingFails() {
        val world = World(failAt = 3)
        assertFailsWith<IllegalStateException> { runBlocking { world.transcriber().transcribe(wavFile(1.0)) } }
        assertTrue(world.closed)
    }
}
