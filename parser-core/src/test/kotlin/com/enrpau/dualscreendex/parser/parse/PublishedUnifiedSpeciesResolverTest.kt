package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PublishedUnifiedSpeciesResolverTest {
    @Test
    fun resolvesPublishedEmbeddedSpeciesWithoutLegacyNameOrSpriteRoots() {
        val bytes = fixture()
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(rom, RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))

        val resolved = PublishedUnifiedSpeciesResolver.resolve(session)

        assertNotNull(resolved)
        requireNotNull(resolved)
        assertEquals(40, resolved.speciesCount)
        assertEquals(SPECIES_ROOT, resolved.tables.baseStats?.offset)
        assertEquals(152, resolved.tables.baseStats?.stride)
        assertEquals(SPECIES_ROOT + 44, resolved.tables.speciesNames?.offset)
        assertEquals(152, resolved.tables.speciesNames?.stride)
        assertEquals(58, resolved.metadata.nationalDexOffset)
        assertEquals(31, resolved.metadata.categoryOffset)
        assertEquals(60, resolved.metadata.heightOffset)
        assertEquals(62, resolved.metadata.weightOffset)
        assertEquals(72, resolved.metadata.descriptionPointerOffset)
        assertEquals(84, resolved.metadata.frontSpritePointerOffset)
        assertEquals(100, resolved.metadata.normalPalettePointerOffset)
        assertEquals(35, resolved.speciesNamesEvidence.coveredRecords)
        assertEquals(35, resolved.speciesNamesEvidence.expectedRecords)
        assertEquals(40, resolved.speciesNamesEvidence.totalRecords)
        assertEquals(true, resolved.descriptionsEvidence.compatible)
        assertEquals(true, resolved.spritesEvidence.compatible)
    }

    @Test
    fun acceptsStructurallyValidEmptyDescriptions() {
        val bytes = fixture().also { target ->
            target[EMPTY_DESCRIPTION_ROOT] = 0xFF.toByte()
            (5 until 40).forEach { id ->
                if (id !in 30..33) writePointer(target, SPECIES_ROOT + id * 152 + 72, EMPTY_DESCRIPTION_ROOT)
            }
        }
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(rom, RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))

        val resolved = requireNotNull(PublishedUnifiedSpeciesResolver.resolve(session))

        assertNotNull(resolved.tables.descriptions)
        assertEquals(72, resolved.metadata.descriptionPointerOffset)
        assertEquals(true, resolved.descriptionsEvidence.compatible)
        assertEquals(35, resolved.descriptionsEvidence.coveredRecords)
    }

    @Test
    fun doesNotReplacePublishedLegacySpeciesTables() {
        val bytes = fixture().also { target ->
            writePointer(target, 0x128, GRAPHICS_ROOT)
            writePointer(target, 0x144, SPECIES_ROOT + 44)
        }
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(rom, RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))

        assertEquals(null, PublishedUnifiedSpeciesResolver.resolve(session))
    }

    @Test
    fun descriptionCoverageUsesTheEntireActiveDomain() {
        val bytes = fixture().also { target ->
            listOf(1, 2, 3, 4, 5, 12, 23, 38).forEach { id ->
                writeU32(target, SPECIES_ROOT + id * 152 + 72, 0)
            }
        }
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(rom, RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))

        val resolved = requireNotNull(PublishedUnifiedSpeciesResolver.resolve(session))

        assertEquals(null, resolved.tables.descriptions)
        assertEquals(null, resolved.metadata.descriptionPointerOffset)
        assertEquals(27, resolved.descriptionsEvidence.coveredRecords)
        assertEquals(35, resolved.descriptionsEvidence.expectedRecords)
        assertNotNull(resolved.tables.speciesNames)
        assertNotNull(resolved.tables.sprites)
    }

    @Test
    fun malformedEmbeddedDescriptionsDisableOnlyDescriptions() {
        val bytes = fixture().also { target ->
            (1 until 40).forEach { id ->
                if (id !in 30..33) writeU32(target, SPECIES_ROOT + id * 152 + 72, 0)
            }
        }
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(rom, RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))

        val resolved = requireNotNull(PublishedUnifiedSpeciesResolver.resolve(session))

        assertEquals(null, resolved.tables.descriptions)
        assertEquals(null, resolved.metadata.descriptionPointerOffset)
        assertEquals(31, resolved.metadata.categoryOffset)
        assertEquals(60, resolved.metadata.heightOffset)
        assertEquals(62, resolved.metadata.weightOffset)
        assertNotNull(resolved.tables.speciesNames)
        assertNotNull(resolved.tables.baseStats)
        assertNotNull(resolved.tables.sprites)
    }

    @Test
    fun malformedEmbeddedSpritesDisableOnlySprites() {
        val bytes = fixture().also { target ->
            (1 until 40).forEach { id ->
                if (id !in 30..33) writeU32(target, SPECIES_ROOT + id * 152 + 84, 0)
            }
        }
        val rom = RomImage(bytes)
        val session = RomAnalysisSession(rom, RomHeader(Platform.GBA, "POKEMON EMER", "BPEE"))

        val resolved = requireNotNull(PublishedUnifiedSpeciesResolver.resolve(session))

        assertEquals(null, resolved.tables.sprites)
        assertEquals(null, resolved.metadata.frontSpritePointerOffset)
        assertEquals(null, resolved.metadata.normalPalettePointerOffset)
        assertNotNull(resolved.tables.speciesNames)
        assertNotNull(resolved.tables.baseStats)
        assertNotNull(resolved.tables.descriptions)
        assertEquals(72, resolved.metadata.descriptionPointerOffset)
    }

    private fun fixture(): ByteArray {
        val bytes = ByteArray(0x20000)
        writePointer(bytes, 0x1BC, SPECIES_ROOT)
        listOf(0x8000, 0x9000, 0xA000, 0xB000, 0xC000, 0xD000).forEachIndexed { index, root ->
            writePointer(bytes, 0x1C0 + index * 4, root)
        }
        repeat(11) { bytes[SPECIES_ROOT + 44 + it] = 0xAC.toByte() }
        bytes[SPECIES_ROOT + 55] = 0xFF.toByte()
        for (id in 1 until 40) {
            if (id in 30..33) continue
            val row = SPECIES_ROOT + id * 152
            repeat(6) { bytes[row + it] = (40 + id).toByte() }
            bytes[row + 6] = 12
            bytes[row + 7] = 3
            writeU16(bytes, row + 24, 1)
            encodeGba(bytes, row + 31, "SEED")
            encodeGba(bytes, row + 44, if (id == 1) "BULBA" else "MON")
            writeU16(bytes, row + 56, if (id <= 3) id else 1)
            writeU16(bytes, row + 58, id)
            writeU16(bytes, row + 60, 7)
            writeU16(bytes, row + 62, 69)
            writePointer(bytes, row + 72, DESCRIPTION_ROOT)
            writePointer(bytes, row + 84, GRAPHICS_ROOT)
            writePointer(bytes, row + 100, PALETTE_ROOT)
        }
        bytes[SPECIES_ROOT + 41 * 152] = 40
        encodeGba(bytes, DESCRIPTION_ROOT, "A SEED POKEMON")
        Base64.getDecoder().decode("ASAIAAAAKAABAAAAAAH+BwEAAAA=").copyInto(bytes, GRAPHICS_ROOT)
        bytes[PALETTE_ROOT + 2] = 0x1F
        return bytes
    }

    private fun encodeGba(target: ByteArray, offset: Int, value: String) {
        value.forEachIndexed { index, char ->
            target[offset + index] = when (char) {
                in 'A'..'Z' -> (0xBB + char.code - 'A'.code).toByte()
                ' ' -> 0
                else -> error("unsupported fixture character $char")
            }
        }
        target[offset + value.length] = 0xFF.toByte()
    }

    private fun writePointer(target: ByteArray, offset: Int, romOffset: Int) =
        writeU32(target, offset, 0x08000000 + romOffset)

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun writeU32(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private companion object {
        const val SPECIES_ROOT = 0x1000
        const val DESCRIPTION_ROOT = 0x7000
        const val EMPTY_DESCRIPTION_ROOT = 0x7100
        const val GRAPHICS_ROOT = 0xE000
        const val PALETTE_ROOT = 0xF000
    }
}
