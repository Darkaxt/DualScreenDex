package com.enrpau.dualscreendex.parser.analysis.arm7

import com.enrpau.dualscreendex.parser.io.RomImage

class Arm7MemoryAccessException(message: String) : IllegalStateException(message)

data class Arm7MemoryTrace(
    val sequence: Long,
    val direction: Arm7MemoryDirection,
    val address: Long,
    val width: Arm7MemoryWidth,
    val value: Long,
)

class Arm7Memory(romBytes: ByteArray) {
    private val rom = romBytes.copyOf()
    private val ewram = ByteArray(EWRAM_SIZE)
    private val iwram = ByteArray(IWRAM_SIZE)
    private val trace = mutableListOf<Arm7MemoryTrace>()
    private var sequence = 0L

    val romImage: RomImage = RomImage(rom)
    val romSize: Int get() = rom.size

    fun traces(): List<Arm7MemoryTrace> = trace.toList()

    fun clearTrace() = trace.clear()

    fun read8(address: Long): Long = accessRead(address, Arm7MemoryWidth.BYTE) { bytes, offset ->
        bytes[offset].toLong() and 0xFF
    }

    fun read16(address: Long): Long = accessRead(address, Arm7MemoryWidth.HALFWORD) { bytes, offset ->
        (bytes[offset].toLong() and 0xFF) or ((bytes[offset + 1].toLong() and 0xFF) shl 8)
    }

    fun read32(address: Long): Long = accessRead(address, Arm7MemoryWidth.WORD) { bytes, offset ->
        (bytes[offset].toLong() and 0xFF) or
            ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
            ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
            ((bytes[offset + 3].toLong() and 0xFF) shl 24)
    }

    fun write8(address: Long, value: Long) = accessWrite(address, Arm7MemoryWidth.BYTE, value) { bytes, offset ->
        bytes[offset] = value.toByte()
    }

    fun write16(address: Long, value: Long) = accessWrite(address, Arm7MemoryWidth.HALFWORD, value) { bytes, offset ->
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    fun write32(address: Long, value: Long) = accessWrite(address, Arm7MemoryWidth.WORD, value) { bytes, offset ->
        for (byte in 0..3) bytes[offset + byte] = (value ushr (byte * 8)).toByte()
    }

    fun romOffset(address: Long, length: Int): Int? {
        val offset = address - ROM_START
        return offset.toInt().takeIf {
            offset >= 0 && offset + length.toLong() <= rom.size.toLong()
        }
    }

    private inline fun accessRead(
        address: Long,
        width: Arm7MemoryWidth,
        read: (ByteArray, Int) -> Long,
    ): Long {
        val (bytes, offset) = mapped(address, width.bytes, write = false)
        val value = read(bytes, offset) and 0xFFFF_FFFFL
        trace += Arm7MemoryTrace(sequence++, Arm7MemoryDirection.READ, address, width, value)
        return value
    }

    private inline fun accessWrite(
        address: Long,
        width: Arm7MemoryWidth,
        value: Long,
        write: (ByteArray, Int) -> Unit,
    ) {
        val (bytes, offset) = mapped(address, width.bytes, write = true)
        write(bytes, offset)
        trace += Arm7MemoryTrace(sequence++, Arm7MemoryDirection.WRITE, address, width, value and 0xFFFF_FFFFL)
    }

    private fun mapped(address: Long, length: Int, write: Boolean): Pair<ByteArray, Int> {
        fun within(start: Long, size: Int): Int? = (address - start).takeIf {
            it >= 0 && it + length.toLong() <= size.toLong()
        }?.toInt()

        within(ROM_START, rom.size)?.let { offset ->
            if (write) throw Arm7MemoryAccessException("write to immutable ROM at 0x${address.toString(16)}")
            return rom to offset
        }
        within(EWRAM_START, ewram.size)?.let { return ewram to it }
        within(IWRAM_START, iwram.size)?.let { return iwram to it }
        throw Arm7MemoryAccessException("unmapped ARM7 memory at 0x${address.toString(16)} length=$length")
    }

    companion object {
        const val ROM_START = 0x0800_0000L
        const val EWRAM_START = 0x0200_0000L
        const val IWRAM_START = 0x0300_0000L
        const val EWRAM_SIZE = 256 * 1024
        const val IWRAM_SIZE = 32 * 1024
    }
}
