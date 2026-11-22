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
        val applicable = buildList {
            definitions.forEach { definition ->
                if (!applicable(definition, context)) return@forEach
                val prior = priorStates[definition.key]
                val shouldEvaluate = prior == null || changedDependencies == null ||
                    definition.dependencies().any { it in changedDependencies }
                val predicate = if (shouldEvaluate) {
                    definition.predicate.evaluate(context)
                } else {
                    PredicateEvaluation(
                        complete = prior.completedAtEpochMs != null,
                        progress = prior.progress,
                        target = prior.target,
                    )
                }
                val next: ChallengeJournalState = if (shouldEvaluate) {
                    nextState(definition, context, prior, predicate, nowEpochMs, saveFingerprint)
                } else requireNotNull(prior)
                states[definition.key] = next
                add(
                    ChallengeResult(
                        definition = definition,
                        progress = next.progress,
                        target = next.target,
                        complete = next.completedAtEpochMs != null,
                        paused = next.paused,
                        missed = next.missed,
                    ),
                )
            }
        }
        return ChallengeEvaluation(
            visible = disclose(applicable, context),
            states = states,
            applicableCount = applicable.size,
            completedCount = applicable.count(ChallengeResult::complete),
        )
    }

    private fun disclose(
        applicable: List<ChallengeResult>,
        context: ChallengeContext,
    ): List<ChallengeResult> {
        if (!context.organicMode) return applicable
        val nextRankByGroup = applicable
            .filterNot(ChallengeResult::complete)
            .mapNotNull { result ->
                val group = result.definition.progressionGroup ?: return@mapNotNull null
                val rank = result.definition.progressionRank ?: return@mapNotNull null
                group to rank
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, ranks) -> ranks.min() }
        return applicable.filter { result ->
            val definition = result.definition
            val chainVisible = definition.progressionGroup?.let { group ->
                result.complete || definition.progressionRank == nextRankByGroup[group]
            } ?: true
            val scopeVisible = definition.disclosureScope?.let { scope ->
                result.complete || (result.progress ?: 0L) > 0L || scope in context.currentCatalogEntities
            } ?: true
            chainVisible && scopeVisible
        }
    }

    private fun nextState(
        definition: ChallengeDefinition,
        context: ChallengeContext,
        prior: ChallengeJournalState?,
        predicate: PredicateEvaluation,
        nowEpochMs: Long,
        saveFingerprint: String?,
    ): ChallengeJournalState {
        if (prior?.completedAtEpochMs != null) {
            return prior.copy(
                target = predicate.target ?: prior.target,
                completedAtSaveFingerprint = prior.completedAtSaveFingerprint ?: saveFingerprint,
                paused = false,
                missed = false,
            )
        }
        val reset = definition.resetWhen?.evaluate(context)?.complete == true
        val missed = definition.missWhen?.evaluate(context)?.complete == true
        val paused = definition.pauseWhen?.evaluate(context)?.complete == true
        return when {
            reset -> ChallengeJournalState(target = predicate.target)
            missed -> (prior ?: ChallengeJournalState()).copy(
                target = predicate.target,
                paused = false,
                missed = true,
            )
            paused -> (prior ?: ChallengeJournalState()).copy(
                target = predicate.target,
                paused = true,
            )
            prior?.missed == true -> prior.copy(target = predicate.target, paused = false)
            else -> ChallengeJournalState(
                progress = predicate.progress ?: if (predicate.complete) 1 else 0,
                target = predicate.target,
                completedAtEpochMs = nowEpochMs.takeIf { predicate.complete },
                completedAtSaveFingerprint = saveFingerprint?.takeIf { predicate.complete },
            )
        }
    }

    private fun ChallengeDefinition.dependencies(): Set<String> = buildSet {
        addAll(predicate.dependencies())
        resetWhen?.dependencies()?.let(::addAll)
        pauseWhen?.dependencies()?.let(::addAll)
        missWhen?.dependencies()?.let(::addAll)
    }

    private fun applicable(definition: ChallengeDefinition, context: ChallengeContext): Boolean =
        context.catalogEntitiesResolved &&
            definition.requiredCapabilities.all { it in context.capabilities && it !in context.unobservableCapabilities } &&
            definition.requiredCatalogEntities.all(context.resolvedCatalogEntities::contains) &&
            definition.requiredAdapters.all(context.provenAdapters::contains) &&
            (!context.organicMode || definition.organicSafe &&
                definition.requiredKnowledgeEntities.all(context.knownCatalogEntities::contains))
}
