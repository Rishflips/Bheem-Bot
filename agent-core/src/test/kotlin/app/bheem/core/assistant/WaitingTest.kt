package app.bheem.core.assistant

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WaitingTest {
    @Test
    fun theLoadBarFillsByElapsedTimeAndStopsAt95PercentUntilTheModelIsReady() {
        assertEquals(0.5f, Waiting.loadProgress(10_000, 20)!!, 0.001f)
        assertEquals(0.95f, Waiting.loadProgress(60_000, 20)!!, 0.001f)
        assertEquals(0f, Waiting.loadProgress(0, 20)!!, 0.001f)
    }

    @Test
    fun withNoUsualLoadTimeTheBarIsIndeterminate() {
        assertNull(Waiting.loadProgress(10_000, null))
        assertNull(Waiting.loadProgress(10_000, 0))
    }

    @Test
    fun theSecondLineAppearsAfterSixSeconds() {
        assertFalse(Waiting.showStillOnIt(5_999))
        assertTrue(Waiting.showStillOnIt(6_000))
    }

    @Test
    fun dotsStandStillWhenAnimationsAreOffOrTheTextIsLarge() {
        assertTrue(Waiting.staticDots(animatorScale = 0f, fontScale = 1f))
        assertTrue(Waiting.staticDots(animatorScale = 1f, fontScale = 1.4f))
        assertFalse(Waiting.staticDots(animatorScale = 1f, fontScale = 1.3f))
    }
}
