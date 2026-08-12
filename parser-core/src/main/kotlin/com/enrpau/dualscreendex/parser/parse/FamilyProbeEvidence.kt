package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityEvidence
import com.enrpau.dualscreendex.parser.model.CapabilityReviewStatus
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.validate.Gen3BaseStatAbilitySlots

internal fun speciesCatalogEvidence(
    names: ValidationEvidence,
    stats: ValidationEvidence,
): ValidationEvidence = ValidationEvidence(
    compatible = names.compatible && stats.compatible,
    validRecords = minOf(names.validRecords, stats.validRecords),
    totalRecords = maxOf(names.totalRecords, stats.totalRecords),
    confidence = minOf(names.confidence, stats.confidence),
    reasons = (names.reasons + stats.reasons).distinct(),
    offset = names.offset,
    recordSize = names.recordSize,
    coveredRecords = minOf(
        names.coveredRecords ?: names.validRecords,
        stats.coveredRecords ?: stats.validRecords,
    ),
    expectedRecords = minOf(
        names.expectedRecords ?: names.totalRecords,
        stats.expectedRecords ?: stats.totalRecords,
    ),
    incompleteRecords = maxOf(names.incompleteRecords ?: 0, stats.incompleteRecords ?: 0),
    reviewRecommended = names.reviewRecommended || stats.reviewRecommended,
    ambiguous = names.ambiguous || stats.ambiguous,
)

internal fun capabilityEvidence(
    capability: RomCapability,
    value: ValidationEvidence,
    status: CapabilityStatus = if (value.compatible) CapabilityStatus.AVAILABLE else CapabilityStatus.NOT_FOUND,
): CapabilityEvidence {
    val coveredRecords = value.coveredRecords ?: value.validRecords
    val expectedRecords = value.expectedRecords ?: value.totalRecords
    val partial = value.compatible && expectedRecords > 0 && coveredRecords < expectedRecords
    val resolvedStatus = when {
        value.ambiguous -> CapabilityStatus.AMBIGUOUS
        status == CapabilityStatus.NOT_APPLICABLE -> CapabilityStatus.NOT_APPLICABLE
        partial -> CapabilityStatus.PARTIAL
        else -> status
    }
    return CapabilityEvidence(
        capability = capability,
        compatible = value.compatible,
        confidence = value.confidence,
        offset = value.offset,
        count = value.totalRecords.takeIf { it > 0 },
        recordSize = value.recordSize,
        reasons = value.reasons,
        status = resolvedStatus,
        validRecords = value.validRecords.takeIf { value.totalRecords > 0 },
        totalRecords = value.totalRecords.takeIf { it > 0 },
        elementSize = value.elementSize,
        reviewStatus = if (partial || value.ambiguous || value.reviewRecommended) {
            CapabilityReviewStatus.MANUAL_REVIEW
        } else {
            CapabilityReviewStatus.NONE
        },
        coveredRecords = coveredRecords.takeIf { expectedRecords > 0 },
        expectedRecords = expectedRecords.takeIf { it > 0 },
        incompleteRecords = value.incompleteRecords,
        validatorReviewRecommended = value.reviewRecommended,
    )
}

internal fun selectAbilityNameEvidence(
    exact: Boolean,
    inherited: ValidationEvidence,
    dynamicCandidates: () -> List<ValidationEvidence>,
): ValidationEvidence {
    if (exact) return inherited
    return dynamicCandidates()
        .filter { it.compatible }
        .maxWithOrNull(compareBy<ValidationEvidence> { it.validRecords }.thenBy { it.confidence })
        ?: inherited
}

internal data class AbilityNameBoundary(
    val count: Int,
    val aliasLabelCount: Int,
)

/** Selects the sole structural separator proven to lie above every directly referenced ability ID. */
internal fun semanticAbilityNameBoundary(
    names: List<String>,
    maximumDirectAbilityId: Int,
): AbilityNameBoundary? {
    val candidates = names.indices.asSequence()
        .drop(1)
        .filter { index -> index >= 10 && names[index].isStructuralSentinel() }
        .filter { index -> maximumDirectAbilityId < index }
        .mapNotNull { index ->
            val following = names.drop(index + 1).take(3)
            if (following.size < 2 || following.any { value -> value.none(Char::isLetterOrDigit) }) {
                return@mapNotNull null
            }
            val aliases = names.drop(index + 1).count { it.any(Char::isLetterOrDigit) }
            AbilityNameBoundary(index, aliases).takeIf { it.aliasLabelCount >= 2 }
        }
        .toList()
    return candidates.singleOrNull()
}

private fun String.isStructuralSentinel(): Boolean =
    isNotBlank() && none(Char::isLetterOrDigit)

internal fun maximumDirectAbilityId(
    rom: RomImage,
    baseStats: TableLayout?,
    speciesCount: Int?,
): Int? {
    val table = baseStats ?: return null
    val count = minOf(speciesCount ?: table.count, table.count)
    if (!Gen3BaseStatAbilitySlots.supportsLayout(rom, table, count)) return 0
    var maximum = 0
    repeat(count) { index ->
        Gen3BaseStatAbilitySlots.read(rom, table, index, count).forEach { abilityId ->
            maximum = maxOf(maximum, abilityId)
        }
    }
    return maximum
}
