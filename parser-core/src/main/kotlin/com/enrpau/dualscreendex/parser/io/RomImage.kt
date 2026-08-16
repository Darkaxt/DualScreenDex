package com.enrpau.dualscreendex.parser.io

import java.security.MessageDigest
import java.io.InputStream
import java.util.zip.CRC32

class RomBoundsException(message: String) : IllegalArgumentException(message)

class RomImage private constructor(source: ByteArray, copySource: Boolean) {
    private val bytes = if (copySource) source.copyOf() else source

    constructor(source: ByteArray) : this(source, copySource = true)

    val size: Int get() = bytes.size

    val sha256: String by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    val crc32: String by lazy {
        val crc = CRC32()
        crc.update(bytes)
        "%08X".format(crc.value)
    }

    fun u8(offset: Int): Int {
        requireRange(offset, 1)
        return bytes[offset].toInt() and 0xff
    }

    fun u16le(offset: Int): Int = u8(offset) or (u8(offset + 1) shl 8)

    fun u24le(offset: Int): Int = u16le(offset) or (u8(offset + 2) shl 16)

    fun u32le(offset: Int): Long =
        u8(offset).toLong() or
            (u8(offset + 1).toLong() shl 8) or
            (u8(offset + 2).toLong() shl 16) or
            (u8(offset + 3).toLong() shl 24)

    fun gbaPointer(offset: Int): Int? {
        val value = u32le(offset)
        return if (value in 0x08000000L..0x09FFFFFFL) {
            (value - 0x08000000L).toInt().takeIf { it in 0 until size }
        } else {
            null
        }
    }

    fun gbBankAddress(bank: Int, address: Int): Int? {
        val offset = when {
            bank == 0 && address in 0x0000..0x3FFF -> address.toLong()
            bank > 0 && address in 0x4000..0x7FFF -> bank.toLong() * 0x4000L + address - 0x4000L
            else -> return null
        }
        return offset.toInt().takeIf { offset in 0 until size.toLong() }
    }

    fun slice(offset: Int, length: Int): ByteArray {
        requireRange(offset, length)
        return bytes.copyOfRange(offset, offset + length)
    }

    fun findAll(pattern: ByteArray, start: Int = 0, endExclusive: Int = size): List<Int> {
        if (pattern.isEmpty()) return emptyList()
        requireRange(start, endExclusive - start)
        val matches = mutableListOf<Int>()
        var offset = start
        val last = endExclusive - pattern.size
        while (offset <= last) {
            var matchesAtOffset = true
            for (index in pattern.indices) {
                if (bytes[offset + index] != pattern[index]) {
                    matchesAtOffset = false
                    break
                }
            }
            if (matchesAtOffset) matches += offset
            offset++
        }
        return matches
    }

    private fun requireRange(offset: Int, length: Int) {
        if (offset < 0 || length < 0 || offset.toLong() + length.toLong() > size.toLong()) {
            throw RomBoundsException("ROM read outside 0..${size - 1}: offset=$offset length=$length")
        }
    }

    companion object {
        /** Consumes but does not close [input], leaving stream ownership with the caller. */
        fun from(input: InputStream): RomImage = RomImage(input.readBytes(), copySource = false)

        /**
         * Takes exclusive ownership of [source] without copying it. The caller must never mutate
         * the array after this call.
         */
        fun consume(source: ByteArray): RomImage = RomImage(source, copySource = false)
    }
}
