package com.darkaxt.dualdex.battle

import com.darkaxt.dualdex.save.SaveParseContext
import com.darkaxt.dualdex.save.SaveSpeciesContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3LivePartyDecoderTest {
    private val context = SaveParseContext(
        romIdentity = "rom",
        speciesById = mapOf(
            277 to SaveSpeciesContext(277, 252, 0),
            280 to SaveSpeciesContext(280, 255, 0),
        ),
    )

    @Test
    fun decodesTheCompleteLivePartyAndItsPartyLevels() {
        val bytes = ByteArray(Gen3LivePartyDecoder.PARTY_BYTES)
        plainPartyRecord(bytes, 0, species = 277, level = 5)
        plainPartyRecord(bytes, 100, species = 280, level = 7)

        val decoded = requireNotNull(Gen3LivePartyDecoder.decode(byteArrayOf(2), bytes, context))

        assertEquals(listOf(277, 280), decoded.map { it.speciesId })
        assertEquals(listOf(5, 7), decoded.map { it.level })
        assertEquals(listOf("party-0", "party-1"), decoded.map { it.stableLocation })
    }

    @Test
    fun acceptsAnEmptyPartyButRejectsPartialOrCorruptSnapshots() {
        val bytes = ByteArray(Gen3LivePartyDecoder.PARTY_BYTES)
        plainPartyRecord(bytes, 0, species = 277, level = 5)

        assertEquals(emptyList<Any>(), Gen3LivePartyDecoder.decode(byteArrayOf(0), bytes, context))
        assertNull(Gen3LivePartyDecoder.decode(byteArrayOf(2), bytes, context))
        assertNull(Gen3LivePartyDecoder.decode(byteArrayOf(7), bytes, context))
        assertNull(Gen3LivePartyDecoder.decode(byteArrayOf(1), bytes.copyOf(100), context))
    }

    private fun plainPartyRecord(bytes: ByteArray, offset: Int, species: Int, level: Int) {
        bytes[offset + 19] = 0x02
        putU16(bytes, offset + 32, species)
        putU32(bytes, offset + 36, 125)
        bytes[offset + 84] = level.toByte()
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private fun putU32(bytes: ByteArray, offset: Int, value: Int) {
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }
}
