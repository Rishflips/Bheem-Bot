package app.bheem.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtsQueryTest {
    @Test
    fun wordsBecomeQuotedPrefixTermsJoinedWithOr() {
        assertEquals("\"keep\"* OR \"scooter\"* OR \"key\"*", FtsQuery.build("where did I keep the scooter key"))
    }

    @Test
    fun aPluralInTheQuestionStillMatchesTheStoredWord() {
        assertEquals("\"task\"*", FtsQuery.build("my tasks"))
    }

    @Test
    fun quotesAndOperatorsCannotChangeTheQuery() {
        assertEquals("\"anita\"* OR \"tea\"*", FtsQuery.build("Anita\" OR NEAR(tea) -x*"))
    }

    @Test
    fun aQueryWithOnlyFillerWordsHasNoSearch() {
        assertNull(FtsQuery.build("what is the"))
        assertNull(FtsQuery.build(""))
    }
}
