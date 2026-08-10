package com.darkaxt.dualdex.save.gen2

import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveParser
import com.darkaxt.dualdex.save.SaveSpeciesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2SaveReaderTest {
    private val context = SaveParseContext(
        romIdentity = "2".repeat(64),
        speciesById = (1..251).associateWith { SaveSpeciesContext(it, it, 0) },
    )

    @Test
    fun decodesCrystalPrimaryPartyEggsBoxesAreaAndFiveDvs() {
        val snapshot = (SaveParser.parse(crystalFixture(), context) as SaveParseResult.Parsed).snapshot

        assertEquals(2, snapshot.saveGeneration)
        assertEquals("gen2-crystal-v1", snapshot.schemaId)
        assertEquals(setOf(25, 172), snapshot.seenDexNumbers)
        assertEquals(setOf(25), snapshot.caughtDexNumbers)
        assertEquals(24, snapshot.currentArea?.mapGroup)
        assertEquals(5, snapshot.currentArea?.mapNumber)
        assertEquals(listOf(11, 15, 10, 5, 1), snapshot.party.first().dvs)
        assertTrue(snapshot.party[1].isEgg)
        assertEquals(172, snapshot.party[1].speciesId)
        assertEquals(6, snapshot.storedIndividuals.single().speciesId)
        assertEquals(SaveCapabilityStatus.AVAILABLE, snapshot.capabilities.getValue(SaveCapability.EGG).status)
        assertEquals(
            SaveCapabilityStatus.NOT_APPLICABLE,
            snapshot.capabilities.getValue(SaveCapability.CAPTURE_BALL).status,
        )
    }

    @Test
    fun fallsBackToTheChecksumValidCrystalBackup() {
        val save = crystalFixture()
        save.copyInto(save, CRYSTAL_BACKUP_GAME_START, CRYSTAL_GAME_START, CRYSTAL_GAME_END)
        save[CRYSTAL_BACKUP_CHECK_1] = 99
        save[CRYSTAL_BACKUP_CHECK_2] = 127
        save.putU16le(
            CRYSTAL_BACKUP_CHECKSUM,
            Gen2Checksums.byteSum16(save, CRYSTAL_BACKUP_GAME_START, CRYSTAL_BACKUP_GAME_END),
        )
        save[CRYSTAL_GAME_START + 7] = (save[CRYSTAL_GAME_START + 7].toInt() xor 1).toByte()

        val snapshot = (SaveParser.parse(save, context) as SaveParseResult.Parsed).snapshot

        assertEquals("gen2-crystal-v1", snapshot.schemaId)
        assertEquals(25, snapshot.party.first().speciesId)
        assertTrue(snapshot.capabilities.getValue(SaveCapability.SAVE_SLOT).reasons.single().contains("backup"))
    }

    @Test
    fun prefersStructurallyCompleteBackupWhenBothCrystalCopiesAreChecksumValid() {
        val save = crystalFixture()
        save.copyInto(save, CRYSTAL_BACKUP_GAME_START, CRYSTAL_GAME_START, CRYSTAL_GAME_END)
        save[CRYSTAL_BACKUP_CHECK_1] = 99
        save[CRYSTAL_BACKUP_CHECK_2] = 127
        save.putU16le(
            CRYSTAL_BACKUP_CHECKSUM,
            Gen2Checksums.byteSum16(save, CRYSTAL_BACKUP_GAME_START, CRYSTAL_BACKUP_GAME_END),
        )
        save[CRYSTAL_PARTY] = 7
        save.putU16le(CRYSTAL_CHECKSUM, Gen2Checksums.byteSum16(save, CRYSTAL_GAME_START, CRYSTAL_GAME_END))

        val snapshot = (SaveParser.parse(save, context) as SaveParseResult.Parsed).snapshot

        assertEquals(25, snapshot.party.first().speciesId)
        assertTrue(snapshot.capabilities.getValue(SaveCapability.SAVE_SLOT).reasons.single().contains("backup"))
    }

    @Test
    fun structurallyCompetesGoldAndSilverAgainstCrystal() {
        val snapshot = (SaveParser.parse(goldFixture(), context) as SaveParseResult.Parsed).snapshot

        assertEquals("gen2-gold-silver-v1", snapshot.schemaId)
        assertEquals(16, snapshot.currentArea?.mapGroup)
        assertEquals(3, snapshot.currentArea?.mapNumber)
        assertEquals(155, snapshot.party.single().speciesId)
    }

    @Test
    fun fallsBackToTheSplitGoldAndSilverBackup() {
        val save = goldFixture()
        val gameData = save.copyOfRange(GOLD_GAME_START, GOLD_GAME_END)
        gameData.copyInto(save, GOLD_BACKUP_PLAYER_1, 0, GOLD_PLAYER_1_SIZE)
        gameData.copyInto(
            save,
            GOLD_BACKUP_PLAYER_2,
            GOLD_PLAYER_1_SIZE,
            GOLD_PLAYER_1_SIZE + GOLD_PLAYER_2_SIZE,
        )
        gameData.copyInto(
            save,
            GOLD_BACKUP_PLAYER_3,
            GOLD_PLAYER_1_SIZE + GOLD_PLAYER_2_SIZE,
            GOLD_PLAYER_1_SIZE + GOLD_PLAYER_2_SIZE + GOLD_PLAYER_3_SIZE,
        )
        gameData.copyInto(
            save,
            GOLD_BACKUP_MAP,
            GOLD_PLAYER_1_SIZE + GOLD_PLAYER_2_SIZE + GOLD_PLAYER_3_SIZE,
            GOLD_PLAYER_1_SIZE + GOLD_PLAYER_2_SIZE + GOLD_PLAYER_3_SIZE + GOLD_MAP_SIZE,
        )
        gameData.copyInto(
            save,
            GOLD_BACKUP_POKEMON,
            GOLD_PLAYER_1_SIZE + GOLD_PLAYER_2_SIZE + GOLD_PLAYER_3_SIZE + GOLD_MAP_SIZE,
            gameData.size,
        )
        save[GOLD_BACKUP_CHECK_1] = 99
        save[GOLD_BACKUP_CHECK_2] = 127
        save.putU16le(
            GOLD_BACKUP_CHECKSUM,
            Gen2Checksums.byteSum16(
                listOf(
                    save.copyOfRange(GOLD_BACKUP_POKEMON, GOLD_BACKUP_POKEMON + GOLD_POKEMON_SIZE),
                    save.copyOfRange(GOLD_BACKUP_PLAYER_3, GOLD_BACKUP_PLAYER_3 + GOLD_PLAYER_3_SIZE),
                    save.copyOfRange(GOLD_BACKUP_PLAYER_1, GOLD_BACKUP_PLAYER_1 + GOLD_PLAYER_1_SIZE),
                    save.copyOfRange(GOLD_BACKUP_PLAYER_2, GOLD_BACKUP_PLAYER_2 + GOLD_PLAYER_2_SIZE),
                    save.copyOfRange(GOLD_BACKUP_MAP, GOLD_BACKUP_MAP + GOLD_MAP_SIZE),
                ),
            ),
        )
        save[GOLD_GAME_START + 7] = (save[GOLD_GAME_START + 7].toInt() xor 1).toByte()

        val snapshot = (SaveParser.parse(save, context) as SaveParseResult.Parsed).snapshot

        assertEquals("gen2-gold-silver-v1", snapshot.schemaId)
        assertEquals(155, snapshot.party.single().speciesId)
        assertTrue(snapshot.capabilities.getValue(SaveCapability.SAVE_SLOT).reasons.single().contains("backup"))
    }

    @Test
    fun derivesTheUnownFormFromGenerationTwoDvs() {
        val save = crystalFixture()
        writeParty(save, CRYSTAL_PARTY, listOf(201 to false))
        save.putU16le(CRYSTAL_CHECKSUM, Gen2Checksums.byteSum16(save, CRYSTAL_GAME_START, CRYSTAL_GAME_END))

        val snapshot = (SaveParser.parse(save, context) as SaveParseResult.Parsed).snapshot

        assertEquals(21, snapshot.party.single().formId)
    }

    @Test
    fun treatsNeverInitializedAllFfPcBanksAsEmptyBoxes() {
        val save = crystalFixture()
        save.fill(0xFF.toByte(), 0x4000, SAVE_SIZE)

        val snapshot = (SaveParser.parse(save, context) as SaveParseResult.Parsed).snapshot

        assertEquals(emptyList<Int>(), snapshot.storedIndividuals.map { it.speciesId })
        assertEquals(SaveCapabilityStatus.AVAILABLE, snapshot.capabilities.getValue(SaveCapability.BOXES).status)
    }

    @Test
    fun rejectsAFileWithoutAnyValidGenerationTwoCopy() {
        val save = crystalFixture()
        save[CRYSTAL_GAME_START] = (save[CRYSTAL_GAME_START].toInt() xor 1).toByte()

        assertTrue(SaveParser.parse(save, context) is SaveParseResult.Unsupported)
    }

    private fun crystalFixture(): ByteArray = ByteArray(SAVE_SIZE).also { save ->
        save[CRYSTAL_CHECK_1] = 99
        save[CRYSTAL_CHECK_2] = 127
        save[CRYSTAL_GAME_START] = 0x12
        save[CRYSTAL_GAME_START + 1] = 0x34
        save[CRYSTAL_MAP_GROUP] = 24
        save[CRYSTAL_MAP_NUMBER] = 5
        writeParty(save, CRYSTAL_PARTY, listOf(25 to false, 172 to true))
        setFlag(save, CRYSTAL_CAUGHT, 25)
        setFlag(save, CRYSTAL_SEEN, 25)
        setFlag(save, CRYSTAL_SEEN, 172)
        writeBox(save, BOX_1_OFFSET, 6, 36)
        save.putU16le(CRYSTAL_CHECKSUM, Gen2Checksums.byteSum16(save, CRYSTAL_GAME_START, CRYSTAL_GAME_END))
    }

    private fun goldFixture(): ByteArray = ByteArray(SAVE_SIZE).also { save ->
        save[GOLD_CHECK_1] = 99
        save[GOLD_CHECK_2] = 127
        save[GOLD_GAME_START] = 0x56
        save[GOLD_GAME_START + 1] = 0x78
        save[GOLD_MAP_GROUP] = 16
        save[GOLD_MAP_NUMBER] = 3
        writeParty(save, GOLD_PARTY, listOf(155 to false))
        setFlag(save, GOLD_CAUGHT, 155)
        setFlag(save, GOLD_SEEN, 155)
        save.putU16le(GOLD_CHECKSUM, Gen2Checksums.byteSum16(save, GOLD_GAME_START, GOLD_GAME_END))
    }

    private fun writeParty(save: ByteArray, offset: Int, mons: List<Pair<Int, Boolean>>) {
        save[offset] = mons.size.toByte()
        mons.forEachIndexed { index, (species, egg) ->
            save[offset + 1 + index] = if (egg) 0xFD.toByte() else species.toByte()
            writeMon(save, offset + PARTY_MONS_RELATIVE + index * PARTY_MON_SIZE, species, if (egg) 5 else 22)
        }
        save[offset + 1 + mons.size] = 0xFF.toByte()
    }

    private fun writeBox(save: ByteArray, offset: Int, species: Int, level: Int) {
        save[offset] = 1
        save[offset + 1] = species.toByte()
        save[offset + 2] = 0xFF.toByte()
        writeMon(save, offset + BOX_MONS_RELATIVE, species, level)
    }

    private fun writeMon(save: ByteArray, offset: Int, species: Int, level: Int) {
        save[offset] = species.toByte()
        save[offset + 21] = 0xFA.toByte()
        save[offset + 22] = 0x51
        save[offset + 31] = level.toByte()
    }

    private fun setFlag(bytes: ByteArray, offset: Int, dex: Int) {
        val index = dex - 1
        bytes[offset + index / 8] = (bytes[offset + index / 8].toInt() or (1 shl (index % 8))).toByte()
    }

    private fun ByteArray.putU16le(offset: Int, value: Int) {
        this[offset] = value.toByte()
        this[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val SAVE_SIZE = 0x8000
        const val BOX_1_OFFSET = 0x4000
        const val BOX_MONS_RELATIVE = 22
        const val PARTY_MONS_RELATIVE = 8
        const val PARTY_MON_SIZE = 48

        const val CRYSTAL_CHECK_1 = 0x2008
        const val CRYSTAL_GAME_START = 0x2009
        const val CRYSTAL_GAME_END = 0x2B83
        const val CRYSTAL_CHECKSUM = 0x2D0D
        const val CRYSTAL_CHECK_2 = 0x2D0F
        const val CRYSTAL_MAP_GROUP = 0x2843
        const val CRYSTAL_MAP_NUMBER = 0x2844
        const val CRYSTAL_PARTY = 0x2865
        const val CRYSTAL_CAUGHT = 0x2A27
        const val CRYSTAL_SEEN = 0x2A47
        const val CRYSTAL_BACKUP_CHECK_1 = 0x1208
        const val CRYSTAL_BACKUP_GAME_START = 0x1209
        const val CRYSTAL_BACKUP_GAME_END = 0x1D83
        const val CRYSTAL_BACKUP_CHECKSUM = 0x1F0D
        const val CRYSTAL_BACKUP_CHECK_2 = 0x1F0F

        const val GOLD_CHECK_1 = 0x2008
        const val GOLD_GAME_START = 0x2009
        const val GOLD_GAME_END = 0x2D69
        const val GOLD_CHECKSUM = 0x2D69
        const val GOLD_CHECK_2 = 0x2D6B
        const val GOLD_MAP_GROUP = 0x2868
        const val GOLD_MAP_NUMBER = 0x2869
        const val GOLD_PARTY = 0x288A
        const val GOLD_CAUGHT = 0x2A4C
        const val GOLD_SEEN = 0x2A6C

        const val GOLD_BACKUP_PLAYER_3 = 0x0C6B
        const val GOLD_BACKUP_POKEMON = 0x10E8
        const val GOLD_BACKUP_PLAYER_1 = 0x15C7
        const val GOLD_BACKUP_PLAYER_2 = 0x3D96
        const val GOLD_BACKUP_CHECK_1 = 0x7E38
        const val GOLD_BACKUP_MAP = 0x7E39
        const val GOLD_BACKUP_CHECKSUM = 0x7E6D
        const val GOLD_BACKUP_CHECK_2 = 0x7E6F
        const val GOLD_PLAYER_1_SIZE = 0x226
        const val GOLD_PLAYER_2_SIZE = 0x1AA
        const val GOLD_PLAYER_3_SIZE = 0x47D
        const val GOLD_MAP_SIZE = 0x34
        const val GOLD_POKEMON_SIZE = 0x4DF
    }
}
