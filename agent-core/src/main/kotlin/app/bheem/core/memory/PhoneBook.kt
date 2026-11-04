package app.bheem.core.memory

import app.bheem.core.pipeline.PhoneLookup

/**
 * Reads phone numbers out of the owner's own fact notes ("Amma's number is 98765 43210"). A number is only ever taken from a note the owner
 * wrote, never from the model, and a note that is not phrased as a number for a name is ignored.
 */
class PhoneBook(records: List<MemoryRecord>) : PhoneLookup {
    private val entries: List<Pair<String, String>> = records
        .filter { it.kind == RecordKind.NOTE }
        .sortedByDescending { it.createdAt }
        .mapNotNull { parse(it.text) }

    override fun find(name: String): String? {
        val wanted = name.trim().lowercase()
        if (wanted.isEmpty()) return null
        return entries.firstOrNull { it.first == wanted }?.second
    }

    private companion object {
        val FACT = Regex(
            """^\s*([\p{L}][\p{L}'. ]{0,30}?)(?:'s|’s)?\s+(?:(?:phone|mobile|cell|contact)\s+)?(?:number|no\.?)\s*(?:is|:|-)?\s*(\+?[0-9][0-9 \-]{4,18}[0-9])\s*$""",
            RegexOption.IGNORE_CASE,
        )
        val COLON = Regex("""^\s*([\p{L}][\p{L}'. ]{0,30}?)\s*:\s*(\+?[0-9][0-9 \-]{4,18}[0-9])\s*$""")

        fun parse(text: String): Pair<String, String>? {
            val m = FACT.find(text) ?: COLON.find(text) ?: return null
            val number = m.groupValues[2].filter { it.isDigit() || it == '+' }
            val digits = number.count { it.isDigit() }
            if (digits !in 6..15 || number.drop(1).contains('+')) return null
            return m.groupValues[1].trim().lowercase() to number
        }
    }
}
