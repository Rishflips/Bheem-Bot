package app.bheem.eval.run

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProcStatusTest {
    private val status = "Name:\tapp.bheem.bench\nVmPeak:\t 9000000 kB\nVmHWM:\t 2048000 kB\nVmRSS:\t 1900000 kB\n"

    @Test
    fun readsAKbField() {
        assertEquals(2048000L, ProcStatus.kb(status, "VmHWM"))
        assertEquals(1900000L, ProcStatus.kb(status, "VmRSS"))
    }

    @Test
    fun missingFieldIsNull() {
        assertNull(ProcStatus.kb(status, "VmSwap"))
        assertNull(ProcStatus.kb("", "VmHWM"))
    }
}
