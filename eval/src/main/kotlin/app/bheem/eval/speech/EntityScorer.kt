package app.bheem.eval.speech

import app.bheem.eval.data.CommandCase

data class EntityScore(val total: Map<EntityKind, Int>, val correct: Map<EntityKind, Int>) {
    val totalCount: Int get() = total.values.sum()

    val correctCount: Int get() = correct.values.sum()

    fun errorRate(kind: EntityKind? = null): Double? {
        val all = if (kind == null) totalCount else total[kind] ?: 0
        val right = if (kind == null) correctCount else correct[kind] ?: 0
        return if (all == 0) null else (all - right).toDouble() / all
    }

    operator fun plus(other: EntityScore) = EntityScore(merge(total, other.total), merge(correct, other.correct))

    private fun merge(a: Map<EntityKind, Int>, b: Map<EntityKind, Int>): Map<EntityKind, Int> =
        (a.keys + b.keys).associateWith { (a[it] ?: 0) + (b[it] ?: 0) }

    companion object {
        val EMPTY = EntityScore(emptyMap(), emptyMap())
    }
}

object EntityScorer {
    fun score(entities: List<Entity>, hypothesis: String): EntityScore {
        val heard = SpeechNormalizer.tokens(hypothesis)
        val right = entities.filter { isHeard(it, heard) }
        return EntityScore(
            entities.groupingBy { it.kind }.eachCount(),
            right.groupingBy { it.kind }.eachCount(),
        )
    }

    fun corpus(scores: List<EntityScore>): EntityScore = scores.fold(EntityScore.EMPTY) { sum, s -> sum + s }

    private fun isHeard(entity: Entity, heard: List<String>): Boolean =
        if (entity.kind == EntityKind.AMOUNT) heard.any { Numbers.equal(it, entity.tokens.single()) } else heard.containsSequence(entity.tokens)
}

data class UtteranceScore(val wer: WerScore, val entities: EntityScore) {
    operator fun plus(other: UtteranceScore) = UtteranceScore(wer + other.wer, entities + other.entities)
}

object SpeechScorer {
    fun score(command: CommandCase, hypothesis: String): UtteranceScore = UtteranceScore(
        Wer.scoreText(command.text, hypothesis),
        EntityScorer.score(EntityExtractor.fromCommand(command.text, command.expected), hypothesis),
    )

    fun corpus(scores: List<UtteranceScore>): UtteranceScore =
        scores.fold(UtteranceScore(WerScore.ZERO, EntityScore.EMPTY)) { sum, s -> sum + s }
}
