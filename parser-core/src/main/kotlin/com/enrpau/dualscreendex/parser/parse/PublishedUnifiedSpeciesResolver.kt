package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.HeaderlessUnifiedSpeciesMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.sprite.GbaDecodeContract
import com.enrpau.dualscreendex.parser.sprite.GbaRomCompression
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

/**
 * Resolves unified species records published through the legacy Game Freak header after the
 * standalone species-name and sprite roots have been retired. The header nominates the root;
 * embedded names, stats, Dex IDs, presentation pointers, and a bounded physical extent must then
 * agree on one record ABI. No content scan can nominate an unrelated root.
 */
internal object PublishedUnifiedSpeciesResolver {
    private const val NAME_OFFSET = 44
    private const val NAME_WIDTH = 13
    private const val CATEGORY_WIDTH = 13
    private const val MINIMUM_ACTIVE_ROWS = 16
    private const val MINIMUM_ACTIVE_RATIO = 0.75
    private const val MINIMUM_PRESENTATION_RATIO = 0.80
    private const val MAXIMUM_SPECIES_RECORDS = 4_096
    private const val MAXIMUM_TRAILING_EMPTY_ROWS = 128
    private const val FRONT_SPRITE_FRAME_BYTES = 2_048
    private const val MAX_FRONT_SPRITE_BYTES = 64 * 1_024

    private data class SpeciesShape(
        val stride: Int,
        val count: Int,
        val activeIds: List<Int>,
        val nationalDexOffset: Int,
        val descriptionOffset: Int?,
        val frontSpriteOffset: Int?,
        val paletteOffset: Int?,
    )

    private data class DescriptionCandidate(
        val offset: Int,
        val structurallyValid: Int,
        val meaningful: Int,
    )

    private enum class EmbeddedTextQuality {
        INVALID,
        EMPTY,
        MEANINGFUL,
    }

    fun resolve(session: RomAnalysisSession): HeaderlessUnifiedSpeciesResolution? {
        val published = GbaPublishedHeaderResolver.resolve(session.rom)
        if (published.publishedDataState != GbaPublishedDataState.RESOLVED ||
            published.speciesNames != null || published.sprites != null
        ) return null
        val root = published.baseStats ?: return null
        val shape = inferShape(session, root) ?: return null
        val activeCount = shape.activeIds.size
        val descriptionsEvidence = shape.descriptionOffset?.let { offset ->
            validateDescriptions(session.rom, root, shape, offset)
        } ?: unavailablePresentationEvidence(shape.count, activeCount, "embedded description pointer was not resolved")
        val spritesEvidence = if (shape.frontSpriteOffset != null && shape.paletteOffset != null) {
            validateSprites(session.rom, root, shape, shape.frontSpriteOffset, shape.paletteOffset)
        } else {
            unavailablePresentationEvidence(shape.count, activeCount, "embedded sprite fields were not resolved")
        }
        val descriptionOffset = shape.descriptionOffset.takeIf { descriptionsEvidence.compatible }
        val frontSpriteOffset = shape.frontSpriteOffset.takeIf { spritesEvidence.compatible }
        val paletteOffset = shape.paletteOffset.takeIf { spritesEvidence.compatible }
        val abilities = HeaderlessUnifiedAbilityResolver.resolve(
            session = session,
            speciesRoot = root,
            speciesCount = shape.count,
            speciesRecordSize = shape.stride,
            activePredicateOffset = 0,
        )
        val moveAcquisitions = HeaderlessUnifiedMoveAcquisitionResolver.resolve(
            session = session,
            speciesRoot = root,
            speciesCount = shape.count,
            speciesRecordSize = shape.stride,
            activePredicateOffset = 0,
        )
        val tables = ProfileTables(
            speciesNames = TableLayout(
                offset = root + NAME_OFFSET,
                count = shape.count,
                recordSize = NAME_WIDTH,
                stride = shape.stride,
            ),
            baseStats = TableLayout(
                offset = root,
                count = shape.count,
                recordSize = shape.stride,
                stride = shape.stride,
            ),
            descriptions = descriptionOffset?.let { offset ->
                TableLayout(
                    offset = root,
                    count = shape.count,
                    recordSize = shape.stride,
                    stride = shape.stride,
                    pointerOffsets = listOf(offset),
                )
            },
            sprites = if (frontSpriteOffset != null && paletteOffset != null) {
                TableLayout(
                    offset = root + frontSpriteOffset,
                    count = shape.count,
                    recordSize = 4,
                    stride = shape.stride,
                    pointerOffsets = listOf(paletteOffset - frontSpriteOffset),
                )
            } else {
                null
            },
            abilities = abilities?.table,
        )
        val semanticEvidence = ValidationEvidence(
            compatible = true,
            validRecords = activeCount,
            totalRecords = shape.count,
            confidence = activeCount.toDouble() / shape.count,
            reasons = listOf("validated embedded fields across the bounded published unified-species extent"),
            offset = root,
            recordSize = shape.stride,
            coveredRecords = activeCount,
            expectedRecords = activeCount,
            incompleteRecords = 0,
        )
        return HeaderlessUnifiedSpeciesResolution(
            speciesCount = shape.count,
            tables = tables,
            metadata = HeaderlessUnifiedSpeciesMetadata(
                speciesTableOffset = root,
                speciesRecordSize = shape.stride,
                activePredicateOffset = 0,
                speciesNameOffset = NAME_OFFSET,
                speciesNameWidth = NAME_WIDTH,
                nationalDexOffset = shape.nationalDexOffset,
                categoryOffset = NAME_OFFSET - CATEGORY_WIDTH,
                heightOffset = shape.nationalDexOffset + 2,
                weightOffset = shape.nationalDexOffset + 4,
                descriptionPointerOffset = descriptionOffset,
                frontSpritePointerOffset = frontSpriteOffset,
                normalPalettePointerOffset = paletteOffset,
                abilities = abilities?.metadata,
                moveAcquisitions = moveAcquisitions,
            ),
            speciesNamesEvidence = semanticEvidence.copy(
                offset = root + NAME_OFFSET,
                recordSize = NAME_WIDTH,
            ),
            baseStatsEvidence = semanticEvidence,
            descriptionsEvidence = descriptionsEvidence,
            spritesEvidence = spritesEvidence,
        )
    }

    private fun inferShape(session: RomAnalysisSession, root: Int): SpeciesShape? {
        val candidates = (64..384 step 4).mapNotNull { stride ->
            inferExtent(session, root, stride)?.let { (count, activeIds) ->
                val nationalDexOffset = inferNationalDexOffset(session.rom, root, stride, activeIds)
                    ?: return@let null
                if (!validateCoreMetadataFields(session.rom, root, stride, activeIds, nationalDexOffset)) {
                    return@let null
                }
                val descriptionOffset = inferDescriptionOffset(
                    session.rom,
                    root,
                    stride,
                    activeIds,
                    nationalDexOffset,
                )
                val frontSpriteOffset = inferFrontSpriteOffset(
                    session.rom,
                    root,
                    stride,
                    activeIds,
                    descriptionOffset ?: alignToWord(nationalDexOffset + 8),
                )
                val paletteOffset = frontSpriteOffset?.let { front ->
                    inferPaletteOffset(session.rom, root, stride, activeIds, front)
                }
                SpeciesShape(
                    stride = stride,
                    count = count,
                    activeIds = activeIds,
                    nationalDexOffset = nationalDexOffset,
                    descriptionOffset = descriptionOffset,
                    frontSpriteOffset = frontSpriteOffset,
                    paletteOffset = paletteOffset,
                )
            }
        }
        val bestActiveCount = candidates.maxOfOrNull { it.activeIds.size } ?: return null
        return candidates.filter { it.activeIds.size == bestActiveCount }
            .minByOrNull { it.stride }
    }

    private fun inferExtent(
        session: RomAnalysisSession,
        root: Int,
        stride: Int,
    ): Pair<Int, List<Int>>? {
        if (!plausibleFallbackRow(session.rom, root, stride)) return null
        val maximumRows = minOf(
            MAXIMUM_SPECIES_RECORDS,
            (session.rom.size - root) / stride,
            (session.limits.maxDatasetExtentBytes / stride).toInt(),
        )
        if (maximumRows <= MINIMUM_ACTIVE_ROWS + 1) return null
        val activeIds = mutableListOf<Int>()
        var trailingEmptyRows = 0
        var foundBoundary = false
        for (id in 1 until maximumRows) {
            val row = root + id * stride
            when {
                isZeroRow(session.rom, row, stride) -> {
                    trailingEmptyRows++
                    if (trailingEmptyRows > MAXIMUM_TRAILING_EMPTY_ROWS) return null
                }
                plausibleActiveRow(session.rom, row) -> {
                    activeIds += id
                    trailingEmptyRows = 0
                }
                else -> {
                    foundBoundary = true
                    break
                }
            }
        }
        if (!foundBoundary || activeIds.size < MINIMUM_ACTIVE_ROWS) return null
        val count = (activeIds.lastOrNull() ?: return null) + 1
        if (activeIds.size.toDouble() / (count - 1) < MINIMUM_ACTIVE_RATIO) return null
        when (
            session.limits.checkTableExtent(
                offset = root.toLong(),
                count = count.toLong(),
                recordSize = stride.toLong(),
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Valid -> Unit
            is ExtentCheck.Invalid, is ExtentCheck.BudgetExceeded -> return null
        }
        return count to activeIds
    }

    private fun inferNationalDexOffset(
        rom: RomImage,
        root: Int,
        stride: Int,
        activeIds: List<Int>,
    ): Int? {
        val early = activeIds.take(24)
        val eligible = ((NAME_OFFSET + NAME_WIDTH - 1)..minOf(stride - 2, NAME_OFFSET + NAME_WIDTH + 16))
            .filter { field ->
                field % 2 == 0 && early.count { id -> rom.u16le(root + id * stride + field) == id } >= early.size * 0.90
            }
        if (eligible.isEmpty()) return null
        val scores = eligible.associateWith { field ->
            activeIds.count { id -> rom.u16le(root + id * stride + field) == id }
        }
        val bestScore = scores.values.maxOrNull() ?: return null
        return scores.filterValues { it == bestScore }.keys.minOrNull()
    }

    private fun validateCoreMetadataFields(
        rom: RomImage,
        root: Int,
        stride: Int,
        activeIds: List<Int>,
        nationalDexOffset: Int,
    ): Boolean {
        val valid = activeIds.count { id ->
            val row = root + id * stride
            plausibleText(rom, row + NAME_OFFSET - CATEGORY_WIDTH, CATEGORY_WIDTH) &&
                rom.u16le(row + nationalDexOffset + 2) > 0 &&
                rom.u16le(row + nationalDexOffset + 4) > 0
        }
        return valid >= activeIds.size * MINIMUM_PRESENTATION_RATIO
    }

    private fun inferDescriptionOffset(
        rom: RomImage,
        root: Int,
        stride: Int,
        activeIds: List<Int>,
        nationalDexOffset: Int,
    ): Int? {
        val sample = sample(activeIds, 32)
        val first = alignToWord(nationalDexOffset + 8)
        val last = minOf(stride - 4, nationalDexOffset + 40)
        val minimumMeaningful = maxOf(2, (sample.size + 9) / 10)
        return (first..last step 4).map { field ->
            val qualities = sample.map { id ->
                rom.gbaPointer(root + id * stride + field)
                    ?.let { embeddedTextQuality(rom, it, 512) }
                    ?: EmbeddedTextQuality.INVALID
            }
            DescriptionCandidate(
                offset = field,
                structurallyValid = qualities.count { it != EmbeddedTextQuality.INVALID },
                meaningful = qualities.count { it == EmbeddedTextQuality.MEANINGFUL },
            )
        }.filter { candidate ->
            candidate.structurallyValid >= sample.size * MINIMUM_PRESENTATION_RATIO &&
                candidate.meaningful >= minimumMeaningful
        }.maxWithOrNull(
            compareBy<DescriptionCandidate> { it.meaningful }
                .thenBy { it.structurallyValid }
                .thenByDescending { it.offset },
        )?.offset
    }

    private fun inferFrontSpriteOffset(
        rom: RomImage,
        root: Int,
        stride: Int,
        activeIds: List<Int>,
        afterField: Int,
    ): Int? {
        val sample = sample(activeIds, 24)
        val first = alignToWord(afterField + 4)
        val last = minOf(stride - 4, afterField + 40)
        return (first..last step 4).map { field ->
            field to sample.count { id ->
                rom.gbaPointer(root + id * stride + field)?.let { validGraphics(rom, it) } == true
            }
        }.filter { (_, valid) -> valid >= sample.size * MINIMUM_PRESENTATION_RATIO }
            .maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
            ?.first
    }

    private fun inferPaletteOffset(
        rom: RomImage,
        root: Int,
        stride: Int,
        activeIds: List<Int>,
        frontSpriteOffset: Int,
    ): Int? {
        val sample = sample(activeIds, 24)
        val first = frontSpriteOffset + 4
        val last = minOf(stride - 4, frontSpriteOffset + 32)
        return (first..last step 4).map { field ->
            field to sample.count { id ->
                rom.gbaPointer(root + id * stride + field)?.let { validPalette(rom, it) } == true
            }
        }.filter { (_, valid) -> valid >= sample.size * MINIMUM_PRESENTATION_RATIO }
            .maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
            ?.first
    }

    private fun validateDescriptions(
        rom: RomImage,
        root: Int,
        shape: SpeciesShape,
        descriptionOffset: Int,
    ): ValidationEvidence {
        val valid = shape.activeIds.count { id ->
            val row = root + id * shape.stride
            plausibleText(rom, row + NAME_OFFSET - CATEGORY_WIDTH, CATEGORY_WIDTH) &&
                rom.gbaPointer(row + descriptionOffset)?.let {
                    embeddedTextQuality(rom, it, 512) != EmbeddedTextQuality.INVALID
                } == true
        }
        return presentationEvidence(
            compatible = valid.toDouble() / shape.activeIds.size >= MINIMUM_PRESENTATION_RATIO,
            valid = valid,
            shape = shape,
            offset = root,
            recordSize = shape.stride,
            label = "descriptions",
        )
    }

    private fun validateSprites(
        rom: RomImage,
        root: Int,
        shape: SpeciesShape,
        frontSpriteOffset: Int,
        paletteOffset: Int,
    ): ValidationEvidence {
        val valid = shape.activeIds.count { id ->
            val row = root + id * shape.stride
            rom.gbaPointer(row + frontSpriteOffset)?.let { validGraphics(rom, it) } == true &&
                rom.gbaPointer(row + paletteOffset)?.let { validPalette(rom, it) } == true
        }
        return presentationEvidence(
            compatible = valid.toDouble() / shape.activeIds.size >= MINIMUM_PRESENTATION_RATIO,
            valid = valid,
            shape = shape,
            offset = root + frontSpriteOffset,
            recordSize = 4,
            label = "sprites",
        )
    }

    private fun presentationEvidence(
        compatible: Boolean,
        valid: Int,
        shape: SpeciesShape,
        offset: Int,
        recordSize: Int,
        label: String,
    ) = ValidationEvidence(
        compatible = compatible,
        validRecords = valid,
        totalRecords = shape.count,
        confidence = valid.toDouble() / shape.activeIds.size,
        reasons = if (compatible) {
            listOf("validated embedded unified-species $label")
        } else {
            listOf("valid embedded unified-species $label $valid/${shape.activeIds.size} below 80%")
        },
        offset = offset,
        recordSize = recordSize,
        coveredRecords = valid,
        expectedRecords = shape.activeIds.size,
        incompleteRecords = shape.activeIds.size - valid,
        reviewRecommended = valid < shape.activeIds.size,
    )

    private fun unavailablePresentationEvidence(count: Int, activeCount: Int, reason: String) = ValidationEvidence(
        compatible = false,
        validRecords = 0,
        totalRecords = count,
        confidence = 0.0,
        reasons = listOf(reason),
        coveredRecords = 0,
        expectedRecords = activeCount,
        incompleteRecords = activeCount,
    )

    private fun plausibleFallbackRow(rom: RomImage, root: Int, stride: Int): Boolean {
        if (root < 0 || root.toLong() + stride > rom.size) return false
        if ((0 until 6).any { rom.u8(root + it) != 0 }) return false
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(rom.slice(root + NAME_OFFSET, NAME_WIDTH))
        return decoded.terminated && decoded.validRatio >= 0.8 && decoded.text.isNotEmpty()
    }

    private fun plausibleActiveRow(rom: RomImage, row: Int): Boolean = runCatching {
        (0 until 6).all { rom.u8(row + it) > 0 } &&
            rom.u8(row + 6) in 0..31 && rom.u8(row + 7) in 0..31 &&
            plausibleText(rom, row + NAME_OFFSET, NAME_WIDTH)
    }.getOrDefault(false)

    private fun isZeroRow(rom: RomImage, row: Int, stride: Int): Boolean =
        (0 until stride).all { rom.u8(row + it) == 0 }

    private fun embeddedTextQuality(rom: RomImage, offset: Int, maximumLength: Int): EmbeddedTextQuality = runCatching {
        if (offset < 0 || offset >= rom.size) return@runCatching EmbeddedTextQuality.INVALID
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(
            rom.slice(offset, minOf(maximumLength, rom.size - offset)),
        )
        when {
            !decoded.terminated -> EmbeddedTextQuality.INVALID
            decoded.contentBytes == 0 -> EmbeddedTextQuality.EMPTY
            decoded.validRatio < 0.8 -> EmbeddedTextQuality.INVALID
            decoded.text.any(Char::isLetterOrDigit) -> EmbeddedTextQuality.MEANINGFUL
            else -> EmbeddedTextQuality.INVALID
        }
    }.getOrDefault(EmbeddedTextQuality.INVALID)

    private fun plausibleText(rom: RomImage, offset: Int, maximumLength: Int): Boolean = runCatching {
        if (offset < 0 || offset >= rom.size) return@runCatching false
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(
            rom.slice(offset, minOf(maximumLength, rom.size - offset)),
        )
        decoded.terminated && decoded.validRatio >= 0.8 && decoded.text.any(Char::isLetterOrDigit)
    }.getOrDefault(false)

    private fun validGraphics(rom: RomImage, offset: Int): Boolean = runCatching {
        val decodedSize = GbaRomCompression.decodedSizeAtOrNull(rom, offset) ?: return@runCatching false
        decodedSize in FRONT_SPRITE_FRAME_BYTES..MAX_FRONT_SPRITE_BYTES &&
            decodedSize % FRONT_SPRITE_FRAME_BYTES == 0 &&
            GbaRomCompression.decodeAt(rom, offset, GbaDecodeContract.SPECIES_SPRITE).size == decodedSize
    }.getOrDefault(false)

    private fun validPalette(rom: RomImage, offset: Int): Boolean = runCatching {
        val decodedSize = GbaRomCompression.decodedSizeAtOrNull(rom, offset)
        val bytes = if (decodedSize != null && decodedSize in 2..32 && decodedSize % 2 == 0) {
            GbaRomCompression.decodeAt(rom, offset, GbaDecodeContract.PALETTE)
        } else {
            rom.slice(offset, 32)
        }
        bytes.size in 2..32 && bytes.size % 2 == 0 && (bytes.indices step 2).all { index ->
            val low = bytes[index].toInt() and 0xFF
            val high = bytes[index + 1].toInt() and 0xFF
            (low or (high shl 8)) <= 0x7FFF
        }
    }.getOrDefault(false)

    private fun sample(ids: List<Int>, maximum: Int): List<Int> {
        if (ids.size <= maximum) return ids
        if (maximum == 1) return listOf(ids.first())
        return List(maximum) { index ->
            ids[(index.toLong() * (ids.lastIndex) / (maximum - 1)).toInt()]
        }.distinct()
    }

    private fun alignToWord(value: Int): Int = (value + 3) and -4
}
