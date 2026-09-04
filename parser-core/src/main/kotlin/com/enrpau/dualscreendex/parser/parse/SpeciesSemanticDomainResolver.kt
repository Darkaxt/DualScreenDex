package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.catalog.SpeciesIndexResolution
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence

internal enum class SpeciesSemanticDomainSource {
    STRONGLY_REFERENCED_REGIONAL_ORDER,
    COMPILED_SPECIES_TO_DEX_MAP,
    PUBLISHED_POKEDEX_COUNT,
    PUBLISHED_EXPANSION_SPECIES_TABLE,
    NAVIGABLE_SPECIES_FALLBACK,
}

internal data class SpeciesSemanticDomain(
    val expectedSpeciesIds: Set<Int>,
    val coveredStatRecords: Int,
    val excludedStructuralRecords: Int,
    val coveredNameRecords: Int = expectedSpeciesIds.size,
    val activeDomainReason: String? = null,
    val source: SpeciesSemanticDomainSource = SpeciesSemanticDomainSource.NAVIGABLE_SPECIES_FALLBACK,
) {
    val expectedRecords: Int = expectedSpeciesIds.size
    val incompleteRecords: Int = expectedRecords - coveredStatRecords

    fun applyToNames(
        evidence: ValidationEvidence,
        authoritativeFallback: Boolean = false,
    ): ValidationEvidence {
        val incomplete = expectedRecords - coveredNameRecords
        return evidence.copy(
            coveredRecords = coveredNameRecords,
            expectedRecords = expectedRecords,
            incompleteRecords = incomplete,
            reviewRecommended = semanticReviewRecommended(evidence, incomplete, authoritativeFallback),
            reasons = buildList {
                addAll(evidence.reasons)
                addAll(domainReasons())
                if (incomplete > 0) {
                    add(
                        "semantic species-name coverage $coveredNameRecords/$expectedRecords; " +
                            "$incomplete active rows lack a ROM name",
                    )
                }
            },
        )
    }

    fun applyToStats(
        evidence: ValidationEvidence,
        authoritativeFallback: Boolean = false,
    ): ValidationEvidence = evidence.copy(
        coveredRecords = coveredStatRecords,
        expectedRecords = expectedRecords,
        incompleteRecords = incompleteRecords,
        reviewRecommended = semanticReviewRecommended(evidence, incompleteRecords, authoritativeFallback),
        reasons = buildList {
            addAll(evidence.reasons)
            addAll(domainReasons())
            if (incompleteRecords > 0) {
                add(
                    "semantic species coverage $coveredStatRecords/$expectedRecords; " +
                        "$incompleteRecords named positive-Dex records lack valid base stats/types",
                )
            }
        },
    )

    fun applyToLearnsets(
        evidence: ValidationEvidence,
        coveredSpeciesIds: Set<Int>,
        authoritativeFallback: Boolean = false,
    ): ValidationEvidence {
        val covered = expectedSpeciesIds.count(coveredSpeciesIds::contains)
        val incomplete = expectedRecords - covered
        return evidence.copy(
            coveredRecords = covered,
            expectedRecords = expectedRecords,
            incompleteRecords = incomplete,
            reviewRecommended = semanticReviewRecommended(evidence, incomplete, authoritativeFallback),
            reasons = buildList {
                addAll(evidence.reasons)
                addAll(domainReasons())
                if (incomplete > 0) {
                    add(
                        "semantic learnset coverage $covered/$expectedRecords; " +
                            "$incomplete named positive-Dex records lack a usable learnset",
                    )
                }
            },
        )
    }

    fun applyToDescriptions(
        evidence: ValidationEvidence,
        coveredSpeciesIds: Set<Int>,
        authoritativeFallback: Boolean = false,
    ): ValidationEvidence {
        val covered = expectedSpeciesIds.count(coveredSpeciesIds::contains)
        val incomplete = expectedRecords - covered
        return evidence.copy(
            coveredRecords = covered,
            expectedRecords = expectedRecords,
            incompleteRecords = incomplete,
            reviewRecommended = semanticReviewRecommended(evidence, incomplete, authoritativeFallback),
            reasons = buildList {
                addAll(evidence.reasons)
                addAll(domainReasons())
                if (incomplete > 0) {
                    add(
                        "semantic Pokédex description coverage $covered/$expectedRecords; " +
                            "$incomplete navigable species lack a decoded ROM description",
                    )
                }
            },
        )
    }

    fun applyToSprites(
        evidence: ValidationEvidence,
        coveredSpeciesIds: Set<Int>,
        authoritativeFallback: Boolean = false,
    ): ValidationEvidence {
        val covered = expectedSpeciesIds.count(coveredSpeciesIds::contains)
        val incomplete = expectedRecords - covered
        return evidence.copy(
            coveredRecords = covered,
            expectedRecords = expectedRecords,
            incompleteRecords = incomplete,
            reviewRecommended = semanticReviewRecommended(evidence, incomplete, authoritativeFallback),
            reasons = buildList {
                addAll(evidence.reasons)
                addAll(domainReasons())
                if (incomplete > 0) {
                    add(
                        "semantic sprite coverage $covered/$expectedRecords; " +
                            "$incomplete navigable species lack a decodable ROM sprite",
                    )
                }
            },
        )
    }

    private fun domainReasons(): List<String> = activeDomainReason?.let(::listOf) ?: listOf(
        "excluded $excludedStructuralRecords reserved, unnamed, or zero-Dex structural slots " +
            "from the Pokédex semantic domain",
    )

    private fun semanticReviewRecommended(
        evidence: ValidationEvidence,
        incomplete: Int,
        authoritativeFallback: Boolean = false,
    ): Boolean {
        val covered = evidence.coveredRecords ?: evidence.validRecords
        val expected = evidence.expectedRecords ?: evidence.totalRecords
        val physicalGap = expected > 0 && covered < expected
        val authoritative = authoritativeFallback ||
            source == SpeciesSemanticDomainSource.STRONGLY_REFERENCED_REGIONAL_ORDER ||
            source == SpeciesSemanticDomainSource.COMPILED_SPECIES_TO_DEX_MAP ||
            source == SpeciesSemanticDomainSource.PUBLISHED_POKEDEX_COUNT
        return incomplete > 0 ||
            evidence.ambiguous ||
            evidence.reviewRecommended ||
            !authoritative && physicalGap
    }
}

internal sealed interface SpeciesSemanticDomainResolution {
    data class Resolved(val domain: SpeciesSemanticDomain) : SpeciesSemanticDomainResolution

    data class Unavailable(
        val reason: String,
        val ambiguous: Boolean,
    ) : SpeciesSemanticDomainResolution

    data class BudgetExceeded(
        val reason: String,
        val budgetKind: com.enrpau.dualscreendex.parser.resolution.BudgetKind,
        val observed: Long,
        val limit: Long,
    ) : SpeciesSemanticDomainResolution
}

internal object SpeciesSemanticDomainResolver {
    fun resolve(rom: RomImage, layout: ResolvedRomLayout): SpeciesSemanticDomain =
        when (val resolution = resolveWithEvidence(rom, layout)) {
            is SpeciesSemanticDomainResolution.Resolved -> resolution.domain
            is SpeciesSemanticDomainResolution.Unavailable -> error(resolution.reason)
            is SpeciesSemanticDomainResolution.BudgetExceeded -> error(resolution.reason)
        }

    fun resolveWithEvidence(rom: RomImage, layout: ResolvedRomLayout): SpeciesSemanticDomainResolution {
        require(layout.generation == 3) { "species semantic domains currently require a Gen 3 layout" }
        val rawCount = layout.speciesCount ?: layout.tables.speciesNames?.count ?: 0
        val materialization = RecordMaterializers.speciesWithIndexResolution(rom, layout)
        when (val indexResolution = materialization.indexResolution) {
            is SpeciesIndexResolution.Resolved -> Unit
            is SpeciesIndexResolution.Unavailable -> {
                return SpeciesSemanticDomainResolution.Unavailable(
                    indexResolution.reason,
                    indexResolution.ambiguous,
                )
            }
            is SpeciesIndexResolution.BudgetExceeded -> {
                return SpeciesSemanticDomainResolution.BudgetExceeded(
                    indexResolution.reason,
                    indexResolution.budgetKind,
                    indexResolution.observed,
                    indexResolution.limit,
                )
            }
        }
        val species = materialization.records.values
        val mapped = species.filter { record -> (record.dexNumber.value ?: 0) > 0 }
        val navigable = mapped.filter { record -> record.name.value?.any(Char::isLetterOrDigit) == true }
        val expansionDomain = layout.pokeemeraldExpansion != null
        val expansionActive = if (expansionDomain) {
            species.filter { record ->
                (record.dexNumber.value ?: 0) > 0 &&
                    (record.name.value?.any(Char::isLetterOrDigit) == true ||
                        record.baseStats.status == CapabilityStatus.AVAILABLE ||
                        record.typeIds.status == CapabilityStatus.AVAILABLE)
            }
        } else {
            emptyList()
        }
        val compiledReferences = compiledGbaReferenceCounts(rom)
        val regionalOrder = resolveStrongRegionalDexOrder(
            rom = rom,
            rawSpeciesCount = rawCount,
            navigableDexNumbers = mapped.associate { it.id to (it.dexNumber.value ?: 0) },
            references = compiledReferences,
        )
        val compiledSpeciesToDexMap = if (regionalOrder != null || expansionDomain) {
            null
        } else {
            resolveCompiledSpeciesToDexMap(
                rom = rom,
                rawSpeciesCount = rawCount,
                dexNumbers = materialization.indexResolution.values,
                references = compiledReferences,
                descriptionCount = layout.tables.descriptions?.count,
            )
        }
        val publishedPokedexCount = layout.defaultTextCodec()
            ?.let { codec -> GbaPublishedHeaderResolver.resolve(rom, codec).pokedexCount }
            ?.takeIf { count -> layout.tables.descriptions?.count == count }
        val publishedPokedexDomain = if (
            regionalOrder == null && compiledSpeciesToDexMap == null && !expansionDomain &&
            publishedPokedexCount != null
        ) {
            mapped.filter { record -> (record.dexNumber.value ?: 0) in 1 until publishedPokedexCount }
                .takeIf { records -> records.map { it.dexNumber.value }.distinct().size == records.size }
        } else {
            null
        }
        val speciesById = species.associateBy { it.id }
        val expected = regionalOrder?.speciesIds?.mapNotNull { speciesId ->
            speciesById[speciesId]
        } ?: if (expansionDomain) {
            expansionActive
        } else if (publishedPokedexDomain != null) {
            publishedPokedexDomain
        } else if (compiledSpeciesToDexMap != null) {
            mapped.filterNot { it.id in compiledSpeciesToDexMap.reservedOverflowSpeciesIds }
        } else {
            navigable
        }
        if (expected.isEmpty()) {
            return SpeciesSemanticDomainResolution.Unavailable(
                "no authoritative non-empty species semantic domain was resolved",
                false,
            )
        }
        val covered = expected.count { record ->
            record.baseStats.status == CapabilityStatus.AVAILABLE &&
                record.typeIds.status == CapabilityStatus.AVAILABLE
        }
        return SpeciesSemanticDomainResolution.Resolved(SpeciesSemanticDomain(
            expectedSpeciesIds = expected.mapTo(linkedSetOf()) { it.id },
            coveredStatRecords = covered,
            excludedStructuralRecords = (rawCount - expected.size).coerceAtLeast(0),
            coveredNameRecords = expected.count { record -> record.name.value?.any(Char::isLetterOrDigit) == true },
            activeDomainReason = regionalOrder?.let {
                "selected ${it.speciesIds.size} species from the strongly compiled-referenced regional " +
                    "Pokédex order at 0x${it.tableOffset.toString(16).uppercase()}; excluded " +
                    "${(rawCount - it.speciesIds.size).coerceAtLeast(0)} installed expansion slots outside " +
                    "the ROM's active Pokédex domain"
            } ?: compiledSpeciesToDexMap?.let {
                "selected ${expected.size} mapped species through the complete compiled-referenced " +
                    "species-to-Dex map at 0x${it.tableOffset.toString(16).uppercase()}; excluded " +
                    "${(rawCount - expected.size).coerceAtLeast(0)} reserved or zero-Dex structural slots" +
                    if (it.reservedOverflowSpeciesIds.isNotEmpty()) {
                        ", including ${it.reservedOverflowSpeciesIds.size} species in a structurally bounded " +
                            "reserved Dex overflow block"
                    } else {
                        ""
                    }
            } ?: publishedPokedexDomain?.let {
                "selected ${it.size} mapped species inside the structurally published " +
                    "Pokédex count $publishedPokedexCount; excluded " +
                    "${(rawCount - it.size).coerceAtLeast(0)} internal or out-of-domain slots"
            } ?: if (expansionDomain) {
                "selected ${expected.size} positive-Dex named or populated species from the published " +
                    "pokeemerald-expansion gSpeciesInfo table; excluded " +
                    "${(rawCount - expected.size).coerceAtLeast(0)} zero-Dex or empty structural slots"
            } else {
                null
            },
            source = if (regionalOrder != null) {
                SpeciesSemanticDomainSource.STRONGLY_REFERENCED_REGIONAL_ORDER
            } else if (compiledSpeciesToDexMap != null) {
                SpeciesSemanticDomainSource.COMPILED_SPECIES_TO_DEX_MAP
            } else if (publishedPokedexDomain != null) {
                SpeciesSemanticDomainSource.PUBLISHED_POKEDEX_COUNT
            } else if (expansionDomain) {
                SpeciesSemanticDomainSource.PUBLISHED_EXPANSION_SPECIES_TABLE
            } else {
                SpeciesSemanticDomainSource.NAVIGABLE_SPECIES_FALLBACK
            },
        ))
    }

    private fun resolveStrongRegionalDexOrder(
        rom: RomImage,
        rawSpeciesCount: Int,
        navigableDexNumbers: Map<Int, Int>,
        references: Map<Int, Int>,
    ): RegionalDexOrder? {
        if (rawSpeciesCount <= 1) return null
        val candidates = references.mapNotNull { (countOffset, countReferences) ->
            if (countReferences < MINIMUM_REGIONAL_COUNT_REFERENCES || countOffset > rom.size - 2) {
                return@mapNotNull null
            }
            val tableOffset = countOffset + 2
            val tableReferences = references[tableOffset] ?: return@mapNotNull null
            if (tableReferences < MINIMUM_REGIONAL_TABLE_REFERENCES) return@mapNotNull null
            val count = rom.u16le(countOffset)
            if (count !in 2 until rawSpeciesCount || tableOffset.toLong() + count.toLong() * 2 > rom.size) {
                return@mapNotNull null
            }
            val speciesIds = List(count) { index -> rom.u16le(tableOffset + index * 2) }
            if (speciesIds.distinct().size != count || speciesIds.any { it !in 1 until rawSpeciesCount }) {
                return@mapNotNull null
            }
            val dexNumbers = speciesIds.map { speciesId ->
                navigableDexNumbers[speciesId] ?: return@mapNotNull null
            }
            if (dexNumbers.distinct().size != count) return@mapNotNull null
            val orderedDexRatio = dexNumbers.zipWithNext().count { (first, second) -> second > first }
                .toDouble() / (count - 1)
            if (orderedDexRatio < MINIMUM_ORDERED_DEX_RATIO) return@mapNotNull null
            RegionalDexOrder(
                tableOffset = tableOffset,
                speciesIds = speciesIds,
                countReferences = countReferences,
                tableReferences = tableReferences,
                orderedDexRatio = orderedDexRatio,
            )
        }
        val ranked = candidates.sortedWith(
            compareByDescending<RegionalDexOrder> { minOf(it.countReferences, it.tableReferences) }
                .thenByDescending { it.countReferences + it.tableReferences }
                .thenByDescending { it.orderedDexRatio }
                .thenByDescending { it.speciesIds.size },
        )
        val first = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        return first.takeUnless { second != null && first.sameStrengthAs(second) }
    }

    private fun resolveCompiledSpeciesToDexMap(
        rom: RomImage,
        rawSpeciesCount: Int,
        dexNumbers: Map<Int, Int>,
        references: Map<Int, Int>,
        descriptionCount: Int?,
    ): CompiledSpeciesToDexMap? {
        if (rawSpeciesCount <= 2) return null
        val values = (1 until rawSpeciesCount).map { speciesId ->
            dexNumbers[speciesId] ?: return null
        }
        if (values.withIndex().all { (index, dex) -> dex == index + 1 }) return null
        val positive = values.filter { it > 0 }
        if (positive.size < (values.size * MINIMUM_COMPILED_MAP_POSITIVE_RATIO).toInt()) return null
        val distinctRatio = positive.distinct().size.toDouble() / positive.size.coerceAtLeast(1)
        if (distinctRatio < MINIMUM_COMPILED_MAP_DISTINCT_RATIO) return null
        val encoded = ByteArray(values.size * 2)
        values.forEachIndexed { index, value ->
            if (value !in 0..0xFFFF) return null
            encoded[index * 2] = value.toByte()
            encoded[index * 2 + 1] = (value ushr 8).toByte()
        }
        val ranked = rom.findAll(encoded)
            .mapNotNull { offset ->
                val referenceCount = references[offset] ?: 0
                CompiledSpeciesToDexMap(
                    offset,
                    referenceCount,
                    reservedDexOverflowSpeciesIds(values, descriptionCount),
                )
                    .takeIf { referenceCount >= MINIMUM_COMPILED_MAP_REFERENCES }
            }
            .sortedByDescending(CompiledSpeciesToDexMap::referenceCount)
        val first = ranked.firstOrNull() ?: return null
        val second = ranked.getOrNull(1)
        return first.takeUnless { second != null && first.referenceCount == second.referenceCount }
    }

    private fun reservedDexOverflowSpeciesIds(values: List<Int>, descriptionCount: Int?): Set<Int> {
        if (descriptionCount == null || descriptionCount <= 1) return emptySet()
        val identityPrefix = values.indices.firstOrNull { values[it] != it + 1 } ?: return emptySet()
        if (descriptionCount <= identityPrefix ||
            identityPrefix < MINIMUM_RESERVED_DEX_SEQUENCE ||
            values[identityPrefix] != descriptionCount
        ) {
            return emptySet()
        }
        var cursor = identityPrefix
        while (cursor < values.size && values[cursor] == descriptionCount + cursor - identityPrefix) cursor++
        val overflowLength = cursor - identityPrefix
        var resumedLength = 0
        while (cursor + resumedLength < values.size &&
            values[cursor + resumedLength] == identityPrefix + resumedLength + 1
        ) {
            resumedLength++
        }
        if (overflowLength < MINIMUM_RESERVED_DEX_SEQUENCE ||
            resumedLength < MINIMUM_RESERVED_DEX_SEQUENCE
        ) {
            return emptySet()
        }
        return (identityPrefix until cursor).mapTo(linkedSetOf()) { it + 1 }
    }

    private fun compiledGbaReferenceCounts(rom: RomImage): Map<Int, Int> {
        val references = linkedMapOf<Int, Int>()
        var instructionOffset = 0
        while (instructionOffset <= rom.size - 2) {
            val instruction = rom.u16le(instructionOffset)
            if (instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE) {
                val pc = (instructionOffset + 4) and -4
                val literalOffset = pc + (instruction and 0xFF) * 4
                if (literalOffset - instructionOffset <= MAX_THUMB_LITERAL_DISTANCE &&
                    literalOffset <= rom.size - 4
                ) {
                    val target = (rom.u32le(literalOffset) - GBA_ROM_BASE)
                        .takeIf { it in 0 until rom.size.toLong() }
                        ?.toInt()
                    target?.let { references[it] = (references[it] ?: 0) + 1 }
                }
            }
            instructionOffset += 2
        }
        return references
    }

    private data class RegionalDexOrder(
        val tableOffset: Int,
        val speciesIds: List<Int>,
        val countReferences: Int,
        val tableReferences: Int,
        val orderedDexRatio: Double,
    ) {
        fun sameStrengthAs(other: RegionalDexOrder): Boolean =
            minOf(countReferences, tableReferences) == minOf(other.countReferences, other.tableReferences) &&
                countReferences + tableReferences == other.countReferences + other.tableReferences &&
                orderedDexRatio == other.orderedDexRatio
    }

    private data class CompiledSpeciesToDexMap(
        val tableOffset: Int,
        val referenceCount: Int,
        val reservedOverflowSpeciesIds: Set<Int>,
    )

    private const val MINIMUM_REGIONAL_COUNT_REFERENCES = 3
    private const val MINIMUM_REGIONAL_TABLE_REFERENCES = 3
    private const val MINIMUM_ORDERED_DEX_RATIO = 0.9
    private const val MINIMUM_COMPILED_MAP_POSITIVE_RATIO = 0.80
    private const val MINIMUM_COMPILED_MAP_DISTINCT_RATIO = 0.90
    private const val MINIMUM_COMPILED_MAP_REFERENCES = 2
    // Three consecutive values provide two adjacent confirmations for each structural sequence;
    // a lone value or transition is not enough evidence to classify species as reserved.
    private const val MINIMUM_RESERVED_DEX_SEQUENCE = 3
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
    private const val MAX_THUMB_LITERAL_DISTANCE = 1_024
    private const val GBA_ROM_BASE = 0x08000000L
}
