package app.bheem.eval.privacy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnonymizerTest {
    @Test
    fun messagesWithAnOtpAreDropped() {
        assertNull(Anonymizer.scrub("Your OTP is 482913. Do not share."))
        assertNull(Anonymizer.scrub("123456 is your verification code for login"))
        assertNull(Anonymizer.scrub("Use one-time password 998877 to continue"))
    }

    @Test
    fun vpasAndEmailsAreMasked() {
        assertEquals("Paid to user@upi now", Anonymizer.scrub("Paid to rahul.sharma@okhdfcbank now"))
        assertEquals("mail user@upi please", Anonymizer.scrub("mail a.b@example please"))
    }

    @Test
    fun phoneNumbersAreMasked() {
        assertEquals("call 9999999999 or 9999999999", Anonymizer.scrub("call 9876543210 or +91 9876543210"))
    }

    @Test
    fun longReferencesAndAccountTailsAreMasked() {
        val out = Anonymizer.scrub("debited from A/c XX1234 UPI Ref No 912345678901")!!
        assertTrue("XX0000" in out)
        assertTrue("000000000000" in out)
        assertTrue("1234" !in out)
    }

    @Test
    fun amountsAndMerchantsStayIntact() {
        assertEquals("Sent Rs.840.00 From Bank A/C *0000 To MURUGAN IDLI", Anonymizer.scrub("Sent Rs.840.00 From Bank A/C *4821 To MURUGAN IDLI"))
    }
}
