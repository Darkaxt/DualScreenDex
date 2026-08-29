package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Gen1DetachedSpeciesResolverTest {
    @Test
    fun resolvesDetachedRecordThroughOneFarCopyConsumerIndex() {
        val fixture = fixture()

        val resolved = Gen1DetachedSpeciesResolver.resolve(fixture.rom, fixture.table)

        assertEquals(setOf(1), resolved.keys)
        assertEquals(DETACHED_RECORD_OFFSET, resolved.getValue(1).offset)
    }

    @Test
    fun observesCancellationDuringFarCopyConsumerScanning() {
        val fixture = fixture()
        val cancellation = CancelAfterChecks(successfulChecks = 2)

        assertThrows(ParserCancellationException::class.java) {
            Gen1DetachedSpeciesResolver.resolve(fixture.rom, fixture.table, cancellation)
        }

        assertEquals(3, cancellation.checks)
    }

    @Test
    fun observesCancellationDuringDetachedCandidateScanning() {
        val fixture = fixture()
        val farCopyChecks = (ROM_SIZE - FAR_COPY_INSTRUCTION_BYTES) / SCAN_CHECK_INTERVAL + 1
        val successfulChecks = 1 + farCopyChecks + 1
        val cancellation = CancelAfterChecks(successfulChecks)

        assertThrows(ParserCancellationException::class.java) {
            Gen1DetachedSpeciesResolver.resolve(fixture.rom, fixture.table, cancellation)
        }

        assertEquals(successfulChecks + 1, cancellation.checks)
    }

    private fun fixture(): Fixture {
        val bytes = ByteArray(ROM_SIZE)
        writeFarCopyConsumer(bytes, FAR_COPY_CONSUMER_OFFSET, DETACHED_RECORD_OFFSET)
        writeDetachedRecord(bytes, DETACHED_RECORD_OFFSET)
        writeSprite(bytes, FRONT_SPRITE_OFFSET)
        writeSprite(bytes, BACK_SPRITE_OFFSET)
        return Fixture(
            rom = RomImage(bytes),
            table = TableLayout(offset = ORDINARY_TABLE_OFFSET, count = 1, recordSize = RECORD_SIZE),
        )
    }

    private fun writeFarCopyConsumer(bytes: ByteArray, offset: Int, recordOffset: Int) {
        val bank = recordOffset / BANK_SIZE
        val address = BANKED_ADDRESS_START + recordOffset % BANK_SIZE
        bytes[offset] = 0x21
        bytes[offset + 1] = address.toByte()
        bytes[offset + 2] = (address ushr 8).toByte()
        bytes[offset + 3] = 0x11
        bytes[offset + 6] = 0x01
        bytes[offset + 7] = RECORD_SIZE.toByte()
        bytes[offset + 9] = 0x3E
        bytes[offset + 10] = bank.toByte()
        bytes[offset + 11] = 0xCD.toByte()
    }

    private fun writeDetachedRecord(bytes: ByteArray, offset: Int) {
        bytes[offset] = 1
        for (field in 1..5) bytes[offset + field] = 10
        bytes[offset + 6] = 1
        bytes[offset + 7] = 2
        bytes[offset + DIMENSIONS_OFFSET] = 0x11
        writeU16(bytes, offset + FRONT_POINTER_OFFSET, FRONT_SPRITE_OFFSET)
        writeU16(bytes, offset + BACK_POINTER_OFFSET, BACK_SPRITE_OFFSET)
    }

    private fun writeSprite(bytes: ByteArray, offset: Int) {
        val payload = byteArrayOf(0x11, 0x3C, 0x13, 0xC1.toByte())
        payload.copyInto(bytes, offset)
    }

    private fun writeU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private class CancelAfterChecks(private val successfulChecks: Int) : ParserCancellationToken {
        var checks: Int = 0
            private set

        override fun throwIfCancellationRequested() {
            checks++
            if (checks > successfulChecks) throw ParserCancellationException()
        }
    }

    private data class Fixture(val rom: RomImage, val table: TableLayout)

    private companion object {
        const val ROM_SIZE = 0x8000
        const val ORDINARY_TABLE_OFFSET = 0x0200
        const val FAR_COPY_CONSUMER_OFFSET = 0x0100
        const val DETACHED_RECORD_OFFSET = 0x5000
        const val FRONT_SPRITE_OFFSET = 0x6000
        const val BACK_SPRITE_OFFSET = 0x6010
        const val RECORD_SIZE = 28
        const val FAR_COPY_INSTRUCTION_BYTES = 14
        const val SCAN_CHECK_INTERVAL = 4 * 1_024
        const val DIMENSIONS_OFFSET = 10
        const val FRONT_POINTER_OFFSET = 11
        const val BACK_POINTER_OFFSET = 13
        const val BANK_SIZE = 0x4000
        const val BANKED_ADDRESS_START = 0x4000
    }
}
