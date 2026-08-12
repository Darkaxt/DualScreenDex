package com.darkaxt.dualdex.save.gen3

import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveParser
import com.darkaxt.dualdex.save.SaveSpeciesContext
import com.darkaxt.dualdex.save.SaveByteSelector
import com.darkaxt.dualdex.save.LevelUpRulesetDetectionFingerprint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen3SaveReaderTest {
    private val context = SaveParseContext(
        romIdentity = "a".repeat(64),
        speciesById = (1..412).associateWith { SaveSpeciesContext(it, it.takeIf { value -> value <= 386 }, 0) },
        captureBallIds = (1..12).toSet(),
    )

    @Test
    fun selectsNewestCompleteSlotAndDecodesKnowledgePartyBoxesAndArea() {
        val old = fixtureSlot(counter = 4, species = 1, partyLevel = 9, boxSpecies = null)
        val current = fixtureSlot(counter = 5, species = 6, partyLevel = 36, boxSpecies = 25)
        val save = ByteArray(128 * 1024).also {
            writeSlot(it, 0, old, rotation = 3)
            writeSlot(it, 14, current, rotation = 9)
        }

        val result = SaveParser.parse(save, context) as SaveParseResult.Parsed
        val snapshot = result.snapshot

        assertEquals(5L, snapshot.saveCounter)
        assertEquals(setOf(6, 25), snapshot.seenDexNumbers)
        assertEquals(setOf(6), snapshot.caughtDexNumbers)
        assertEquals(2, snapshot.currentArea?.mapGroup)
        assertEquals(14, snapshot.currentArea?.mapNumber)
        assertEquals(6, snapshot.party.single().speciesId)
        assertEquals(36, snapshot.party.single().level)
        assertEquals(listOf(31, 30, 29, 28, 27, 26), snapshot.party.single().ivs)
        assertEquals(4, snapshot.party.single().captureBallId)
        assertEquals(25, snapshot.storedIndividuals.single().speciesId)
        assertEquals(20, snapshot.storedIndividuals.single().level)
        assertEquals(SaveCapabilityStatus.AVAILABLE, snapshot.capabilities.getValue(SaveCapability.BOXES).status)
    }

    @Test
    fun discoversExpandedPokedexLayoutFromOwnedPartyAndFlagInvariants() {
        val modernContext = SaveParseContext(
            romIdentity = "b".repeat(64),
            speciesById = (1..462).associateWith { speciesId ->
                val dexNumber = when (speciesId) {
                    in 1..251 -> speciesId
                    277 -> 252 // Treecko follows the 25 legacy Unown slots.
                    280 -> 255 // Torchic
                    286 -> 261 // Poochyena
                    288 -> 263 // Zigzagoon
                    290 -> 265 // Wurmple
                    303 -> 278 // Wingull
                    else -> null
                }
                SaveSpeciesContext(speciesId, dexNumber, 0)
            },
        )
        val slot = fixtureSlot(
            counter = 15,
            species = 277,
            context = modernContext,
            ownedOffset = 0x2C,
            flagBytes = 58,
        )
        val saveBlock2 = slot.sections.getValue(0)
        setFlag(saveBlock2, 0x2C, 261)
        listOf(255, 261, 263, 265, 278).forEach { setFlag(saveBlock2, 0x2C + 58, it) }
        val saveBlock1 = concatenate(slot.sections, 1..4)
        saveBlock1[0x234] = 2
        pokemonRecord(286, level = 4, ball = 4, personality = 19).copyInto(
            saveBlock1,
            0x238 + Gen3PokemonCodec.PARTY_RECORD_SIZE,
        )
        split(saveBlock1, slot.sections, 1..4)
        val save = ByteArray(128 * 1024).also { writeSlot(it, 0, slot, rotation = 4) }

        val parsed = SaveParser.parse(save, modernContext) as SaveParseResult.Parsed

        assertEquals(setOf(252, 261), parsed.snapshot.caughtDexNumbers)
        assertEquals(setOf(252, 255, 261, 263, 265, 278), parsed.snapshot.seenDexNumbers)
        assertEquals(listOf(277, 286), parsed.snapshot.party.map { it.speciesId })
    }

    @Test
    fun fallsBackToOlderSlotWhenNewestHasOneCorruptSector() {
        val save = ByteArray(128 * 1024).also {
            writeSlot(it, 0, fixtureSlot(counter = 9, species = 1), rotation = 0)
            writeSlot(it, 14, fixtureSlot(counter = 10, species = 6), rotation = 0)
            it[14 * Gen3Checksums.SECTOR_SIZE] = (it[14 * Gen3Checksums.SECTOR_SIZE].toInt() xor 0x40).toByte()
        }

        val parsed = SaveParser.parse(save, context) as SaveParseResult.Parsed

        assertEquals(9L, parsed.snapshot.saveCounter)
        assertEquals(1, parsed.snapshot.party.single().speciesId)
    }

    @Test
    fun rejectsTruncatedAndNonPokemonSaveData() {
        assertTrue(SaveParser.parse(ByteArray(8 * 1024), context) is SaveParseResult.Unsupported)
        assertTrue(SaveParser.parse(ByteArray(128 * 1024) { 0x5A }, context) is SaveParseResult.Unsupported)
    }

    @Test
    fun rejectsAnInvalidPokemonWithoutDroppingTheChecksumValidSnapshot() {
        val slot = fixtureSlot(counter = 3, species = 6)
        val party = slot.sections[1]!!
        party[0x238 + 32] = (party[0x238 + 32].toInt() xor 1).toByte()
        val save = ByteArray(128 * 1024).also { writeSlot(it, 0, slot, rotation = 0) }

        val parsed = SaveParser.parse(save, context) as SaveParseResult.Parsed

        assertTrue(parsed.snapshot.party.isEmpty())
        assertEquals(SaveCapabilityStatus.PARTIAL, parsed.snapshot.capabilities.getValue(SaveCapability.PARTY).status)
        assertFalse(parsed.snapshot.seenDexNumbers.isEmpty())
    }

    @Test
    fun acceptsTerminalSectionChecksumsThatExcludeStaleSectorTailBytes() {
        val save = ByteArray(128 * 1024).also {
            writeSlot(it, 0, fixtureSlot(counter = 7, species = 6, boxSpecies = 25), rotation = 5)
        }
        val terminal = physicalSector(save, logicalId = 13, counter = 7)
        val terminalOffset = terminal * Gen3Checksums.SECTOR_SIZE
        save[terminalOffset + 2048] = 0x55
        save.putU16le(terminalOffset + 0xFF6, Gen3Checksums.sector(save, terminalOffset, 1104))

        val parsed = SaveParser.parse(save, context) as SaveParseResult.Parsed

        assertEquals(7L, parsed.snapshot.saveCounter)
        save[terminalOffset + 20] = (save[terminalOffset + 20].toInt() xor 1).toByte()
        assertTrue(SaveParser.parse(save, context) is SaveParseResult.Unsupported)
    }

    @Test
    fun treatsBoxMetadataAsMetadataInsteadOfCorruptPokemonRecords() {
        val slot = fixtureSlot(counter = 11, species = 6)
        val storage = concatenate(slot.sections, 5..13)
        storage[4 + 420 * Gen3PokemonCodec.BOX_RECORD_SIZE + 19] = 0x02
        split(storage, slot.sections, 5..13)
        slot.sections.getValue(5)[4000] = 0x01
        val save = ByteArray(128 * 1024).also { writeSlot(it, 0, slot, rotation = 0) }

        val parsed = SaveParser.parse(save, context) as SaveParseResult.Parsed

        assertTrue(parsed.snapshot.storedIndividuals.isEmpty())
        assertEquals(SaveCapabilityStatus.AVAILABLE, parsed.snapshot.capabilities.getValue(SaveCapability.BOXES).status)
    }

    @Test
    fun competesTheFireRedLeafGreenPartyLayout() {
        val slot = fixtureSlot(counter = 12, species = 6, partyLevel = 44)
        val saveBlock1 = concatenate(slot.sections, 1..4)
        saveBlock1.copyInto(
            saveBlock1,
            destinationOffset = 0x38,
            startIndex = 0x238,
            endIndex = 0x238 + Gen3PokemonCodec.PARTY_RECORD_SIZE,
        )
        saveBlock1.fill(0, 0x238, 0x238 + Gen3PokemonCodec.PARTY_RECORD_SIZE)
        saveBlock1[0x34] = 1
        saveBlock1[0x234] = 202.toByte()
        split(saveBlock1, slot.sections, 1..4)
        val save = ByteArray(128 * 1024).also { writeSlot(it, 0, slot, rotation = 0) }

        val parsed = SaveParser.parse(save, context) as SaveParseResult.Parsed

        assertEquals(6, parsed.snapshot.party.single().speciesId)
        assertEquals(44, parsed.snapshot.party.single().level)
    }

    @Test
    fun derivesUnownFormFromTheSavedPersonality() {
        val save = ByteArray(128 * 1024).also {
            writeSlot(it, 0, fixtureSlot(counter = 13, species = 201), rotation = 0)
        }

        val parsed = SaveParser.parse(save, context) as SaveParseResult.Parsed

        assertEquals(1, parsed.snapshot.party.single().formId)
        assertEquals(SaveCapabilityStatus.AVAILABLE, parsed.snapshot.capabilities.getValue(SaveCapability.FORM).status)
    }

    @Test
    fun decodesChecksumOmittedFixedOrderPokemonUsedByBinaryDerivatives() {
        val slot = fixtureSlot(counter = 14, species = 6)
        val saveBlock1 = concatenate(slot.sections, 1..4)
        plainPokemonRecord(species = 25, level = 37, ball = 4, personality = 17).copyInto(saveBlock1, 0x238)
        split(saveBlock1, slot.sections, 1..4)
        val save = ByteArray(128 * 1024).also { writeSlot(it, 0, slot, rotation = 0) }

        val parsed = SaveParser.parse(save, context) as SaveParseResult.Parsed

        assertEquals(25, parsed.snapshot.party.single().speciesId)
        assertEquals(37, parsed.snapshot.party.single().level)
        assertEquals(listOf(31, 30, 29, 28, 27, 26), parsed.snapshot.party.single().ivs)
        assertEquals(4, parsed.snapshot.party.single().captureBallId)
    }

    @Test
    fun detectsTheActiveLevelUpRulesetFromAReconstructedSaveBlock1Selector() {
        val selectorContext = context.copy(
            levelUpRulesetSelectors = listOf(
                SaveByteSelector("original", 0x3DA6, 0x02, 0x00),
                SaveByteSelector("modern", 0x3DA6, 0x02, 0x02),
            ),
        )
        val slot = fixtureSlot(counter = 16, species = 6, context = selectorContext)
        val saveBlock1 = concatenate(slot.sections, 1..4)
        saveBlock1[0x3DA6] = 0x0F
        split(saveBlock1, slot.sections, 1..4)
        slot.sections.getValue(1)[0xF90] = 1 // Distinguish the full 0xFF4 chunk ABI from the legacy 0xF80 ABI.
        val save = ByteArray(128 * 1024).also { writeSlot(it, 0, slot, rotation = 8) }

        val parsed = SaveParser.parse(save, selectorContext) as SaveParseResult.Parsed

        assertEquals("modern", parsed.snapshot.detectedLevelUpRulesetId)
        assertTrue(parsed.snapshot.levelUpRulesetDetectionResolved)
        assertEquals(
            LevelUpRulesetDetectionFingerprint.create(selectorContext.levelUpRulesetSelectors, "modern"),
            parsed.snapshot.levelUpRulesetDetectionFingerprint,
        )
    }

    @Test
    fun leavesLevelUpAutoUnresolvedWhenSelectorDescriptorsConflict() {
        val selectorContext = context.copy(
            levelUpRulesetSelectors = listOf(
                SaveByteSelector("one", 0x3DA6, 0x02, 0x00),
                SaveByteSelector("two", 0x3DA6, 0x02, 0x00),
            ),
        )
        val save = ByteArray(128 * 1024).also {
            writeSlot(it, 0, fixtureSlot(counter = 17, species = 6, context = selectorContext), rotation = 8)
        }

        val parsed = SaveParser.parse(save, selectorContext) as SaveParseResult.Parsed

        assertEquals(null, parsed.snapshot.detectedLevelUpRulesetId)
        assertFalse(parsed.snapshot.levelUpRulesetDetectionResolved)
        assertEquals(null, parsed.snapshot.levelUpRulesetDetectionFingerprint)
    }

    private fun fixtureSlot(
        counter: Long,
        species: Int,
        partyLevel: Int = 20,
        boxSpecies: Int? = null,
        context: SaveParseContext = this.context,
        ownedOffset: Int = 0x28,
        flagBytes: Int = (context.internalSpeciesCount + 7) / 8,
    ): FixtureSlot {
        val sections = (0 until 14).associateWith { ByteArray(Gen3Checksums.SECTOR_DATA_SIZE) }.toMutableMap()
        val sb2 = sections.getValue(0)
        sb2.putU32le(0x0A, 0x12345678)
        val speciesDex = context.speciesById.getValue(species).dexNumber ?: species
        setFlag(sb2, ownedOffset, speciesDex)
        setFlag(sb2, ownedOffset + flagBytes, speciesDex)
        if (boxSpecies != null) {
            val boxDex = context.speciesById.getValue(boxSpecies).dexNumber ?: boxSpecies
            setFlag(sb2, ownedOffset + flagBytes, boxDex)
        }

        val sb1 = concatenate(sections, 1..4)
        sb1[4] = 2
        sb1[5] = 14
        sb1[0x234] = 1
        val party = pokemonRecord(species, partyLevel, ball = 4, personality = 17)
        party.copyInto(sb1, 0x238)
        split(sb1, sections, 1..4)

        if (boxSpecies != null) {
            val storage = concatenate(sections, 5..13)
            pokemonRecord(boxSpecies, 20, ball = 2, personality = 8).copyOf(80).copyInto(storage, 4)
            split(storage, sections, 5..13)
        }
        return FixtureSlot(counter, sections)
    }

    private fun pokemonRecord(species: Int, level: Int, ball: Int, personality: Long): ByteArray {
        val record = ByteArray(Gen3PokemonCodec.PARTY_RECORD_SIZE)
        val otId = 0x10203040L
        record.putU32le(0, personality)
        record.putU32le(4, otId)
        record[18] = 2
        record[19] = 0x02
        val decrypted = ByteArray(48)
        val logical = Array(4) { ByteArray(12) }
        logical[0].putU16le(0, species)
        logical[0].putU32le(4, Gen3Experience.required(0, level))
        logical[3].putU16le(2, ball shl 11)
        var ivWord = 0L
        listOf(31, 30, 29, 28, 27, 26).forEachIndexed { index, iv -> ivWord = ivWord or (iv.toLong() shl (index * 5)) }
        logical[3].putU32le(4, ivWord)
        val order = ORDERS[(personality % 24).toInt()]
        repeat(4) { logicalIndex -> logical[logicalIndex].copyInto(decrypted, order[logicalIndex] * 12) }
        record.putU16le(28, Gen3Checksums.pokemon(decrypted))
        val key = personality xor otId
        repeat(12) { index -> record.putU32le(32 + index * 4, decrypted.u32le(index * 4) xor key) }
        record[84] = level.toByte()
        return record
    }

    private fun plainPokemonRecord(species: Int, level: Int, ball: Int, personality: Long): ByteArray {
        val record = ByteArray(Gen3PokemonCodec.PARTY_RECORD_SIZE)
        record.putU32le(0, personality)
        record.putU32le(4, 0x10203040L)
        record[18] = 2
        record[19] = 0x02
        val logical = ByteArray(48)
        logical.putU16le(0, species)
        logical.putU32le(4, Gen3Experience.required(0, level))
        logical.putU16le(36 + 2, ball shl 11)
        var ivWord = 0L
        listOf(31, 30, 29, 28, 27, 26).forEachIndexed { index, iv -> ivWord = ivWord or (iv.toLong() shl (index * 5)) }
        logical.putU32le(36 + 4, ivWord)
        logical.copyInto(record, 32)
        record[84] = level.toByte()
        return record
    }

    private fun writeSlot(target: ByteArray, physicalStart: Int, fixture: FixtureSlot, rotation: Int) {
        for (logical in 0 until 14) {
            val physical = physicalStart + (logical + rotation) % 14
            val offset = physical * Gen3Checksums.SECTOR_SIZE
            val data = fixture.sections.getValue(logical)
            data.copyInto(target, offset)
            target.putU16le(offset + 0xFF4, logical)
            target.putU16le(offset + 0xFF6, Gen3Checksums.sector(data))
            target.putU32le(offset + 0xFF8, Gen3Checksums.SECTOR_SIGNATURE)
            target.putU32le(offset + 0xFFC, fixture.counter)
        }
    }

    private fun physicalSector(save: ByteArray, logicalId: Int, counter: Long): Int =
        (0 until 28).single { physical ->
            val offset = physical * Gen3Checksums.SECTOR_SIZE
            save.u16le(offset + 0xFF4) == logicalId && save.u32le(offset + 0xFFC) == counter
        }

    private fun setFlag(bytes: ByteArray, offset: Int, dexNumber: Int) {
        val index = dexNumber - 1
        bytes[offset + index / 8] = (bytes[offset + index / 8].toInt() or (1 shl (index % 8))).toByte()
    }

    private fun concatenate(sections: Map<Int, ByteArray>, range: IntRange): ByteArray =
        range.flatMap { sections.getValue(it).asIterable() }.toByteArray()

    private fun split(bytes: ByteArray, sections: MutableMap<Int, ByteArray>, range: IntRange) {
        range.forEachIndexed { index, section ->
            bytes.copyInto(sections.getValue(section), 0, index * Gen3Checksums.SECTOR_DATA_SIZE, (index + 1) * Gen3Checksums.SECTOR_DATA_SIZE)
        }
    }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private fun ByteArray.putU32le(offset: Int, value: Long) {
        repeat(4) { index -> this[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private data class FixtureSlot(val counter: Long, val sections: MutableMap<Int, ByteArray>)

    private companion object {
        val ORDERS = arrayOf(
            intArrayOf(0, 1, 2, 3), intArrayOf(0, 1, 3, 2), intArrayOf(0, 2, 1, 3),
            intArrayOf(0, 3, 1, 2), intArrayOf(0, 2, 3, 1), intArrayOf(0, 3, 2, 1),
            intArrayOf(1, 0, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(2, 0, 1, 3),
            intArrayOf(3, 0, 1, 2), intArrayOf(2, 0, 3, 1), intArrayOf(3, 0, 2, 1),
            intArrayOf(1, 2, 0, 3), intArrayOf(1, 3, 0, 2), intArrayOf(2, 1, 0, 3),
            intArrayOf(3, 1, 0, 2), intArrayOf(2, 3, 0, 1), intArrayOf(3, 2, 0, 1),
            intArrayOf(1, 2, 3, 0), intArrayOf(1, 3, 2, 0), intArrayOf(2, 1, 3, 0),
            intArrayOf(3, 1, 2, 0), intArrayOf(2, 3, 1, 0), intArrayOf(3, 2, 1, 0),
        )
    }
}
