package com.enrpau.dualscreendex.parser.family

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionCodec
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityDescriptionResolver
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameCodec
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameResolver
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilityNameRowOutcome
import com.enrpau.dualscreendex.parser.dataset.abilities.AbilitySemanticDomain
import com.enrpau.dualscreendex.parser.dataset.abilities.ResolvedAbilityNameLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityTextResolution
import com.enrpau.dualscreendex.parser.parse.CompiledAbilityTextResolver
import com.enrpau.dualscreendex.parser.resolution.DatasetResolution
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class CompiledAbilitySemanticEvidence(
    val evidence: ValidationEvidence,
    val names: ResolvedAbilityNameLayout?,
)

/** Native ownership precedes text shape; prose failure cannot select a different pointer table. */
internal object CompiledAbilitySemanticResolver {
    fun resolve(
        session: RomAnalysisSession,
        core: TableLayout,
        codec: PokemonTextCodec,
    ): CompiledAbilitySemanticEvidence? {
        val binding = when (val proof = CompiledAbilityTextResolver.resolve(session, core)) {
            is CompiledAbilityTextResolution.Resolved -> proof.binding
            is CompiledAbilityTextResolution.Unavailable -> return null
            is CompiledAbilityTextResolution.Ambiguous -> return failed(
                "multiple independently proved compiled ability-text contracts", ambiguous = true)
            is CompiledAbilityTextResolution.BudgetExceeded -> return failed(
                "compiled ability-text ${proof.kind} budget exceeded (${proof.observed}/${proof.limit})")
        }
        val domain = AbilitySemanticDomain(buildSet {
            for (speciesId in 1 until binding.speciesSlots.speciesCount) {
                session.cancellation.throwIfCancellationRequested()
                addAll(binding.speciesSlots.read(session.rom, speciesId))
            }
        })
        val resolution = AbilityNameResolver(AbilityNameCodec(codec)).resolve(
            session, domain, directCompiledConsumerLayouts = listOf(binding.names))
        val candidate = when (resolution) {
            is DatasetResolution.Resolved -> resolution.candidate
            is DatasetResolution.Partial -> resolution.candidate
            else -> return failed("proved native ability names failed typed selection: ${failure(resolution)}")
        }
        val names = candidate.layout
        if (names.table != binding.names || names.baseRowCount.toLong() != binding.names.count) {
            return failed("typed ability names did not retain the proved complete direct array")
        }
        val descriptionResolution = AbilityDescriptionResolver(AbilityDescriptionCodec(codec)).resolve(
            session, names, directCompiledConsumerLayouts = listOf(binding.descriptions))
        val descriptions = when (descriptionResolution) {
            is DatasetResolution.Resolved -> descriptionResolution.candidate.layout
            is DatasetResolution.Partial -> descriptionResolution.candidate.layout
            else -> null
        }
        val boundNames = ResolvedAbilityNameLayout(
            names.table, names.rows, names.baseRowCount, names.aliasLabels, names.unresolvedActiveAbilityIds,
            compiledTextBinding = binding, compiledDescriptions = descriptions,
            compiledDescriptionFailure = if (descriptions == null) failure(descriptionResolution) else null,
        )
        val covered = domain.activeAbilityIds.count(names.decodedDirectAbilityIds()::contains)
        val decoded = names.baseRows.drop(1).count { it is AbilityNameRowOutcome.Decoded }
        return CompiledAbilitySemanticEvidence(ValidationEvidence(
            compatible = true, validRecords = decoded, totalRecords = names.baseAbilityCount,
            confidence = decoded.toDouble() / names.baseAbilityCount,
            reasons = listOf("validated paired compiled ability text, bounded direct IDs and native species slots") +
                ((resolution as? DatasetResolution.Partial)?.reasons ?: emptyList()),
            offset = names.table.offset.toInt(), recordSize = names.table.nameWidth,
            coveredRecords = covered, expectedRecords = domain.activeAbilityIds.size,
            incompleteRecords = domain.activeAbilityIds.size - covered,
            reviewRecommended = resolution is DatasetResolution.Partial,
        ), boundNames)
    }

    private fun failed(reason: String, ambiguous: Boolean = false) = CompiledAbilitySemanticEvidence(
        ValidationEvidence(false, 0, 0, 0.0, listOf(reason), ambiguous = ambiguous, reviewRecommended = true), null)

    private fun failure(resolution: DatasetResolution<*>): String = when (resolution) {
        is DatasetResolution.Unavailable -> resolution.reasons.joinToString("; ")
        is DatasetResolution.Ambiguous -> "multiple typed candidates"
        is DatasetResolution.BudgetExceeded -> "${resolution.budgetKind} budget exceeded (${resolution.observed}/${resolution.limit}): ${resolution.reason}"
        else -> "typed selection unavailable"
    }
}
