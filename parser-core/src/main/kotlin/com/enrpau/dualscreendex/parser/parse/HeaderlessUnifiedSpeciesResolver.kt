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

internal data class HeaderlessUnifiedSpeciesResolution(
    val speciesCount: Int,
    val tables: ProfileTables,
    val metadata: HeaderlessUnifiedSpeciesMetadata,
    val speciesNamesEvidence: ValidationEvidence,
    val baseStatsEvidence: ValidationEvidence,
    val descriptionsEvidence: ValidationEvidence,
    val spritesEvidence: ValidationEvidence,
)

/**
 * Resolves the headerless unified species ABI emitted by expansion-derived engines. Selection is
 * authorized only by paired complete compiled consumers: a bounded name accessor with its active
 * row fallback and a leaf consumer of all six base-stat bytes. Table content only validates the
 * consumer-selected root; it cannot nominate or rank a root by itself.
 */
internal object HeaderlessUnifiedSpeciesResolver {
    private const val RECORD_SIZE = 260
    private const val NAME_OFFSET = 44
    private const val NAME_WIDTH = 13
    private const val CATEGORY_WIDTH = 13
    private const val DEFAULT_NATIONAL_DEX_OFFSET = 60
    private const val MINIMUM_PRESENTATION_RATIO = 0.80
    private const val FRONT_SPRITE_FRAME_BYTES = 2048
    private const val MAX_FRONT_SPRITE_BYTES = 64 * 1024

    private data class PresentationOffsets(
        val category: Int,
        val height: Int,
        val weight: Int,
        val description: Int,
        val frontSprite: Int,
        val palette: Int,
    )

    private data class RootAccessorPrefix(
        val exclusiveBound: Int,
        val rootRegister: Int,
        val indexRegister: Int,
    )

    private data class NationalDexAccessor(
        val exclusiveBound: Int,
        val fieldOffset: Int,
    )

    fun resolve(session: RomAnalysisSession): HeaderlessUnifiedSpeciesResolution? {
        val index = session.gbaReferenceIndex ?: return null
        if (index.overflowed) return null
        val fieldRelativeRoots = index.targets.mapNotNullTo(linkedSetOf()) { (target, evidence) ->
            if (!evidence.siteEvidenceAvailable || target < NAME_OFFSET) return@mapNotNullTo null
            if (evidence.instructionSites.none { hasNameFieldAddressFormation(session.rom, it) }) {
                return@mapNotNullTo null
            }
            val root = target - NAME_OFFSET
            if (!plausibleFirstRows(session.rom, root)) return@mapNotNullTo null
            root
        }
        val rootRelativeRoots = index.targets.mapNotNullTo(linkedSetOf()) { (target, evidence) ->
            if (!plausibleFirstRows(session.rom, target)) return@mapNotNullTo null
            val sites = if (evidence.siteEvidenceAvailable) {
                evidence
            } else {
                session.nominatedGbaReferenceSites(target)
            }?.takeIf { it.siteEvidenceAvailable } ?: return@mapNotNullTo null
            target.takeIf { sites.instructionSites.any { site -> rootNameAccessorBound(session.rom, site) != null } }
        }
        val nominatedRoots = linkedSetOf<Int>().apply {
            addAll(fieldRelativeRoots)
            addAll(rootRelativeRoots)
        }
        val candidates = nominatedRoots.mapNotNull { root -> resolveRoot(session, root) }
        return candidates.singleOrNull()
    }

    private fun resolveRoot(
        session: RomAnalysisSession,
        root: Int,
    ): HeaderlessUnifiedSpeciesResolution? {
        val references = session.nominatedGbaReferenceSites(root)
            ?.takeIf { it.siteEvidenceAvailable } ?: return null
        val fieldRelativeCounts = references.instructionSites.mapNotNull { site ->
            nameAccessorBound(session.rom, site)
        }.distinct()
        val rootRelativeCounts = references.instructionSites.mapNotNull { site ->
            rootNameAccessorBound(session.rom, site)
        }.distinct()
        val counts = (fieldRelativeCounts + rootRelativeCounts).distinct()
        if (counts.size != 1 || references.instructionSites.none { hasSixStatLeafConsumer(session.rom, it) }) {
            return null
        }
        val speciesCount = counts.single()
        if (speciesCount <= 1) return null
        val nationalDexAccessors = references.instructionSites.mapNotNull { site ->
            rootNationalDexAccessor(session.rom, site)
        }.distinct()
        val nationalDexOffset = when {
            rootRelativeCounts.isEmpty() -> DEFAULT_NATIONAL_DEX_OFFSET
            nationalDexAccessors.size != 1 -> return null
            nationalDexAccessors.single().exclusiveBound != speciesCount -> return null
            else -> nationalDexAccessors.single().fieldOffset
        }
        when (
            session.limits.checkTableExtent(
                offset = root.toLong(),
                count = speciesCount.toLong(),
                recordSize = RECORD_SIZE.toLong(),
                romSize = session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Valid -> Unit
            is ExtentCheck.Invalid, is ExtentCheck.BudgetExceeded -> return null
        }
        val activeCount = validateRows(session.rom, root, speciesCount) ?: return null
        val abilities = HeaderlessUnifiedAbilityResolver.resolve(
            session = session,
            speciesRoot = root,
            speciesCount = speciesCount,
            speciesRecordSize = RECORD_SIZE,
            activePredicateOffset = 0,
        )
        val moveAcquisitions = HeaderlessUnifiedMoveAcquisitionResolver.resolve(
            session = session,
            speciesRoot = root,
            speciesCount = speciesCount,
            speciesRecordSize = RECORD_SIZE,
            activePredicateOffset = 0,
        )
        val presentation = presentationOffsets(nationalDexOffset)
        val descriptionsEvidence = presentation?.let {
            validateDescriptions(session.rom, root, speciesCount, activeCount, it)
        } ?: unavailablePresentationEvidence(speciesCount, "unified description fields exceed the record")
        val spritesEvidence = presentation?.let {
            validateSprites(session.rom, root, speciesCount, activeCount, it)
        } ?: unavailablePresentationEvidence(speciesCount, "unified sprite fields exceed the record")
        val descriptionFields = presentation?.takeIf { descriptionsEvidence.compatible }
        val spriteFields = presentation?.takeIf { spritesEvidence.compatible }
        return HeaderlessUnifiedSpeciesResolution(
            speciesCount = speciesCount,
            tables = ProfileTables(
                speciesNames = TableLayout(
                    offset = root + NAME_OFFSET,
                    count = speciesCount,
                    recordSize = NAME_WIDTH,
                    stride = RECORD_SIZE,
                ),
                baseStats = TableLayout(
                    offset = root,
                    count = speciesCount,
                    recordSize = RECORD_SIZE,
                    stride = RECORD_SIZE,
                ),
                descriptions = descriptionFields?.let { fields ->
                    TableLayout(
                        offset = root,
                        count = speciesCount,
                        recordSize = RECORD_SIZE,
                        stride = RECORD_SIZE,
                        pointerOffsets = listOf(fields.description),
                    )
                },
                sprites = spriteFields?.let { fields ->
                    TableLayout(
                        offset = root + fields.frontSprite,
                        count = speciesCount,
                        recordSize = 4,
                        stride = RECORD_SIZE,
                        pointerOffsets = listOf(fields.palette - fields.frontSprite),
                    )
                },
                abilities = abilities?.table,
            ),
            metadata = HeaderlessUnifiedSpeciesMetadata(
                speciesTableOffset = root,
                speciesRecordSize = RECORD_SIZE,
                activePredicateOffset = 0,
                speciesNameOffset = NAME_OFFSET,
                speciesNameWidth = NAME_WIDTH,
                nationalDexOffset = nationalDexOffset,
                categoryOffset = descriptionFields?.category,
                heightOffset = descriptionFields?.height,
                weightOffset = descriptionFields?.weight,
                descriptionPointerOffset = descriptionFields?.description,
                frontSpritePointerOffset = spriteFields?.frontSprite,
                normalPalettePointerOffset = spriteFields?.palette,
                abilities = abilities?.metadata,
                moveAcquisitions = moveAcquisitions,
            ),
            speciesNamesEvidence = ValidationEvidence(
                compatible = true,
                validRecords = activeCount,
                totalRecords = speciesCount,
                confidence = activeCount.toDouble() / speciesCount,
                reasons = listOf("bounded unified species-name field through the complete compiled accessor"),
                offset = root + NAME_OFFSET,
                recordSize = NAME_WIDTH,
            ),
            baseStatsEvidence = ValidationEvidence(
                compatible = true,
                validRecords = activeCount,
                totalRecords = speciesCount,
                confidence = activeCount.toDouble() / speciesCount,
                reasons = listOf("validated six base-stat bytes selected by the complete compiled leaf consumer"),
                offset = root,
                recordSize = RECORD_SIZE,
            ),
            descriptionsEvidence = descriptionsEvidence,
            spritesEvidence = spritesEvidence,
        )
    }

    private fun presentationOffsets(nationalDexOffset: Int): PresentationOffsets? {
        // The pointer-aligned presentation tail is stable across the observed one- and two-byte cry fields.
        val description = (nationalDexOffset + 16 + 3) and -4
        val candidate = PresentationOffsets(
            category = NAME_OFFSET - CATEGORY_WIDTH,
            height = description - 14,
            weight = description - 12,
            description = description,
            frontSprite = description + 12,
            palette = description + 20,
        )
        return candidate.takeIf {
            it.category >= 0 && it.category + CATEGORY_WIDTH == NAME_OFFSET &&
                it.height + 2 <= RECORD_SIZE && it.weight + 2 <= RECORD_SIZE &&
                it.description + 4 <= RECORD_SIZE && it.frontSprite + 4 <= RECORD_SIZE &&
                it.palette + 4 <= RECORD_SIZE
        }
    }

    private fun validateDescriptions(
        rom: RomImage,
        root: Int,
        count: Int,
        activeCount: Int,
        fields: PresentationOffsets,
    ): ValidationEvidence {
        var valid = 0
        repeat(count) { id ->
            val record = root + id * RECORD_SIZE
            if (rom.u8(record) == 0) return@repeat
            val pointer = runCatching { rom.gbaPointer(record + fields.description) }.getOrNull()
            if (
                plausibleText(rom, record + fields.category, CATEGORY_WIDTH) &&
                pointer?.let { plausibleText(rom, it, 512) } == true
            ) {
                valid++
            }
        }
        val confidence = valid.toDouble() / activeCount
        val compatible = confidence >= MINIMUM_PRESENTATION_RATIO
        return ValidationEvidence(
            compatible = compatible,
            validRecords = valid,
            totalRecords = count,
            confidence = confidence,
            reasons = if (compatible) {
                listOf("validated embedded category and terminated description fields")
            } else {
                listOf("valid embedded unified descriptions $valid/$activeCount active rows below 80%")
            },
            offset = root,
            recordSize = RECORD_SIZE,
            coveredRecords = valid,
            expectedRecords = activeCount,
            incompleteRecords = activeCount - valid,
            reviewRecommended = valid < activeCount,
        )
    }

    private fun validateSprites(
        rom: RomImage,
        root: Int,
        count: Int,
        activeCount: Int,
        fields: PresentationOffsets,
    ): ValidationEvidence {
        var valid = 0
        repeat(count) { id ->
            val record = root + id * RECORD_SIZE
            if (rom.u8(record) == 0) return@repeat
            val graphics = runCatching { rom.gbaPointer(record + fields.frontSprite) }.getOrNull()
            val palette = runCatching { rom.gbaPointer(record + fields.palette) }.getOrNull()
            val graphicsValid = graphics?.let { pointer ->
                val decodedSize = GbaRomCompression.decodedSizeAtOrNull(rom, pointer)
                    ?: return@let false
                decodedSize in FRONT_SPRITE_FRAME_BYTES..MAX_FRONT_SPRITE_BYTES &&
                    decodedSize % FRONT_SPRITE_FRAME_BYTES == 0 &&
                    runCatching { GbaRomCompression.decodeAt(rom, pointer, GbaDecodeContract.SPECIES_SPRITE).size == decodedSize }
                        .getOrDefault(false)
            } == true
            val paletteValid = palette?.let { pointer -> validPalette(rom, pointer) } == true
            if (graphicsValid && paletteValid) valid++
        }
        val confidence = valid.toDouble() / activeCount
        val compatible = confidence >= MINIMUM_PRESENTATION_RATIO
        return ValidationEvidence(
            compatible = compatible,
            validRecords = valid,
            totalRecords = count,
            confidence = confidence,
            reasons = if (compatible) {
                listOf("validated bounded embedded front graphics and BGR555 palettes")
            } else {
                listOf("valid embedded unified sprites $valid/$activeCount active rows below 80%")
            },
            offset = root + fields.frontSprite,
            recordSize = 4,
            coveredRecords = valid,
            expectedRecords = activeCount,
            incompleteRecords = activeCount - valid,
            reviewRecommended = valid < activeCount,
        )
    }

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

    private fun plausibleText(rom: RomImage, offset: Int, maximumLength: Int): Boolean = runCatching {
        if (offset < 0 || offset >= rom.size) return@runCatching false
        val decoded = PokemonTextCodec.gbaEnglish.decodeDetailed(
            rom.slice(offset, minOf(maximumLength, rom.size - offset)),
        )
        decoded.terminated && decoded.validRatio >= 0.8 && decoded.text.any(Char::isLetterOrDigit)
    }.getOrDefault(false)

    private fun unavailablePresentationEvidence(count: Int, reason: String) = ValidationEvidence(
        compatible = false,
        validRecords = 0,
        totalRecords = count,
        confidence = 0.0,
        reasons = listOf(reason),
    )

    private fun validateRows(rom: RomImage, root: Int, count: Int): Int? {
        val codec = PokemonTextCodec.gbaEnglish
        val row0 = root
        if (rom.u8(row0) != 0 || (0 until 6).any { rom.u8(row0 + it) != 0 }) return null
        val fallbackBytes = rom.slice(row0 + NAME_OFFSET, NAME_WIDTH)
        val fallback = codec.decode(fallbackBytes)
        if (fallback.isEmpty() || fallbackBytes.none { (it.toInt() and 0xFF) == 0xFF }) return null
        if (rom.u8(root + (count - 1) * RECORD_SIZE) != 0) return null
        var active = 0
        for (id in 1 until count) {
            val row = root + id * RECORD_SIZE
            if (rom.u8(row) == 0) continue
            active += 1
            if ((0 until 6).any { rom.u8(row + it) == 0 }) return null
            val name = codec.decode(rom.slice(row + NAME_OFFSET, NAME_WIDTH))
            if (name.none(Char::isLetterOrDigit)) return null
        }
        return active.takeIf { it > 0 }
    }

    private fun plausibleFirstRows(rom: RomImage, root: Int): Boolean {
        val end = root.toLong() + RECORD_SIZE.toLong() * 2L
        if (root < 0 || end > rom.size.toLong() || rom.u8(root) != 0 || rom.u8(root + RECORD_SIZE) == 0) {
            return false
        }
        return PokemonTextCodec.gbaEnglish
            .decode(rom.slice(root + RECORD_SIZE + NAME_OFFSET, NAME_WIDTH))
            .any(Char::isLetterOrDigit)
    }

    /** `id * 65 * 4 + (root + 44)` address formation used as a root nomination only. */
    private fun hasNameFieldAddressFormation(rom: RomImage, site: Int): Boolean {
        if (site !in 4 until rom.size - 8) return false
        val scale65 = rom.u16le(site - 4)
        val addOriginal = rom.u16le(site - 2)
        val scaleFour = rom.u16le(site + 2)
        val addRoot = rom.u16le(site + 6)
        return scale65 and 0xF800 == 0 && (scale65 ushr 6) and 0x1F == 6 &&
            (addOriginal and 0xFC00 == 0x4400 || addOriginal and 0xFE00 == 0x1800) &&
            scaleFour and 0xF800 == 0 && (scaleFour ushr 6) and 0x1F == 2 &&
            addRoot and 0xFE00 == 0x1800
    }

    /** Inclusive u16 bound + active-byte test + row-zero fallback + name-field return. */
    private fun nameAccessorBound(rom: RomImage, site: Int): Int? {
        if (site !in 12 until rom.size - 62) return null
        if (rom.u16le(site - 2) and 0xF800 != 0 || (rom.u16le(site - 2) ushr 6) and 0x1F != 6) return null
        if (rom.u16le(site + 2) and 0xFE00 != 0x1800 ||
            rom.u16le(site + 4) and 0xF800 != 0 || (rom.u16le(site + 4) ushr 6) and 0x1F != 2 ||
            rom.u16le(site + 6) and 0xF800 != 0x5800 ||
            rom.u16le(site + 8) and 0xF800 != 0x2800 ||
            rom.u16le(site + 10) and 0xFF00 != 0xD100
        ) return null
        val boundLoad = site - 12
        val maximumId = literalValue(rom, boundLoad)?.takeIf { it in 1 until Int.MAX_VALUE } ?: return null
        if (rom.u16le(site - 6) and 0xFFC0 != 0x4280 || rom.u16le(site - 4) and 0xFF00 != 0xD800) return null
        val returnWindow = (site + 12..site + 58 step 2).map { rom.u16le(it) }
        if (returnWindow.none { it and 0xFF00 == 0x3000 && it and 0xFF == NAME_OFFSET } ||
            returnWindow.none { it and 0xFF87 == 0x4700 }
        ) return null
        return maximumId + 1
    }

    /**
     * Alternate compiler materialization of the same complete accessor. The exclusive table
     * bound is built from an immediate shift, then the function indexes the nominated root,
     * checks the active byte, and returns the row name or the row-zero fallback.
     */
    private fun rootNameAccessorBound(rom: RomImage, site: Int): Int? {
        val prefix = rootAccessorPrefix(rom, site) ?: return null
        val returnWindow = (site + 10..site + 58 step 2).map { rom.u16le(it) }
        if (returnWindow.none { it and 0xFF00 == 0x3000 && it and 0xFF == NAME_OFFSET } ||
            returnWindow.none { it and 0xFF87 == 0x4700 }
        ) return null
        return prefix.exclusiveBound
    }

    /** Complete active-row accessor that returns a u16 Dex field from the same bounded table. */
    private fun rootNationalDexAccessor(rom: RomImage, site: Int): NationalDexAccessor? {
        val prefix = rootAccessorPrefix(rom, site) ?: return null
        val activeBranch = rom.u16le(site + 10)
        if (activeBranch and 0xFF00 != 0xD100) return null
        val branchDisplacement = (activeBranch and 0xFF).toByte().toInt() * 2
        val activePath = site + 14 + branchDisplacement
        if (activePath !in site + 12..site + 48 || activePath + 4 > rom.size) return null
        val rowAdd = rom.u16le(activePath)
        if (rowAdd and 0xFE00 != 0x1800) return null
        val addDestination = rowAdd and 0x7
        val addLeft = (rowAdd ushr 3) and 0x7
        val addRight = (rowAdd ushr 6) and 0x7
        if (addDestination != prefix.rootRegister ||
            setOf(addLeft, addRight) != setOf(prefix.rootRegister, prefix.indexRegister)
        ) return null
        val load = rom.u16le(activePath + 2)
        if (load and 0xF800 != 0x8800 || (load ushr 3) and 0x7 != prefix.rootRegister) return null
        val fieldOffset = ((load ushr 6) and 0x1F) * 2
        if (fieldOffset < NAME_OFFSET + NAME_WIDTH || fieldOffset + 2 > RECORD_SIZE) return null
        return NationalDexAccessor(prefix.exclusiveBound, fieldOffset)
    }

    /** Shared bound, species normalization, 260-byte row index, and active-byte prefix. */
    private fun rootAccessorPrefix(rom: RomImage, site: Int): RootAccessorPrefix? {
        if (site !in 14 until rom.size - 62) return null
        val immediate = rom.u16le(site - 14)
        val normalizeLeft = rom.u16le(site - 12)
        val normalizeRight = rom.u16le(site - 10)
        val boundShift = rom.u16le(site - 8)
        val compare = rom.u16le(site - 6)
        val outOfBoundsBranch = rom.u16le(site - 4)
        val indexShift = rom.u16le(site - 2)
        val addOriginal = rom.u16le(site + 2)
        val scaleFour = rom.u16le(site + 4)
        val activeLoad = rom.u16le(site + 6)
        val activeCompare = rom.u16le(site + 8)
        if (immediate and 0xF800 != 0x2000 ||
            normalizeLeft and 0xF800 != 0 || (normalizeLeft ushr 6) and 0x1F != 16 ||
            normalizeRight and 0xF800 != 0x0800 || (normalizeRight ushr 6) and 0x1F != 16 ||
            boundShift and 0xF800 != 0 ||
            compare and 0xFFC0 != 0x4280 ||
            outOfBoundsBranch and 0xFF00 != 0xD200 ||
            indexShift and 0xF800 != 0 || (indexShift ushr 6) and 0x1F != 6 ||
            addOriginal and 0xFE00 != 0x1800 ||
            scaleFour and 0xF800 != 0 || (scaleFour ushr 6) and 0x1F != 2 ||
            activeLoad and 0xF800 != 0x5800 ||
            activeCompare and 0xF800 != 0x2800
        ) return null
        val immediateRegister = (immediate ushr 8) and 0x7
        if ((boundShift ushr 3) and 0x7 != immediateRegister || boundShift and 0x7 != immediateRegister) {
            return null
        }
        val speciesRegister = normalizeLeft and 0x7
        if (
            (normalizeRight ushr 3) and 0x7 != speciesRegister || normalizeRight and 0x7 != speciesRegister ||
            compare and 0x7 != speciesRegister || (compare ushr 3) and 0x7 != immediateRegister
        ) return null
        val indexRegister = indexShift and 0x7
        if ((indexShift ushr 3) and 0x7 != speciesRegister ||
            addOriginal and 0x7 != indexRegister || (addOriginal ushr 3) and 0x7 != indexRegister ||
            (addOriginal ushr 6) and 0x7 != speciesRegister ||
            scaleFour and 0x7 != indexRegister || (scaleFour ushr 3) and 0x7 != indexRegister
        ) return null
        val rootRegister = (rom.u16le(site) ushr 8) and 0x7
        val activeRegister = activeLoad and 0x7
        if (rom.u16le(site) and 0xF800 != 0x4800 ||
            setOf((activeLoad ushr 6) and 0x7, (activeLoad ushr 3) and 0x7) !=
            setOf(rootRegister, indexRegister) ||
            (activeCompare ushr 8) and 0x7 != activeRegister || activeCompare and 0xFF != 0
        ) return null
        val shiftAmount = (boundShift ushr 6) and 0x1F
        val exclusiveBound = (immediate and 0xFF).toLong() shl shiftAmount
        if (exclusiveBound !in 2..Int.MAX_VALUE.toLong()) return null
        return RootAccessorPrefix(exclusiveBound.toInt(), rootRegister, indexRegister)
    }

    /** Complete leaf that indexes the same root and returns the sum of bytes +0 through +5. */
    private fun hasSixStatLeafConsumer(rom: RomImage, site: Int): Boolean {
        if (site !in 2 until rom.size - 32) return false
        val expected = intArrayOf(
            0x0182, 0x4B08, 0x1812, 0x0092, 0x5CD0, 0x189B, 0x785A, 0x1880,
            0x789A, 0x1880, 0x78DA, 0x1880, 0x791A, 0x795B, 0x1880, 0x18C0, 0x4770,
        )
        return expected.indices.all { rom.u16le(site - 2 + it * 2) == expected[it] }
    }

    private fun literalValue(rom: RomImage, instructionOffset: Int): Int? {
        if (instructionOffset !in 0 until rom.size - 2) return null
        val instruction = rom.u16le(instructionOffset)
        if (instruction and 0xF800 != 0x4800) return null
        val literal = ((instructionOffset + 4) and -4).toLong() + (instruction and 0xFF) * 4L
        if (literal !in 0..rom.size.toLong() - 4L) return null
        return rom.u32le(literal.toInt()).takeIf { it <= Int.MAX_VALUE }?.toInt()
    }
}
