package com.darkaxt.dualdex.progress

enum class ChallengeCategory { PROGRESS, COLLECTION, EXPLORATION, BATTLE, PARTY, SPECIAL }

enum class NumericComparison { EQUAL, NOT_EQUAL, LESS_THAN, LESS_OR_EQUAL, GREATER_THAN, GREATER_OR_EQUAL }

data class ChallengeContext(
    val metrics: Map<String, Number> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val sets: Map<String, Set<String>> = emptyMap(),
    val sequences: Map<String, List<String>> = emptyMap(),
    val epochs: Map<String, Number> = emptyMap(),
    val previousValues: Map<String, Number> = emptyMap(),
    val currentValues: Map<String, Number> = emptyMap(),
    val capabilities: Set<String> = emptySet(),
    val unobservableCapabilities: Set<String> = emptySet(),
    val catalogEntitiesResolved: Boolean = true,
    val organicMode: Boolean = true,
)

data class PredicateEvaluation(
    val complete: Boolean,
    val progress: Long? = null,
    val target: Long? = null,
)

sealed interface ChallengePredicate {
    fun evaluate(context: ChallengeContext): PredicateEvaluation
    fun dependencies(): Set<String>

    data class BooleanFact(val key: String, val expected: Boolean = true) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext) = PredicateEvaluation(context.booleans[key] == expected)
        override fun dependencies() = setOf("boolean:$key")
    }

    data class All(val predicates: List<ChallengePredicate>) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext): PredicateEvaluation {
            val evaluations = predicates.map { it.evaluate(context) }
            return PredicateEvaluation(evaluations.all { it.complete })
        }
        override fun dependencies() = predicates.flatMapTo(linkedSetOf()) { it.dependencies() }
    }

    data class Any(val predicates: List<ChallengePredicate>) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext) = PredicateEvaluation(predicates.any { it.evaluate(context).complete })
        override fun dependencies() = predicates.flatMapTo(linkedSetOf()) { it.dependencies() }
    }

    data class Not(val predicate: ChallengePredicate) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext) = PredicateEvaluation(!predicate.evaluate(context).complete)
        override fun dependencies() = predicate.dependencies()
    }

    data class CountAtLeast(val metric: String, val target: Long) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext): PredicateEvaluation {
            val current = context.metrics[metric]?.toLong()?.coerceAtLeast(0) ?: 0
            return PredicateEvaluation(current >= target, current, target)
        }
        override fun dependencies() = setOf("metric:$metric")
    }

    data class Compare(val metric: String, val comparison: NumericComparison, val target: Long) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext): PredicateEvaluation {
            val current = context.metrics[metric]?.toLong() ?: return PredicateEvaluation(false)
            return PredicateEvaluation(compare(current, target, comparison), current, target)
        }
        override fun dependencies() = setOf("metric:$metric")
    }

    data class SetContains(val set: String, val value: String) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext) = PredicateEvaluation(value in context.sets[set].orEmpty())
        override fun dependencies() = setOf("set:$set")
    }

    data class SetSizeAtLeast(val set: String, val target: Long) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext): PredicateEvaluation {
            val current = context.sets[set].orEmpty().size.toLong()
            return PredicateEvaluation(current >= target, current, target)
        }
        override fun dependencies() = setOf("set:$set")
    }

    data class Ordered(val sequence: String, val required: List<String>) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext): PredicateEvaluation {
            val actual = context.sequences[sequence].orEmpty()
            var cursor = 0
            actual.forEach { value -> if (cursor < required.size && required[cursor] == value) cursor++ }
            return PredicateEvaluation(cursor == required.size, cursor.toLong(), required.size.toLong())
        }
        override fun dependencies() = setOf("sequence:$sequence")
    }

    data class EpochAtLeast(val epoch: String, val target: Long) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext): PredicateEvaluation {
            val current = context.epochs[epoch]?.toLong() ?: 0
            return PredicateEvaluation(current >= target, current, target)
        }
        override fun dependencies() = setOf("epoch:$epoch")
    }

    data class PreviousCompare(val value: String, val comparison: NumericComparison) : ChallengePredicate {
        override fun evaluate(context: ChallengeContext): PredicateEvaluation {
            val previous = context.previousValues[value]?.toLong() ?: return PredicateEvaluation(false)
            val current = context.currentValues[value]?.toLong() ?: return PredicateEvaluation(false)
            return PredicateEvaluation(compare(current, previous, comparison), current, previous)
        }
        override fun dependencies() = setOf("previous:$value")
    }

    companion object {
        private fun compare(left: Long, right: Long, operator: NumericComparison) = when (operator) {
            NumericComparison.EQUAL -> left == right
            NumericComparison.NOT_EQUAL -> left != right
            NumericComparison.LESS_THAN -> left < right
            NumericComparison.LESS_OR_EQUAL -> left <= right
            NumericComparison.GREATER_THAN -> left > right
            NumericComparison.GREATER_OR_EQUAL -> left >= right
        }
    }
}

data class ChallengeDefinition(
    val key: String,
    val title: String,
    val description: String,
    val category: ChallengeCategory,
    val requiredCapabilities: Set<String>,
    val organicSafe: Boolean,
    val predicate: ChallengePredicate,
    val sourceInspiration: String = "portable-pattern",
)

data class ChallengeResult(
    val definition: ChallengeDefinition,
    val progress: Long?,
    val target: Long?,
    val complete: Boolean,
)

data class ChallengeEvaluation(
    val visible: List<ChallengeResult>,
    val states: Map<String, ChallengeJournalState>,
)

