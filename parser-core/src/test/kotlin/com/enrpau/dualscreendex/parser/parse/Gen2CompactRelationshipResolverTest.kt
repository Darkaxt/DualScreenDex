package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.LearnsetEntry
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen2CompactRelationshipResolverTest {
    @Test fun bindsFormTargetRowsThroughTheBaseIndexHelperAndWidthConsumer() {
        val bytes = formFixture()
        val core = core(bytes)
        val contract = requireNotNull(resolve(bytes, core))
        assertEquals(BANK3 + 0x1000, contract.tableOffset)
        assertEquals(291, contract.rows)
        assertEquals(0xFF, contract.terminator)
        assertTrue(contract.formTargets)
        assertEquals(setOf(10, 6, 4), contract.wideMethods)
        assertTrue(contract.tradeMethodPresent)
        val decoded = requireNotNull(Gen2CompactRelationshipResolver.decode(
            RomImage(bytes), core.metadata.withRelationships(contract), 251))
        val edges = decoded.evolutions.getValue(1)
        assertEquals(listOf(2, 259), edges.map { it.targetSpeciesId })
        assertEquals(listOf(1, 10), edges.map { it.methodId })
        assertEquals(5, edges[1].raw.size)
        assertEquals(listOf(LearnsetEntry(5, 1), LearnsetEntry(7, 2)), decoded.learnsets.getValue(1))
        assertEquals(core.metadata.slots.size, decoded.evolutions.size)
    }

    @Test fun bindsByteRowsThroughTheWalkerAndRefusesMethodThreeWithoutTrade() {
        val bytes = byteFixture()
        val core = core(bytes)
        val contract = requireNotNull(resolve(bytes, core))
        assertEquals(0x00, contract.terminator)
        assertFalse(contract.formTargets)
        assertFalse(contract.tradeMethodPresent)
        val decoded = requireNotNull(Gen2CompactRelationshipResolver.decode(
            RomImage(bytes), core.metadata.withRelationships(contract), 251))
        assertTrue(decoded.evolutionFailures.getValue(1).contains("not a trade evolution"))
        assertEquals(listOf(LearnsetEntry(5, 1)), decoded.learnsets.getValue(1))
        assertEquals(listOf(2), decoded.evolutions.getValue(2).map { it.targetSpeciesId })
    }

    @Test fun failsClosedWithoutEveryLinkedConsumer() {
        val form = formFixture()
        val formCore = core(form)
        // Missing trade vocabulary proof.
        assertNull(resolve(form.copyOf().also { it[TRADE] = 0 }, formCore))
        // Trade branch on another method number.
        assertNull(resolve(form.copyOf().also { it[TRADE + 2] = 4 }, formCore))
        // Pointer consumer calling a different index helper.
        assertNull(resolve(form.copyOf().also { it[CONSUMER + 1] = 0x10 }, formCore))
        // Width consumer reading another bank.
        assertNull(resolve(form.copyOf().also { it[WIDTH + 29] = 2 }, formCore))
        // Target outside the species domain.
        assertNull(resolve(form.copyOf().also { it[ROW + 2] = 0xFF.toByte() }, formCore))
        // Both row ABIs at once.
        val both = form.copyOf().also { WALKER_BYTES.copyInto(it, BANK3 + 0x2400) }
        assertNull(resolve(both, formCore))
        val byte = byteFixture()
        assertNull(resolve(byte.copyOf().also { it[CONSUMER + 3] = 0 }, core(byte)))
        assertNull(resolve(byte.copyOf().also { it[BANK3 + 0x2400 + 4] = 0x60 }, core(byte)))
    }

    private fun core(bytes: ByteArray) = requireNotNull(Gen2CompactCoreResolver.resolve(session(bytes)))

    private fun resolve(bytes: ByteArray, core: Gen2CompactCoreResolution) =
        Gen2CompactRelationshipResolver.resolve(session(bytes), core, 251)

    private fun session(bytes: ByteArray) = RomAnalysisSession(RomImage(bytes), RomHeader(Platform.GBC, "CRYSTAL"))

    private fun formFixture(): ByteArray = Gen2CompactCoreResolverTest().fixture(false).also { bytes ->
        bytes.put(CONSUMER, "cd 00 02 21 00 50 09 09 2a 66 6f c9")
        bytes.put(WIDTH, "3e 03 cd 23 00 57 3c 3e 0a 28 27 7a fe 0a 28 08 fe 06 28 04 fe 04 20 01 23 23 23 e5 3e 03 cd 99 2b")
        bytes.put(TRADE, "47 fe 03 28 05 fa 00 c1 a7 c2")
        repeat(300) { bytes.word(BANK3 + 0x1000 + it * 2, 0x5400) }
        bytes.put(ROW, "01 10 02 00 0a 05 00 03 20 ff 05 01 07 02 ff")
    }

    private fun byteFixture(): ByteArray = Gen2CompactCoreResolverTest().fixture(true).also { bytes ->
        bytes.put(CONSUMER, "cd 00 03 0b 21 00 50 09 09 2a 66 6f c9")
        WALKER_BYTES.copyInto(bytes, BANK3 + 0x2400)
        bytes.put(TRADE, "47 fa 00 c1 a7 c2 00 00 78 fe")
        repeat(300) { bytes.word(BANK3 + 0x1000 + it * 2, 0x5480) }
        bytes.word(BANK3 + 0x1000, 0x5400)
        bytes.put(ROW, "03 2a 04 00 05 01 00")
        bytes.put(BANK3 + 0x1480, "01 10 02 00 05 01 00")
    }

    private fun ByteArray.put(offset: Int, value: String) = value.split(' ')
        .map { it.toInt(16).toByte() }.toByteArray().copyInto(this, offset)

    private fun ByteArray.word(offset: Int, value: Int) {
        this[offset] = value.toByte(); this[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val BANK3 = 0xC000
        const val CONSUMER = BANK3 + 0x2000
        // Bank 2: the width consumer far-reads the bank-3 table from another bank, as in Polished Crystal.
        const val WIDTH = 0xA000
        const val TRADE = BANK3 + 0x2200
        const val ROW = BANK3 + 0x1400
        // Complete pre-evolution walker over the bank-3 table at 0x5000, bound 254.
        val WALKER_BYTES = ("0e 00 21 00 50 06 00 09 09 2a 66 6f 2a a7 28 11 fe 05 20 01 23 23 fa 00 c1 be 28 0d " +
            "23 7e a7 20 eb 0c 79 fe fe 38 db a7 c9 0c 79 ea 00 c1 37 c9")
            .split(' ').map { it.toInt(16).toByte() }.toByteArray()
    }
}
