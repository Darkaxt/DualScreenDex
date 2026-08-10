package com.darkaxt.dualdex.save.gen1

import com.darkaxt.dualdex.save.SaveCapability
import com.darkaxt.dualdex.save.SaveCapabilityStatus
import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveParseResult
import com.darkaxt.dualdex.save.SaveParser
import com.darkaxt.dualdex.save.SaveSpeciesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen1SaveReaderTest {
    private val context = SaveParseContext(
        romIdentity = "1".repeat(64),
        speciesById = (1..190).associateWith { SaveSpeciesContext(it, it.takeIf { value -> value <= 151 }, 0) },
    )

    @Test
    fun decodesMainChecksumDexPartyBoxesAreaAndFiveDvs() {
        val save = fixture()

        val snapshot = (SaveParser.parse(save, context) as SaveParseResult.Parsed).snapshot

        assertEquals(1, snapshot.saveGeneration)
        assertEquals("gen1-v1", snapshot.schemaId)
        assertEquals(setOf(6, 25), snapshot.seenDexNumbers)
        assertEquals(setOf(6), snapshot.caughtDexNumbers)
        assertEquals(12, snapshot.currentArea?.mapNumber)
        assertEquals(6, snapshot.party.single().speciesId)
        assertEquals(36, snapshot.party.single().level)
        assertEquals(listOf(11, 15, 10, 5, 1), snapshot.party.single().dvs)
        assertEquals(25, snapshot.storedIndividuals.single().speciesId)
        assertFalse(snapshot.party.single().isEgg)
        assertEquals(
            SaveCapabilityStatus.NOT_APPLICABLE,
            snapshot.capabilities.getValue(SaveCapability.CAPTURE_BALL).status,
        )
        assertEquals(SaveCapabilityStatus.NOT_APPLICABLE, snapshot.capabilities.getValue(SaveCapability.EGG).status)
    }

    @Test
    fun retainsTheChecksummedCurrentBoxWhenItsCanonicalBankIsDamaged() {
        val save = fixture()
        save[BOX_1_OFFSET + 200] = 1

        val snapshot = (SaveParser.parse(save, context) as SaveParseResult.Parsed).snapshot

        assertEquals(25, snapshot.storedIndividuals.single().speciesId)
        assertEquals(SaveCapabilityStatus.AVAILABLE, snapshot.capabilities.getValue(SaveCapability.BOXES).status)
    }

    @Test
    fun rejectsAnInvalidMainChecksum() {
        val save = fixture()
        save[MAIN_START] = (save[MAIN_START].toInt() xor 1).toByte()

        assertTrue(SaveParser.parse(save, context) is SaveParseResult.Unsupported)
    }

    @Test
    fun treatsNeverInitializedAllFfPcBanksAsEmptyBoxes() {
        val save = fixture()
        save.fill(0xFF.toByte(), 0x4000, SAVE_SIZE)
        save[CURRENT_BOX_DATA_OFFSET] = 0
        save[CURRENT_BOX_DATA_OFFSET + 1] = 0xFF.toByte()
        save[MAIN_CHECKSUM_OFFSET] = Gen1Checksums.complementedByteSum(save, MAIN_START, MAIN_END).toByte()

        val snapshot = (SaveParser.parse(save, context) as SaveParseResult.Parsed).snapshot

        assertEquals(emptyList<Int>(), snapshot.storedIndividuals.map { it.speciesId })
        assertEquals(SaveCapabilityStatus.AVAILABLE, snapshot.capabilities.getValue(SaveCapability.BOXES).status)
    }

    private fun fixture(): ByteArray = ByteArray(SAVE_SIZE).also { save ->
        save[MAIN_START] = 0x80.toByte()
        setFlag(save, OWNED_OFFSET, 6)
        setFlag(save, SEEN_OFFSET, 6)
        setFlag(save, SEEN_OFFSET, 25)
        save[MAP_OFFSET] = 12
        save[CURRENT_BOX_OFFSET] = 0

        save[PARTY_OFFSET] = 1
        save[PARTY_OFFSET + 1] = 6
        save[PARTY_OFFSET + 2] = 0xFF.toByte()
        writeMon(save, PARTY_MONS_OFFSET, species = 6, level = 36, party = true)

        writeBox(save, BOX_1_OFFSET, species = 25, level = 12)
        writeBox(save, CURRENT_BOX_DATA_OFFSET, species = 25, level = 12)
        writeEmptyBoxBanks(save)
        save[MAIN_CHECKSUM_OFFSET] = Gen1Checksums.complementedByteSum(save, MAIN_START, MAIN_END)
            .toByte()
    }

    private fun writeEmptyBoxBanks(save: ByteArray) {
        for (bankStart in listOf(0x4000, 0x6000)) {
            val checksumOffset = bankStart + 6 * BOX_SIZE
            save[checksumOffset] = Gen1Checksums.complementedByteSum(save, bankStart, checksumOffset).toByte()
            repeat(6) { index ->
                val boxOffset = bankStart + index * BOX_SIZE
                save[checksumOffset + 1 + index] = Gen1Checksums.complementedByteSum(
                    save,
                    boxOffset,
                    boxOffset + BOX_SIZE,
                ).toByte()
            }
        }
    }

    private fun writeBox(save: ByteArray, offset: Int, species: Int, level: Int) {
        save[offset] = 1
        save[offset + 1] = species.toByte()
        save[offset + 2] = 0xFF.toByte()
        writeMon(save, offset + BOX_MONS_RELATIVE, species, level, party = false)
    }

    private fun writeMon(save: ByteArray, offset: Int, species: Int, level: Int, party: Boolean) {
        save[offset] = species.toByte()
        save[offset + 3] = level.toByte()
        save[offset + 27] = 0xFA.toByte()
        save[offset + 28] = 0x51
        if (party) save[offset + 33] = level.toByte()
    }

    private fun setFlag(bytes: ByteArray, offset: Int, dex: Int) {
        val index = dex - 1
        bytes[offset + index / 8] = (bytes[offset + index / 8].toInt() or (1 shl (index % 8))).toByte()
    }

    private companion object {
        const val SAVE_SIZE = 0x8000
        const val MAIN_START = 0x2598
        const val MAIN_END = 0x3523
        const val MAIN_CHECKSUM_OFFSET = 0x3523
        const val OWNED_OFFSET = 0x25A3
        const val SEEN_OFFSET = 0x25B6
        const val MAP_OFFSET = 0x260A
        const val CURRENT_BOX_OFFSET = 0x284C
        const val PARTY_OFFSET = 0x2F2C
        const val PARTY_MONS_OFFSET = 0x2F34
        const val CURRENT_BOX_DATA_OFFSET = 0x30C0
        const val BOX_1_OFFSET = 0x4000
        const val BOX_MONS_RELATIVE = 22
        const val BOX_SIZE = 0x462
    }
}
