package app.bheem.core.shared

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class SharedTest {
    private class Handle(val id: Int) {
        var closed = false
    }

    private var opened = 0
    private val shared = Shared({ Handle(++opened) }, { it.closed = true })

    @Test
    fun theSameInstanceIsHandedOutUntilItIsClosed() {
        val a = shared.get()
        assertSame(a, shared.get())
        shared.close()
        assertTrue(a.closed)
        assertNotSame(a, shared.get())
        assertEquals(2, opened)
    }

    @Test
    fun closingTwiceIsHarmless() {
        shared.get()
        shared.close()
        shared.close()
        assertEquals(1, opened)
    }

    @Test
    fun aGetDuringAReplaceWaitsAndGetsTheNewInstanceNeverTheOldOne() {
        val old = shared.get()
        val inside = CountDownLatch(1)
        var erased = false
        val eraser = thread {
            shared.replacing {
                inside.countDown()
                Thread.sleep(300)
                erased = true
            }
        }
        assertTrue(inside.await(5, TimeUnit.SECONDS))
        var sawErasedFirst = false
        var got: Handle? = null
        val reader = thread {
            got = shared.get()
            sawErasedFirst = erased
        }
        eraser.join()
        reader.join()
        assertTrue(sawErasedFirst)
        assertTrue(old.closed)
        assertNotSame(old, got)
    }
}
