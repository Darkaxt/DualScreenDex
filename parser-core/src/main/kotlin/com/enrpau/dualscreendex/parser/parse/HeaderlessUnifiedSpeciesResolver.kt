package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.HeaderlessUnifiedSpeciesMetadata
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.TableLayout
import com.enrpau.dualscreendex.parser.model.ValidationEvidence
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec

internal data class HeaderlessUnifiedSpeciesResolution(
    val speciesCount: Int,
    val tables: ProfileTables,
    val metadata: HeaderlessUnifiedSpeciesMetadata,
    val speciesNamesEvidence: ValidationEvidence,
    val baseStatsEvidence: ValidationEvidence,
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
    private const val NATIONAL_DEX_OFFSET = 60

    fun resolve(session: RomAnalysisSession): HeaderlessUnifiedSpeciesResolution? {
        val index = session.gbaReferenceIndex ?: return null
        if (index.overflowed) return null
        val nominatedRoots = index.targets.mapNotNullTo(linkedSetOf()) { (target, evidence) ->
            if (!evidence.siteEvidenceAvailable || target < NAME_OFFSET) return@mapNotNullTo null
            if (evidence.instructionSites.none { hasNameFieldAddressFormation(session.rom, it) }) {
                return@mapNotNullTo null
            }
            val root = target - NAME_OFFSET
            if (!plausibleFirstRows(session.rom, root)) return@mapNotNullTo null
            root
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
        val counts = references.instructionSites.mapNotNull { nameAccessorBound(session.rom, it) }.distinct()
        if (counts.size != 1 || references.instructionSites.none { hasSixStatLeafConsumer(session.rom, it) }) {
            return null
        }
        val speciesCount = counts.single()
        if (speciesCount <= 1) return null
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
            ),
            metadata = HeaderlessUnifiedSpeciesMetadata(
                speciesRecordSize = RECORD_SIZE,
                activePredicateOffset = 0,
                speciesNameOffset = NAME_OFFSET,
                speciesNameWidth = NAME_WIDTH,
                nationalDexOffset = NATIONAL_DEX_OFFSET,
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
        )
    }

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
