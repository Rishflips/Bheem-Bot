package app.bheem.core.memory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FactSlotsTest {
    @Test
    fun singleValueFactsKnowTheirSlot() {
        assertEquals("name", FactSlots.slot("my name is Rish"))
        assertEquals("name", FactSlots.slot("Call me Rish"))
        assertEquals("city", FactSlots.slot("I live in Chennai"))
        assertEquals("city", FactSlots.slot("I stay in Chennai"))
        assertEquals("city", FactSlots.slot("I moved to Bengaluru"))
        assertEquals("work", FactSlots.slot("I work at Infosys"))
        assertEquals("work", FactSlots.slot("I work for Infosys"))
        assertEquals("work", FactSlots.slot("I work as a teacher"))
        assertEquals("work", FactSlots.slot("my job is teaching"))
        assertEquals("birthday", FactSlots.slot("my birthday is in May"))
        assertEquals("partner", FactSlots.slot("my wife is Meena"))
        assertEquals("partner", FactSlots.slot("my husband is Arun"))
        assertEquals("partner", FactSlots.slot("my partner is Riya"))
        assertNull(FactSlots.slot("I like tea"))
        assertNull(FactSlots.slot("my sister is called Meena"))
    }

    @Test
    fun factsAreGroupedByKindForTheMemoryScreen() {
        assertEquals(FactKind.ABOUT_YOU, FactSlots.kind("my name is Rish"))
        assertEquals(FactKind.ABOUT_YOU, FactSlots.kind("I live in Chennai"))
        assertEquals(FactKind.ABOUT_YOU, FactSlots.kind("I work at Infosys"))
        assertEquals(FactKind.ABOUT_YOU, FactSlots.kind("my birthday is in May"))
        assertEquals(FactKind.ABOUT_YOU, FactSlots.kind("I speak Tamil"))
        assertEquals(FactKind.PEOPLE, FactSlots.kind("my wife is Meena"))
        assertEquals(FactKind.PEOPLE, FactSlots.kind("my sister is called Meena"))
        assertEquals(FactKind.PREFERENCES, FactSlots.kind("I like filter coffee"))
        assertEquals(FactKind.ROUTINE, FactSlots.kind("I usually wake up at 6"))
        assertEquals(FactKind.GOALS, FactSlots.kind("I want a house of my own"))
        assertEquals(FactKind.OTHER, FactSlots.kind("The sky is blue"))
    }

    @Test
    fun interviewFallbackTopicsKeepTheirKind() {
        assertEquals(FactKind.ABOUT_YOU, FactSlots.kind("Work: I teach at a school"))
        assertEquals(FactKind.ABOUT_YOU, FactSlots.kind("Languages: Tamil and English"))
        assertEquals(FactKind.PEOPLE, FactSlots.kind("People: Meena and Arun"))
        assertEquals(FactKind.PREFERENCES, FactSlots.kind("Preferences: Filter coffee"))
        assertEquals(FactKind.ROUTINE, FactSlots.kind("Routine: I wake at 6"))
        assertEquals(FactKind.GOALS, FactSlots.kind("Goals: A house of my own"))
    }
}
