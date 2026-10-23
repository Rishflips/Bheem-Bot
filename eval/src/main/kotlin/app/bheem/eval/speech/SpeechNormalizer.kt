package app.bheem.eval.speech

import java.text.Normalizer

object SpeechNormalizer {
    private val apostropheVariants = Regex("""[‘’ʼ`]""")
    private val innerApostrophe = Regex("""(?<=\p{L})'(?=\p{L})""")
    private val digitGroupComma = Regex("""(?<=\d),(?=\d)""")
    private val zeroMinutes = Regex("""(\d{1,2}):00(?!\d)""")
    private val zeroDecimals = Regex("""(\d)\.0+(?!\d)""")
    private val tokenPattern = Regex("""\d+(?:[.:]\d+)?|[\p{L}\p{M}]+""")
    private val numeric = Regex("""\d+(?::\d+)?""")

    private val fillers = setOf("um", "umm", "uh", "uhh", "hmm", "mm", "mhm", "erm")
    private val currency = setOf("rupees", "rupee", "rs", "inr", "rupaye")
    private val compounds = listOf(
        listOf("whats", "app") to "whatsapp",
        listOf("wi", "fi") to "wifi",
        listOf("big", "basket") to "bigbasket",
    )

    private val units = listOf("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine")
        .withIndex().associate { it.value to it.index.toLong() }
    private val teens = listOf("ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen")
        .withIndex().associate { it.value to 10L + it.index }
    private val tens = listOf("twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")
        .withIndex().associate { it.value to 20L + 10L * it.index }
    private val scales = mapOf("hundred" to 100L, "thousand" to 1_000L, "lakh" to 100_000L, "lakhs" to 100_000L, "crore" to 10_000_000L)

    fun normalize(text: String): String = tokens(text).joinToString(" ")

    fun tokens(text: String): List<String> {
        val cleaned = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
            .let { apostropheVariants.replace(it, "'") }
            .let { innerApostrophe.replace(it, "") }
            .let { digitGroupComma.replace(it, "") }
            .let { zeroMinutes.replace(it, "$1") }
            .let { zeroDecimals.replace(it, "$1") }
        val words = tokenPattern.findAll(cleaned).map { it.value }.filter { it !in fillers }.toList()
        val joined = joinMeridiem(joinNumberWords(words))
        return mergeCompounds(joined.filter { it !in currency })
    }

    private fun isNumberWord(t: String) = t in units || t in teens || t in tens || t in scales

    private fun joinNumberWords(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val implicitOne = (tokens[i] == "a" || tokens[i] == "an") && i + 1 < tokens.size && tokens[i + 1] in scales
            var j = if (implicitOne) i + 1 else i
            if (!isNumberWord(tokens[j])) {
                out += tokens[i]
                i++
                continue
            }
            val run = mutableListOf<String>()
            if (implicitOne) run += "one"
            while (j < tokens.size) {
                val t = tokens[j]
                val andBeforeNumber = t == "and" && run.last() in scales &&
                    j + 1 < tokens.size && isNumberWord(tokens[j + 1]) && tokens[j + 1] !in scales
                when {
                    isNumberWord(t) -> { run += t; j++ }
                    andBeforeNumber -> j++
                    else -> break
                }
            }
            out += value(run)
            i = j
        }
        return out
    }

    private fun value(run: List<String>): String {
        if (run.size >= 2 && run.all { it in units }) return run.joinToString("") { units.getValue(it).toString() }
        var total = 0L
        var current = 0L
        for (w in run) {
            when {
                w in units -> current += units.getValue(w)
                w in teens -> current += teens.getValue(w)
                w in tens -> current += tens.getValue(w)
                w == "hundred" -> current = (if (current == 0L) 1L else current) * 100L
                else -> {
                    total += (if (current == 0L) 1L else current) * scales.getValue(w)
                    current = 0L
                }
            }
        }
        return (total + current).toString()
    }

    private fun joinMeridiem(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val afterNumber = out.lastOrNull()?.let { numeric.matches(it) } == true
            if ((t == "a" || t == "p") && afterNumber && tokens.getOrNull(i + 1) == "m") {
                out += t + "m"
                i += 2
            } else {
                out += t
                i++
            }
        }
        return out
    }

    private fun mergeCompounds(tokens: List<String>): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        while (i < tokens.size) {
            val match = compounds.firstOrNull { (parts, _) -> tokens.subList(i, minOf(tokens.size, i + parts.size)) == parts }
            if (match != null) {
                out += match.second
                i += match.first.size
            } else {
                out += tokens[i]
                i++
            }
        }
        return out
    }
}
