package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.validate.TableValidators

internal data class PublishedPartialBaseStatsResolution(
    val layout: TableLayout,
    val evidence: ValidationEvidence,
)

internal data class PublishedPartialBaseStatsCandidate(
    val layout: TableLayout,
    val rawEvidence: ValidationEvidence,
    val compiledReferences: Int,
    val zeroRows: Int,
)

/**
 * Retains an incomplete standard Gen III base-stat table only when the ROM itself proves both its
 * physical root and its active semantic domain. This does not weaken the ordinary all-slot table
 * validator: malformed rows must be exactly zero-filled and active rows remain reported missing.
 */
internal object Gen3PublishedPartialBaseStatsResolver {
    fun resolve(
        rom: RomImage,
        publishedRoot: Int?,
        names: ValidationEvidence,
        provisionalLayout: ResolvedRomLayout,
    ): PublishedPartialBaseStatsResolution? {
        val candidate = resolveStructuralCandidate(
            rom,
            publishedRoot,
            names,
            provisionalLayout.generation,
            provisionalLayout.speciesCount,
        ) ?: return null
        return confirmCandidate(rom, candidate, names, provisionalLayout)
    }

    fun resolveStructuralCandidate(
        rom: RomImage,
        publishedRoot: Int?,
        names: ValidationEvidence,
        generation: Int,
        speciesCount: Int?,
    ): PublishedPartialBaseStatsCandidate? {
        val root = publishedRoot ?: return null
        val count = speciesCount ?: return null
        if (generation != 3 || !names.compatible || names.totalRecords != count) return null
        val compiledReferences = compiledReferenceCount(rom, root)
        if (compiledReferences < MINIMUM_COMPILED_REFERENCES) return null
        return SUPPORTED_RECORD_SIZES.mapNotNull { recordSize ->
            val table = TableLayout(root, count, recordSize)
            val rawStats = TableValidators.baseStats(rom, root, count, recordSize, generation = 3)
            structuralCandidate(rom, root, rawStats, table, count, compiledReferences)
        }.singleOrNull()
    }

    fun confirmCandidate(
        rom: RomImage,
        candidate: PublishedPartialBaseStatsCandidate,
        names: ValidationEvidence,
        provisionalLayout: ResolvedRomLayout,
    ): PublishedPartialBaseStatsResolution? {
        val count = provisionalLayout.speciesCount ?: return null
        val table = candidate.layout
        val rawStats = candidate.rawEvidence
        if (provisionalLayout.generation != 3 || !names.compatible || names.totalRecords != count ||
            table.count != count
        ) return null
        val candidateLayout = provisionalLayout.copy(
            tables = provisionalLayout.tables.copy(baseStats = table),
        )
        val domain = when (val resolution = SpeciesSemanticDomainResolver.resolveWithEvidence(rom, candidateLayout)) {
            is SpeciesSemanticDomainResolution.Resolved -> resolution.domain
            is SpeciesSemanticDomainResolution.Unavailable,
            is SpeciesSemanticDomainResolution.BudgetExceeded
            -> return null
        }
        if (domain.source != SpeciesSemanticDomainSource.STRONGLY_REFERENCED_REGIONAL_ORDER &&
            domain.source != SpeciesSemanticDomainSource.COMPILED_SPECIES_TO_DEX_MAP
        ) {
            return null
        }
        if (domain.expectedRecords <= 0 ||
            domain.coveredStatRecords.toLong() * COVERAGE_DENOMINATOR <
            domain.expectedRecords.toLong() * MINIMUM_PARTIAL_PERCENT
        ) {
            return null
        }

        return PublishedPartialBaseStatsResolution(
            layout = table,
            evidence = rawStats.copy(
                compatible = true,
                coveredRecords = domain.coveredStatRecords,
                expectedRecords = domain.expectedRecords,
                incompleteRecords = domain.incompleteRecords,
                reasons = (rawStats.reasons + listOf(
                    "retained published Gen 3 base-stat root 0x${table.offset.toString(16).uppercase()} " +
                        "with ${candidate.compiledReferences} compiled references and ${candidate.zeroRows} exact zero-filled rows",
                    "authoritative semantic species coverage ${domain.coveredStatRecords}/${domain.expectedRecords}",
                )).distinct(),
                reviewRecommended = rawStats.reviewRecommended || domain.incompleteRecords > 0,
            ),
        )
    }

    private fun structuralCandidate(
        rom: RomImage,
        root: Int,
        rawStats: ValidationEvidence,
        table: TableLayout,
        count: Int,
        compiledReferences: Int,
    ): PublishedPartialBaseStatsCandidate? {
        if (rawStats.compatible || rawStats.ambiguous) return null
        if (root != table.offset || rawStats.offset != table.offset || rawStats.recordSize != table.recordSize ||
            rawStats.totalRecords != count || table.count != count ||
            rawStats.confidence < MINIMUM_PARTIAL_COVERAGE || table.recordSize !in SUPPORTED_RECORD_SIZES
        ) {
            return null
        }

        val rowClasses = classifyRows(rom, table) ?: return null
        if (rowClasses.valid != rawStats.validRecords || rowClasses.malformed != 0 ||
            rowClasses.valid + rowClasses.zero != count
        ) {
            return null
        }

        return PublishedPartialBaseStatsCandidate(
            layout = table,
            rawEvidence = rawStats,
            compiledReferences = compiledReferences,
            zeroRows = rowClasses.zero,
        )
    }

    private fun classifyRows(rom: RomImage, table: TableLayout): RowClasses? {
        val stride = table.stride ?: table.recordSize
        if (table.offset < 0 || table.count <= 0 || table.recordSize <= 0 || stride < table.recordSize) return null
        val end = table.offset.toLong() + (table.count - 1L) * stride + table.recordSize
        if (end > rom.size.toLong()) return null
        var valid = 0
        var zero = 0
        var malformed = 0
        repeat(table.count) { index ->
            val base = table.offset + index * stride
            val statsValid = (0 until 6).all { rom.u8(base + it) in 1..255 }
            val typesValid = rom.u8(base + 6) in 0..31 && rom.u8(base + 7) in 0..31
            when {
                statsValid && typesValid -> valid++
                (0 until table.recordSize).all { rom.u8(base + it) == 0 } -> zero++
                else -> malformed++
            }
        }
        return RowClasses(valid, zero, malformed)
    }

    private fun compiledReferenceCount(rom: RomImage, target: Int): Int {
        var references = 0
        var instructionOffset = 0
        while (instructionOffset <= rom.size - 2) {
            val instruction = rom.u16le(instructionOffset)
            if (instruction and THUMB_LITERAL_LOAD_MASK == THUMB_LITERAL_LOAD_OPCODE) {
                val pc = (instructionOffset + 4) and -4
                val literalOffset = pc + (instruction and 0xFF) * 4
                if (literalOffset - instructionOffset <= MAX_THUMB_LITERAL_DISTANCE && literalOffset <= rom.size - 4) {
                    val referenced = (rom.u32le(literalOffset) - GBA_ROM_BASE)
                        .takeIf { it in 0 until rom.size.toLong() }
                        ?.toInt()
                    if (referenced == target) references++
                }
            }
            instructionOffset += 2
        }
        return references
    }

    private data class RowClasses(val valid: Int, val zero: Int, val malformed: Int)

    private val SUPPORTED_RECORD_SIZES = setOf(28, 32)
    private const val MINIMUM_COMPILED_REFERENCES = 3
    private const val MINIMUM_PARTIAL_COVERAGE = 0.70
    private const val MINIMUM_PARTIAL_PERCENT = 70L
    private const val COVERAGE_DENOMINATOR = 100L
    private const val THUMB_LITERAL_LOAD_MASK = 0xF800
    private const val THUMB_LITERAL_LOAD_OPCODE = 0x4800
    private const val MAX_THUMB_LITERAL_DISTANCE = 1_024
    private const val GBA_ROM_BASE = 0x08000000L
}
