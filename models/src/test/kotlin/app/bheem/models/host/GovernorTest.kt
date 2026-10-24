package app.bheem.models.host

import kotlin.test.Test
import kotlin.test.assertEquals

class GovernorTest {
    @Test
    fun aCoolPhoneOnNormalPowerRunsEverything() {
        assertEquals(Capability.Full, Governor.capability(batterySaver = false, thermalStatus = 0, charging = false))
        assertEquals(Capability.Full, Governor.capability(batterySaver = false, thermalStatus = 1, charging = false))
    }

    @Test
    fun aWarmPhoneUsesTheCpuOnly() {
        assertEquals(Capability.CpuOnly, Governor.capability(batterySaver = false, thermalStatus = 2, charging = false))
    }

    @Test
    fun aHotPhoneOrBatterySaverStopsLoadingTheModel() {
        assertEquals(Capability.RulesOnly, Governor.capability(batterySaver = false, thermalStatus = 3, charging = false))
        assertEquals(Capability.RulesOnly, Governor.capability(batterySaver = false, thermalStatus = 6, charging = true))
        assertEquals(Capability.RulesOnly, Governor.capability(batterySaver = true, thermalStatus = 0, charging = false))
    }

    @Test
    fun batterySaverWhileChargingIsAllowedButStillCpuOnly() {
        assertEquals(Capability.CpuOnly, Governor.capability(batterySaver = true, thermalStatus = 0, charging = true))
    }

    @Test
    fun aLoadedModelIsUnloadedFromSevereHeat() {
        assertEquals(false, Governor.shouldUnload(thermalStatus = 3))
        assertEquals(true, Governor.shouldUnload(thermalStatus = 4))
        assertEquals(true, Governor.shouldUnload(thermalStatus = 6))
    }
}
