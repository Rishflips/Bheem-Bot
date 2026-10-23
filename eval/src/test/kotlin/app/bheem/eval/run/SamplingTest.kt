package app.bheem.eval.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SamplingTest {
    @Test
    fun aSampleSmallerThanTheListSpansTheWholeList() {
        val sample = (0 until 100).toList().evenlySpaced(10)
        assertEquals(10, sample.size)
        assertEquals(0, sample.first())
        assertTrue(sample.last() >= 90)
        assertEquals(sample.sorted().distinct(), sample)
    }

    @Test
    fun aSampleAtLeastAsBigAsTheListIsTheWholeList() {
        val list = listOf("a", "b", "c")
        assertEquals(list, list.evenlySpaced(3))
        assertEquals(list, list.evenlySpaced(10))
    }

    @Test
    fun anEmptyListStaysEmpty() {
        assertEquals(emptyList<Int>(), emptyList<Int>().evenlySpaced(5))
    }
}
