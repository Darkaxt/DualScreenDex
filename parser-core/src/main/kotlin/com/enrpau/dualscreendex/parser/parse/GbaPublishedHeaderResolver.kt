package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.validate.TableValidators

/** Pointers exposed by the Game Freak ROM header shared with external tools. */
internal data class GbaHeaderPointers(
    val speciesNames: Int? = null,
    val moveNames: Int? = null,
    val sprites: Int? = null,
    val baseStats: Int? = null,
    val abilities: Int? = null,
    val abilityDescriptions: Int? = null,
    val moveData: Int? = null,
    val pokedexCount: Int? = null,
    val publishedDataState: GbaPublishedDataState = GbaPublishedDataState.ABSENT,
    val publishedDataEvidence: ValidationEvidence? = null,
)

internal enum class GbaPublishedDataState { RESOLVED, ABSENT, AMBIGUOUS }

/**
 * Resolves known layouts of the published Gen III pointer block.
 *
 * Older decompilation projects place species, ability, item, move, and ball roots at 0x1AC,
 * while the retained retail/DPE header places the same seven consecutive pointers at 0x1BC.
 * Complete runs are ranked by whether their base-stat and move-data roles validate against the
 * independently published name-table counts. Pointer completeness is only a fallback when no
 * candidate has semantic evidence; equally credible windows are rejected rather than address-tied.
 */
internal object GbaPublishedHeaderResolver {
    fun resolve(
        rom: RomImage,
        codec: PokemonTextCodec,
    ): GbaHeaderPointers {
        val speciesCount = inferredNameCount(rom, SPECIES_NAMES_SLOT, SPECIES_NAME_WIDTH, 2_048, codec)
        val moveCount = inferredNameCount(rom, MOVE_NAMES_SLOT, MOVE_NAME_WIDTH, 2_048, codec)
        val candidates = listOf(COMPACT_DATA_ROOT, FREE_SEEN_FLAGS_DATA_ROOT, STANDARD_DATA_ROOT)
            .map { start ->
                PublishedBlockCandidate(
                    start = start,
                    pointerRun = pointerRun(rom, start),
                    semanticRoles = semanticRoles(rom, start, speciesCount, moveCount),
                )
            }
        val bestSemanticRoles = candidates.maxOfOrNull { it.semanticRoles } ?: 0
        val semanticLeaders = if (bestSemanticRoles > 0) {
            candidates.filter { it.semanticRoles == bestSemanticRoles }
        } else {
            candidates
        }
        val bestPointerRun = semanticLeaders.maxOfOrNull { it.pointerRun } ?: 0
        val pointerBlock = semanticLeaders
            .filter { it.pointerRun == bestPointerRun && it.pointerRun > 0 }
            .singleOrNull()
            ?.start
        val publishedDataState = when {
            pointerBlock != null -> GbaPublishedDataState.RESOLVED
            bestPointerRun > 0 -> GbaPublishedDataState.AMBIGUOUS
            else -> GbaPublishedDataState.ABSENT
        }
        val publishedDataEvidence = if (publishedDataState == GbaPublishedDataState.AMBIGUOUS) {
            ValidationEvidence(
                compatible = false,
                validRecords = 0,
                totalRecords = semanticLeaders.count { it.pointerRun == bestPointerRun },
                confidence = 0.0,
                reasons = listOf(
                    "ambiguous published Gen 3 data-pointer blocks: " + semanticLeaders
                        .filter { it.pointerRun == bestPointerRun }
                        .joinToString { "0x${it.start.toString(16)}" },
                ),
                ambiguous = true,
                reviewRecommended = true,
            )
        } else {
            null
        }

        val speciesNames = rom.pointerOrNull(SPECIES_NAMES_SLOT)
        val moveNames = rom.pointerOrNull(MOVE_NAMES_SLOT)
        val sprites = rom.pointerOrNull(SPRITES_SLOT)
        val publishedPokedexCount = if (POKEDEX_COUNT_SLOT <= rom.size - 4) {
            rom.u32le(POKEDEX_COUNT_SLOT)
                .takeIf { speciesCount != null && it in 2L..speciesCount.toLong() }
                ?.toInt()
                ?.takeIf { speciesNames != null && moveNames != null && sprites != null }
        } else {
            null
        }
        return GbaHeaderPointers(
            speciesNames = speciesNames,
            moveNames = moveNames,
            sprites = sprites,
            baseStats = pointerBlock?.let { rom.pointerOrNull(it) },
            abilities = pointerBlock?.let { rom.pointerOrNull(it + 4) },
            abilityDescriptions = pointerBlock?.let { rom.pointerOrNull(it + 8) },
            moveData = pointerBlock?.let { rom.pointerOrNull(it + 16) },
            pokedexCount = publishedPokedexCount,
            publishedDataState = publishedDataState,
            publishedDataEvidence = publishedDataEvidence,
        )
    }

    private fun inferredNameCount(
        rom: RomImage,
        slot: Int,
        width: Int,
        maximum: Int,
        codec: PokemonTextCodec,
    ): Int? =
        rom.pointerOrNull(slot)?.let { offset ->
            TableValidators.inferFixedNameCount(
                rom = rom,
                offset = offset,
                width = width,
                codec = codec,
                minimumCount = 10,
                maximumCount = maximum,
            )
        }

    private fun semanticRoles(
        rom: RomImage,
        start: Int,
        speciesCount: Int?,
        moveCount: Int?,
    ): Int {
        var roles = 0
        val baseStats = rom.pointerOrNull(start)
        if (baseStats != null && speciesCount != null &&
            TableValidators.inferBaseStatsRecordSize(rom, baseStats, speciesCount, generation = 3) != null
        ) {
            roles++
        }
        val moveData = rom.pointerOrNull(start + 16)
        if (moveData != null && moveCount != null && validatesMoveRole(rom, moveData, moveCount)) {
            roles++
        }
        return roles
    }

    private fun validatesMoveRole(rom: RomImage, offset: Int, count: Int): Boolean =
        sequenceOf(
            TableValidators.moveData(rom, offset, count, 12, generation = 3),
            TableValidators.cfruMoveData(rom, offset, count),
            TableValidators.battleEngineMoveData(rom, offset, count),
        ).any { it.compatible }

    private fun pointerRun(rom: RomImage, start: Int): Int =
        (0 until PUBLISHED_POINTER_COUNT).count { rom.pointerOrNull(start + it * 4) != null }

    private fun RomImage.pointerOrNull(offset: Int): Int? =
        if (offset >= 0 && offset + 4 <= size) gbaPointer(offset) else null

    private const val SPRITES_SLOT = 0x128
    private const val SPECIES_NAMES_SLOT = 0x144
    private const val MOVE_NAMES_SLOT = 0x148
    private const val POKEDEX_COUNT_SLOT = 0x168
    private const val COMPACT_DATA_ROOT = 0x1AC
    private const val FREE_SEEN_FLAGS_DATA_ROOT = 0x1B4
    private const val STANDARD_DATA_ROOT = 0x1BC
    private const val PUBLISHED_POINTER_COUNT = 7
    private const val SPECIES_NAME_WIDTH = 11
    private const val MOVE_NAME_WIDTH = 13

    private data class PublishedBlockCandidate(
        val start: Int,
        val pointerRun: Int,
        val semanticRoles: Int,
    )
}
