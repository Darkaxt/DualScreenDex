package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
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
): ValidationEvidence {
    if (names.compatible) {
        return ValidationEvidence(
            compatible = stats.compatible,
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
    }
    return stats.copy(
        reasons = (stats.reasons + "species names are unavailable; retained the numeric species domain").distinct(),
    )
}

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

/**
 * Proves a fixed ability-name stride only when every complete reference-site consumer multiplies
 * an ability ID by the same immediate and adds that product to the nominated table root.
 */
internal fun compiledAbilityNameStride(session: RomAnalysisSession, root: Int): Int? {
    val index = session.gbaReferenceIndex ?: return null
    val sites = executableGbaTextSites(index, root) ?: return null
    val strides = sites.map { site ->
        compiledFixedStrideConsumer(session.rom, site) ?: return null
    }
    return strides.distinct().singleOrNull()
}

/** Enumerates only roots whose complete compiled consumers prove one fixed name stride. */
internal fun compiledAbilityNameCandidates(session: RomAnalysisSession): Map<Int, Int> {
    val index = session.gbaReferenceIndex?.takeUnless { it.overflowed } ?: return emptyMap()
    return buildMap {
        index.targets.keys.forEach { root ->
            session.cancellation.throwIfCancellationRequested()
            compiledAbilityNameStride(session, root)?.let { stride -> put(root, stride) }
        }
    }
}

private fun compiledFixedStrideConsumer(rom: RomImage, rootLoadSite: Int): Int? {
    if (rootLoadSite < 4 || rootLoadSite + 4 > rom.size) return null
    val move = rom.u16le(rootLoadSite - 4)
    val multiply = rom.u16le(rootLoadSite - 2)
    val rootLoad = rom.u16le(rootLoadSite)
    val add = rom.u16le(rootLoadSite + 2)
    // Strength-reduced fixed strides preserve the same root/product data flow as MUL.
    if (multiply and 0xF800 == 0 && rootLoad and 0xF800 == 0x4800 && add and 0xFE00 == 0x1800) {
        val shift = (multiply ushr 6) and 0x1F
        val product = multiply and 7
        val base = (rootLoad ushr 8) and 7
        if (shift in 1..5 && product != base &&
            setOf((add ushr 3) and 7, (add ushr 6) and 7) == setOf(product, base)
        ) return 1 shl shift
    }
    if (move and 0xF800 != 0x2000 || multiply and 0xFFC0 != 0x4340 ||
        rootLoad and 0xF800 != 0x4800 || add and 0xFE00 != 0x1800
    ) return null

    val scaleRegister = (move ushr 8) and 0x7
    val scale = move and 0xFF
    val multiplyResult = multiply and 0x7
    val multiplyOther = (multiply ushr 3) and 0x7
    if (scale == 0 || scaleRegister != multiplyResult && scaleRegister != multiplyOther) return null

    val rootRegister = (rootLoad ushr 8) and 0x7
    val addFirst = (add ushr 3) and 0x7
    val addSecond = (add ushr 6) and 0x7
    if (rootRegister == multiplyResult ||
        setOf(addFirst, addSecond) != setOf(rootRegister, multiplyResult)
    ) return null
    return scale
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
