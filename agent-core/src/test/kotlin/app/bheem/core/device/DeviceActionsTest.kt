package app.bheem.core.device

import app.bheem.core.action.ProposedAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceActionsTest {
    private fun parse(text: String) = DeviceActions.parse(text)

    private fun action(name: String, vararg args: Pair<String, String>) = ProposedAction(name, mapOf(*args))

    @Test
    fun theTorchTurnsOnAndOff() {
        assertEquals(action("Flashlight", "state" to "on"), parse("turn on the torch"))
        assertEquals(action("Flashlight", "state" to "off"), parse("Turn off the flashlight."))
        assertEquals(action("Flashlight", "state" to "on"), parse("torch on"))
        assertEquals(action("Flashlight", "state" to "on"), parse("please turn the torch on"))
    }

    @Test
    fun aTimerIsSetInSeconds() {
        assertEquals(action("SetTimer", "seconds" to "600"), parse("set a 10 minute timer"))
        assertEquals(action("SetTimer", "seconds" to "300"), parse("set a timer for 5 minutes"))
        assertEquals(action("SetTimer", "seconds" to "30"), parse("start a 30 second timer"))
        assertEquals(action("SetTimer", "seconds" to "5400"), parse("timer for 90 min"))
        assertEquals(action("SetTimer", "seconds" to "7200"), parse("set a 2 hour timer"))
    }

    @Test
    fun anAppIsOpenedByItsName() {
        assertEquals(action("OpenApp", "app" to "WhatsApp"), parse("open WhatsApp"))
        assertEquals(action("OpenApp", "app" to "Google Maps"), parse("launch Google Maps"))
    }

    @Test
    fun theVolumeGoesUpDownOrOff() {
        assertEquals(action("SetVolume", "change" to "down"), parse("turn the volume down"))
        assertEquals(action("SetVolume", "change" to "up"), parse("volume up"))
        assertEquals(action("SetVolume", "change" to "mute"), parse("mute the phone"))
        assertEquals(action("SetVolume", "change" to "up"), parse("turn up the volume"))
    }

    @Test
    fun anythingElseIsNotADeviceAction() {
        assertNull(parse("remind me tomorrow at 9 to call Arun"))
        assertNull(parse("what is the torch"))
        assertNull(parse("set a timer"))
        assertNull(parse("open"))
        assertNull(parse("open a route to the office and call Ravi and then message him"))
        assertNull(parse("tell me something about volcanoes"))
    }
}
