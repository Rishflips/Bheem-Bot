package app.bheem.eval.speech

data class WerScore(val substitutions: Int, val deletions: Int, val insertions: Int, val referenceWords: Int) {
    val errors: Int get() = substitutions + deletions + insertions

    val rate: Double? get() = if (referenceWords == 0) null else errors.toDouble() / referenceWords

    operator fun plus(other: WerScore) = WerScore(
        substitutions + other.substitutions, deletions + other.deletions,
        insertions + other.insertions, referenceWords + other.referenceWords,
    )

    companion object {
        val ZERO = WerScore(0, 0, 0, 0)
    }
}

object Wer {
    fun score(reference: List<String>, hypothesis: List<String>): WerScore {
        val n = reference.size
        val m = hypothesis.size
        val cost = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) cost[i][0] = i
        for (j in 0..m) cost[0][j] = j
        for (i in 1..n) {
            for (j in 1..m) {
                val diagonal = cost[i - 1][j - 1] + if (reference[i - 1] == hypothesis[j - 1]) 0 else 1
                cost[i][j] = minOf(diagonal, cost[i - 1][j] + 1, cost[i][j - 1] + 1)
            }
        }
        var substitutions = 0
        var deletions = 0
        var insertions = 0
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            val same = i > 0 && j > 0 && reference[i - 1] == hypothesis[j - 1]
            when {
                i > 0 && j > 0 && cost[i][j] == cost[i - 1][j - 1] + if (same) 0 else 1 -> {
                    if (!same) substitutions++
                    i--
                    j--
                }
                i > 0 && cost[i][j] == cost[i - 1][j] + 1 -> {
                    deletions++
                    i--
                }
                else -> {
                    insertions++
                    j--
                }
            }
        }
        return WerScore(substitutions, deletions, insertions, n)
    }

    fun scoreText(reference: String, hypothesis: String): WerScore =
        score(SpeechNormalizer.tokens(reference), SpeechNormalizer.tokens(hypothesis))

    fun corpus(scores: List<WerScore>): WerScore = scores.fold(WerScore.ZERO) { total, s -> total + s }
}
