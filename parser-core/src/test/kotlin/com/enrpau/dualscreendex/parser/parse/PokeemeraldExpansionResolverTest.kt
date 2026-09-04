package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.family.resolvedLayout
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageTag
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import com.enrpau.dualscreendex.parser.text.PokemonTextToken
import com.enrpau.dualscreendex.parser.text.PokemonTextTokenDecoder
import com.enrpau.dualscreendex.parser.text.WesternPokemonTextCodecs
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PokeemeraldExpansionResolverTest {
    @Test
    fun resolvesPublishedCountsPointersAndValidatedRecordShapes() {
        val bytes = fixture()

        val resolved = resolve(RomImage(bytes))

        assertNotNull(resolved)
        requireNotNull(resolved)
        assertEquals(20, resolved.speciesCount)
        assertEquals(16, resolved.moveCount)
        assertEquals(8, resolved.abilityCount)
        assertEquals(0x1000 + 44, resolved.tables.speciesNames?.offset)
        assertEquals(180, resolved.tables.speciesNames?.stride)
        assertEquals(0x5000, resolved.tables.moveNames?.offset)
        assertEquals(64, resolved.tables.moveNames?.stride)
        assertEquals(true, resolved.tables.moveNames?.valuesArePointers)
        assertEquals(0x7000, resolved.tables.abilities?.offset)
        assertEquals(28, resolved.tables.abilities?.stride)
        assertEquals(1, resolved.firstRegisters.speciesNationalDex)
        assertEquals("BULBA", resolved.firstRegisters.speciesName)
        assertEquals("POUND", resolved.firstRegisters.moveName)
        assertEquals("STENCH", resolved.firstRegisters.abilityName)
    }

    @Test
    fun samplesMoveTableAcrossPublishedExtentToRejectAliasedStride() {
        val resolved = requireNotNull(
            resolve(
                RomImage(fixture(moveCount = 80, moveStride = 48)),
            ),
        )

        assertEquals(48, resolved.tables.moveNames?.stride)
    }

    @Test
    fun scoresNationalDexFieldAcrossActiveSpeciesInsteadOfAcceptingAnEarlyCryAlias() {
        val resolved = requireNotNull(
            resolve(
                RomImage(
                    fixture(
                        speciesCount = 80,
                        nationalDexOffset = 62,
                        cryAliasThrough = 24,
                    ),
                ),
            ),
        )

        assertEquals(62, resolved.metadata.nationalDexOffset)
        assertEquals(64, resolved.metadata.heightOffset)
        assertEquals(66, resolved.metadata.weightOffset)
        assertEquals(76, resolved.metadata.descriptionPointerOffset)
        assertEquals(88, resolved.metadata.frontSpritePointerOffset)
        assertEquals(96, resolved.metadata.normalPalettePointerOffset)
    }

    @Test
    fun validatesOnlyPositiveDexRowsInSparsePublishedSpeciesExtents() {
        val bytes = fixture(speciesCount = 80, activeSpeciesCount = 40).also {
            Base64.getDecoder().decode("ASAIAAAAKAABAAAAAAH+BwEAAAA=").copyInto(it, 0x8100)
            it[0x8200 + 2] = 0x1F
        }
        val rom = RomImage(bytes)
        val resolved = requireNotNull(resolve(rom))

        val evidence = listOf(
            PokeemeraldExpansionResolver.validateSpeciesNames(rom, resolved, PokemonTextCodec.gbaEnglish),
            PokeemeraldExpansionResolver.validateBaseStats(rom, resolved),
            PokeemeraldExpansionResolver.validateDescriptions(rom, resolved, PokemonTextCodec.gbaEnglish),
            PokeemeraldExpansionResolver.validateSprites(rom, resolved),
            PokeemeraldExpansionResolver.validateLearnsets(rom, resolved),
            PokeemeraldExpansionResolver.validateEvolutions(rom, resolved),
        )

        evidence.forEach {
            assertEquals(true, it.compatible)
            assertEquals(40, it.validRecords)
            assertEquals(80, it.totalRecords)
            assertEquals(40, it.coveredRecords)
            assertEquals(40, it.expectedRecords)
            assertEquals(0, it.incompleteRecords)
        }
        assertEquals(
            80,
            requireNotNull(
                resolvedLayout(requireNotNull(resolved.tables.speciesNames), evidence.first()),
            ).count,
        )
    }

    @Test
    fun infersCompleteSixByteEightByteAndTwelveByteEvolutionRecords() {
        val sixByte = requireNotNull(
            resolve(RomImage(fixture(evolutionRecordSize = 6))),
        )
        val eightByte = requireNotNull(
            resolve(RomImage(fixture(evolutionRecordSize = 8))),
        )
        val twelveByte = requireNotNull(
            resolve(RomImage(fixture(evolutionRecordSize = 12))),
        )

        assertEquals(6, sixByte.metadata.evolutionRecordSize)
        assertEquals(6, sixByte.tables.evolutions?.elementSize)
        assertEquals(8, eightByte.metadata.evolutionRecordSize)
        assertEquals(8, eightByte.tables.evolutions?.elementSize)
        assertEquals(12, twelveByte.metadata.evolutionRecordSize)
        assertEquals(12, twelveByte.tables.evolutions?.elementSize)
    }

    @Test
    fun disablesOnlyEvolutionResolutionWhenNoCandidateAbiTerminates() {
        val rom = RomImage(fixture(evolutionRecordSize = 6, terminateEvolutions = false))
        val resolved = requireNotNull(resolve(rom))

        assertEquals(null, resolved.metadata.evolutionRecordSize)
        assertEquals(null, resolved.tables.evolutions)
        assertEquals(false, PokeemeraldExpansionResolver.validateEvolutions(rom, resolved).compatible)
        assertNotNull(resolved.tables.speciesNames)
        assertNotNull(resolved.tables.baseStats)
    }

    @Test
    fun descriptionValidationPreservesTheSpeciesTableRootAndStride() {
        val bytes = fixture()
        val resolved = requireNotNull(resolve(RomImage(bytes)))

        val evidence = PokeemeraldExpansionResolver.validateDescriptions(
            RomImage(bytes),
            resolved,
            PokemonTextCodec.gbaEnglish,
        )

        assertEquals(true, evidence.compatible)
        assertEquals(0x1000, evidence.offset)
        assertEquals(180, evidence.recordSize)
    }

    @Test
    fun spriteValidationRequiresDecodableExpansionGraphicsInsteadOfOnlyAnInBoundsPointer() {
        val valid = fixture().also { bytes ->
            Base64.getDecoder().decode("ASAIAAAAKAABAAAAAAH+BwEAAAA=").copyInto(bytes, 0x8100)
            bytes[0x8200 + 2] = 0x1F
        }
        val validResolution = requireNotNull(resolve(RomImage(valid)))
        val validEvidence = PokeemeraldExpansionResolver.validateSprites(RomImage(valid), validResolution)
        assertEquals(true, validEvidence.compatible)
        assertEquals(19, validEvidence.validRecords)

        val malformed = valid.copyOf().also { it[0x8100] = 7 }
        val malformedResolution = requireNotNull(resolve(RomImage(malformed)))
        val malformedEvidence = PokeemeraldExpansionResolver.validateSprites(RomImage(malformed), malformedResolution)
        assertEquals(false, malformedEvidence.compatible)
        assertEquals(0, malformedEvidence.validRecords)
    }

    @Test
    fun expansionDiscoveryUsesTheSuppliedEnglishCodec() {
        val rejectingEnglishCodec = PokemonTextCodec(
            id = "test-gba-expansion-rejecting-en",
            version = 1,
            language = LanguageTag.ENGLISH,
            applicableGenerations = setOf(3),
            applicablePlatforms = setOf(Platform.GBA),
            terminator = 0xFF,
            tokenDecoder = PokemonTextTokenDecoder { rom, offset, _ ->
                if (rom.u8(offset) == 0xFF) {
                    PokemonTextToken.Terminator()
                } else {
                    PokemonTextToken.Invalid()
                }
            },
        )

        assertNull(PokeemeraldExpansionResolver.resolve(RomImage(fixture()), rejectingEnglishCodec))
    }

    @Test
    fun expansionTextDiscoveryIsExplicitlyEnglishScoped() {
        assertNull(
            PokeemeraldExpansionResolver.resolve(
                RomImage(fixture()),
                WesternPokemonTextCodecs.gen3French,
            ),
        )
    }

    private fun resolve(rom: RomImage): PokeemeraldExpansionResolution? =
        PokeemeraldExpansionResolver.resolve(rom, PokemonTextCodec.gbaEnglish)

    private fun fixture(
        moveCount: Int = 16,
        moveStride: Int = 64,
        speciesCount: Int = 20,
        activeSpeciesCount: Int = speciesCount - 1,
        nationalDexOffset: Int = 60,
        cryAliasThrough: Int = 0,
        evolutionRecordSize: Int = 12,
        terminateEvolutions: Boolean = true,
    ): ByteArray {
        val bytes = ByteArray(0x9000)
        writeAscii(bytes, 0x108, "pokemon emerald version")
        writePointer(bytes, 0x1BC, 0x1000)
        writePointer(bytes, 0x1CC, 0x5000)

        writeAscii(bytes, 0x204, "RHHEXP")
        bytes[0x20A] = 1
        bytes[0x20B] = 15
        bytes[0x20C] = 3
        writeU16(bytes, 0x20E, moveCount)
        writeU16(bytes, 0x210, speciesCount)
        writeU16(bytes, 0x212, 8)
        writePointer(bytes, 0x214, 0x7000)

        repeat(speciesCount) { id ->
            val base = 0x1000 + id * 180
            if (id in 1..activeSpeciesCount) {
                repeat(6) { bytes[base + it] = (40 + id).toByte() }
                bytes[base + 6] = 12
                bytes[base + 7] = 3
                bytes[base + 21] = 4
                writeU16(bytes, base + 24, 65)
                encodeGba(bytes, base + 31, "SEED")
                encodeGba(bytes, base + 44, if (id == 1) "BULBA" else "MON")
                if (nationalDexOffset > 60) {
                    writeU16(bytes, base + 60, if (id <= cryAliasThrough) id else 1)
                }
                writeU16(bytes, base + nationalDexOffset, id)
                writeU16(bytes, base + nationalDexOffset + 2, 7)
                writeU16(bytes, base + nationalDexOffset + 4, 69)
                writePointer(bytes, base + 76, 0x8000)
                writePointer(bytes, base + 88, 0x8100)
                writePointer(bytes, base + 96, 0x8200)
                writePointer(bytes, base + 148, 0x8300)
                writePointer(bytes, base + 152, 0x8400)
                writePointer(bytes, base + 156, 0x8500)
                writePointer(bytes, base + 160, 0x8600)
                writeU32(bytes, base + 164, 1)
            }
        }

        repeat(moveCount) { id ->
            val base = 0x5000 + id * moveStride
            writePointer(bytes, base, 0x6000 + id * 16)
            writePointer(bytes, base + 4, 0x6500)
            encodeGba(bytes, 0x6000 + id * 16, if (id == 1) "POUND" else if (id == 0) "NONE" else "MOVE")
            val type = if (id == 2) 19 else 1
            writeU16(bytes, base + 10, type or (0 shl 5) or (40 shl 7))
            writeU16(bytes, base + 12, 100)
            bytes[base + 14] = 35
        }

        repeat(8) { id ->
            val base = 0x7000 + id * 28
            encodeGba(bytes, base, if (id == 1) "STENCH" else if (id == 0) "NONE" else "ABILITY")
            writePointer(bytes, base + 20, 0x6500)
        }
        encodeGba(bytes, 0x6500, "DESCRIPTION")
        encodeGba(bytes, 0x8000, "A SEED POKEMON")
        val evolutionEntries = if (terminateEvolutions) 1 else 32
        repeat(evolutionEntries) { index ->
            val entry = 0x8600 + index * evolutionRecordSize
            writeU16(bytes, entry, 1)
            writeU16(bytes, entry + 2, 16)
            writeU16(bytes, entry + 4, 2)
        }
        if (terminateEvolutions) writeU16(bytes, 0x8600 + evolutionRecordSize, 0xFFFF)
        repeat(20 * 20) { index -> writeU32(bytes, 0x8800 + index * 4, 4096) }
        writeU32(bytes, 0x8800 + (1 * 20 + 6) * 4, 2048)
        writeU32(bytes, 0x8800 + (1 * 20 + 8) * 4, 0)
        writeU32(bytes, 0x8800 + (2 * 20 + 1) * 4, 8192)
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

    private fun writeAscii(target: ByteArray, offset: Int, value: String) {
        value.toByteArray(Charsets.US_ASCII).copyInto(target, offset)
    }

    private fun writeU16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = value.toByte()
        target[offset + 1] = (value ushr 8).toByte()
    }

    private fun writePointer(target: ByteArray, offset: Int, romOffset: Int) {
        val value = 0x08000000 + romOffset
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun writeU32(target: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> target[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
