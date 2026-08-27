package com.darkaxt.dualdex.progress

class ChallengeEngine {
    fun evaluate(
        definitions: List<ChallengeDefinition>,
        context: ChallengeContext,
        priorStates: Map<String, ChallengeJournalState>,
        changedDependencies: Set<String>? = null,
        nowEpochMs: Long,
        saveFingerprint: String?,
    ): ChallengeEvaluation {
        val states = priorStates.toMutableMap()
        val visible = buildList {
            definitions.forEach { definition ->
                if (!applicable(definition, context)) return@forEach
                val prior = priorStates[definition.key]
                val shouldEvaluate = prior == null || changedDependencies == null ||
                    definition.predicate.dependencies().any { it in changedDependencies }
                val predicate = if (shouldEvaluate) {
                    definition.predicate.evaluate(context)
                } else {
                    PredicateEvaluation(
                        complete = prior.completedAtEpochMs != null,
                        progress = prior.progress,
                    )
                }
                val next: ChallengeJournalState = if (shouldEvaluate) {
                    ChallengeJournalState(
                        progress = predicate.progress ?: if (predicate.complete) 1 else 0,
                        completedAtEpochMs = prior?.completedAtEpochMs ?: nowEpochMs.takeIf { predicate.complete },
                        completedAtSaveFingerprint = prior?.completedAtSaveFingerprint
                            ?: saveFingerprint?.takeIf { predicate.complete },
                    )
                } else requireNotNull(prior)
                states[definition.key] = next
                add(
                    ChallengeResult(
                        definition = definition,
                        progress = predicate.progress ?: next.progress,
                        target = predicate.target,
                        complete = next.completedAtEpochMs != null || predicate.complete,
                    ),
                )
            }
        }
        return ChallengeEvaluation(visible, states)
    }

    private fun applicable(definition: ChallengeDefinition, context: ChallengeContext): Boolean =
        context.catalogEntitiesResolved &&
            definition.requiredCapabilities.all { it in context.capabilities && it !in context.unobservableCapabilities } &&
            definition.requiredCatalogEntities.all(context.resolvedCatalogEntities::contains) &&
            definition.requiredAdapters.all(context.provenAdapters::contains) &&
            (!context.organicMode || definition.organicSafe &&
                definition.requiredKnowledgeEntities.all(context.knownCatalogEntities::contains))
}
