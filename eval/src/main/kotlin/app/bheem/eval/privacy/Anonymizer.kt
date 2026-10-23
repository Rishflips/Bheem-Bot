package app.bheem.eval.privacy

object Anonymizer {
    private val otp = Regex("""(?i)\b(otp|one[- ]time password|verification code)\b""")
    private val address = Regex("""[\w.\-]+@[\w.\-]+""")
    private val phone = Regex("""(?<!\d)(?:\+?91[\s-]?)?[6-9]\d{9}(?!\d)""")
    private val longNumber = Regex("""\d{12,}""")
    private val accountTail = Regex("""(?<=[Xx*]{1,2})\d{3,4}""")

    fun scrub(text: String): String? {
        if (otp.containsMatchIn(text)) return null
        return text
            .replace(address, "user@upi")
            .replace(phone, "9999999999")
            .replace(longNumber, "000000000000")
            .replace(accountTail, "0000")
    }
}
