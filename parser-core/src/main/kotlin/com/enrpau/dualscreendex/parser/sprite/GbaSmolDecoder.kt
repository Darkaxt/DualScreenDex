package com.enrpau.dualscreendex.parser.sprite

/** Decoder for pokeemerald-expansion's SMOL graphics modes 1 through 6 and tilemap mode 8. */
internal object GbaSmolDecoder {
    fun decode(input: ByteArray): ByteArray {
        require(input.size >= HEADER_BYTES) { "truncated SMOL header" }
        val first = input.u32le(0)
        val second = input.u32le(4)
        val mode = first and MODE_MASK
        if (mode == IS_TILEMAP) return decodeTilemap(input, first, second)
        require(mode in BASE_ONLY..ENCODE_BOTH_DELTA_SYMS) { "unsupported SMOL mode $mode" }

        val outputSize = decodedSize(first, mode)
        val symbolCount = (first ushr SYMBOL_SIZE_OFFSET) and SYMBOL_SIZE_MASK
        val initialState = second and INITIAL_STATE_MASK
        val bitstreamWords = (second ushr BITSTREAM_SIZE_OFFSET) and BITSTREAM_SIZE_MASK
        val loCount = (second ushr LO_SIZE_OFFSET) and LO_SIZE_MASK

        val loEncoded = mode == ENCODE_LO || mode == ENCODE_BOTH || mode == ENCODE_BOTH_DELTA_SYMS
        val symbolsEncoded = mode == ENCODE_SYMS || mode == ENCODE_DELTA_SYMS ||
            mode == ENCODE_BOTH || mode == ENCODE_BOTH_DELTA_SYMS
        val symbolsDelta = mode == ENCODE_DELTA_SYMS || mode == ENCODE_BOTH_DELTA_SYMS
        var cursor = HEADER_BYTES

        val loTable = if (loEncoded) {
            decodeTable(input, cursor).also { cursor += FREQUENCY_BYTES }
        } else {
            null
        }
        val symbolTable = if (symbolsEncoded) {
            decodeTable(input, cursor).also { cursor += FREQUENCY_BYTES }
        } else {
            null
        }
        val bitstreamBytes = Math.multiplyExact(bitstreamWords, 4)
        require(cursor <= input.size - bitstreamBytes) { "truncated SMOL bitstream" }
        val bits = BitReader(input, cursor, bitstreamBytes)
        cursor += bitstreamBytes

        val unencodedBytes = Math.addExact(
            if (symbolsEncoded) 0 else Math.multiplyExact(symbolCount, 2),
            if (loEncoded) 0 else loCount,
        )
        require(cursor <= input.size - unencodedBytes) { "truncated SMOL data vectors" }

        val rawSymbols = if (symbolsEncoded) {
            null
        } else {
            IntArray(symbolCount) { index -> input.u16le(cursor + index * 2) }
                .also { cursor += symbolCount * 2 }
        }
        val rawLo = if (loEncoded) {
            null
        } else {
            input.copyOfRange(cursor, cursor + loCount).also { cursor += loCount }
        }

        var state = initialState
        val lo = rawLo ?: decodeNibbles(
            table = requireNotNull(loTable),
            count = Math.multiplyExact(loCount, 2),
            bits = bits,
            initialState = state,
            advanceAfterLast = symbolsEncoded,
        ).also { state = it.finalState }.values.packNibblesToBytes()

        val symbols = rawSymbols ?: decodeNibbles(
            table = requireNotNull(symbolTable),
            count = Math.multiplyExact(symbolCount, 4),
            bits = bits,
            initialState = state,
            advanceAfterLast = false,
        ).values.let { nibbles ->
            if (symbolsDelta) nibbles.deltaDecoded() else nibbles
        }.packNibblesToShorts()

        return expand(lo, symbols, outputSize)
    }

    fun decodedSize(input: ByteArray): Int {
        require(input.size >= HEADER_BYTES) { "truncated SMOL header" }
        val first = input.u32le(0)
        return decodedSize(first, first and MODE_MASK)
    }

    fun encodedLength(input: ByteArray): Int {
        val encodedLength = encodedLengthFromHeader(input)
        require(encodedLength <= input.size) { "truncated SMOL stream" }
        return encodedLength
    }

    fun encodedLengthFromHeader(input: ByteArray): Int {
        require(input.size >= HEADER_BYTES) { "truncated SMOL header" }
        val first = input.u32le(0)
        val second = input.u32le(4)
        val mode = first and MODE_MASK
        decodedSize(first, mode)
        val symbolCount = (first ushr SYMBOL_SIZE_OFFSET) and SYMBOL_SIZE_MASK
        if (mode == IS_TILEMAP) {
            require(second > 0) { "empty SMOL tilemap instruction vector" }
            val symbolBytes = Math.multiplyExact(symbolCount, 2)
            return aligned4(
                Math.addExact(
                    Math.addExact(HEADER_BYTES, aligned4(symbolBytes)),
                    second,
                ),
            )
        }

        val bitstreamWords = (second ushr BITSTREAM_SIZE_OFFSET) and BITSTREAM_SIZE_MASK
        val loCount = (second ushr LO_SIZE_OFFSET) and LO_SIZE_MASK
        val loEncoded = mode == ENCODE_LO || mode == ENCODE_BOTH || mode == ENCODE_BOTH_DELTA_SYMS
        val symbolsEncoded = mode == ENCODE_SYMS || mode == ENCODE_DELTA_SYMS ||
            mode == ENCODE_BOTH || mode == ENCODE_BOTH_DELTA_SYMS
        val bytes = HEADER_BYTES +
            (if (loEncoded) FREQUENCY_BYTES else 0) +
            (if (symbolsEncoded) FREQUENCY_BYTES else 0) +
            Math.multiplyExact(bitstreamWords, 4) +
            (if (symbolsEncoded) 0 else Math.multiplyExact(symbolCount, 2)) +
            (if (loEncoded) 0 else loCount)
        return aligned4(bytes)
    }

    private fun decodedSize(first: Int, mode: Int): Int {
        require(mode in BASE_ONLY..ENCODE_BOTH_DELTA_SYMS || mode == IS_TILEMAP) {
            "unsupported SMOL mode $mode"
        }
        val unitBytes = if (mode == IS_TILEMAP) 1 else SMOL_IMAGE_SIZE_MULTIPLIER
        val outputSize = Math.multiplyExact(
            (first ushr IMAGE_SIZE_OFFSET) and IMAGE_SIZE_MASK,
            unitBytes,
        )
        require(outputSize > 0 && outputSize % 2 == 0) { "invalid SMOL output size $outputSize" }
        return outputSize
    }

    private fun decodeTilemap(input: ByteArray, first: Int, second: Int): ByteArray {
        val outputSize = decodedSize(first, IS_TILEMAP)
        val symbolCount = (first ushr SYMBOL_SIZE_OFFSET) and SYMBOL_SIZE_MASK
        val instructionCount = second
        require(symbolCount > 0) { "empty SMOL tilemap symbol vector" }
        require(instructionCount > 0) { "empty SMOL tilemap instruction vector" }
        val symbolBytes = Math.multiplyExact(symbolCount, 2)
        val instructionOffset = HEADER_BYTES + aligned4(symbolBytes)
        require(instructionOffset <= input.size - instructionCount) { "truncated SMOL tilemap data" }
        val symbols = IntArray(symbolCount) { index -> input.u16le(HEADER_BYTES + index * 2) }
        val instructions = input.copyOfRange(instructionOffset, instructionOffset + instructionCount)
        val output = expand(instructions, symbols, outputSize)
        var previous = 0
        repeat(outputSize / 2) { index ->
            val offset = index * 2
            previous = (previous + output.u16le(offset)) and 0xFFFF
            output[offset] = previous.toByte()
            output[offset + 1] = (previous ushr 8).toByte()
        }
        return output
    }

    private fun aligned4(value: Int): Int = Math.addExact(value, 3) and -4

    private fun decodeTable(input: ByteArray, offset: Int): Array<DecodeEntry> {
        require(offset <= input.size - FREQUENCY_BYTES) { "truncated SMOL frequency table" }
        val words = IntArray(3) { index -> input.u32le(offset + index * 4) }
        val frequencies = IntArray(16)
        repeat(3) { word ->
            repeat(5) { column -> frequencies[word * 5 + column] = (words[word] ushr (column * 6)) and 0x3F }
        }
        frequencies[15] = repeatBits(words)
        require(frequencies.sum() == TABLE_SIZE) { "invalid SMOL frequency total ${frequencies.sum()}" }
        val table = ArrayList<DecodeEntry>(TABLE_SIZE)
        frequencies.forEachIndexed { symbol, frequency ->
            repeat(frequency) { occurrence ->
                val y = frequency + occurrence
                var k = 0
                while ((y shl k) < TABLE_SIZE) k++
                table += DecodeEntry(symbol, y, k)
            }
        }
        require(table.size == TABLE_SIZE)
        return table.toTypedArray()
    }

    private fun repeatBits(words: IntArray): Int =
        ((words[0] ushr 30) and 0x3) or
            (((words[1] ushr 30) and 0x3) shl 2) or
            (((words[2] ushr 30) and 0x3) shl 4)

    private fun decodeNibbles(
        table: Array<DecodeEntry>,
        count: Int,
        bits: BitReader,
        initialState: Int,
        advanceAfterLast: Boolean,
    ): DecodedNibbles {
        require(count > 0) { "empty encoded SMOL vector" }
        var state = initialState
        val output = ByteArray(count)
        repeat(count) { index ->
            require(state in table.indices) { "invalid SMOL tANS state $state" }
            val entry = table[state]
            output[index] = entry.symbol.toByte()
            if (index != count - 1 || advanceAfterLast) {
                val next = (entry.y shl entry.k) + bits.read(entry.k) - TABLE_SIZE
                require(next in table.indices) { "invalid SMOL tANS transition $next" }
                state = next
            }
        }
        return DecodedNibbles(output, state)
    }

    private fun ByteArray.packNibblesToBytes(): ByteArray {
        require(size % 2 == 0)
        return ByteArray(size / 2) { index ->
            ((this[index * 2].toInt() and 0xF) or ((this[index * 2 + 1].toInt() and 0xF) shl 4)).toByte()
        }
    }

    private fun ByteArray.packNibblesToShorts(): IntArray {
        require(size % 4 == 0)
        return IntArray(size / 4) { index ->
            var value = 0
            repeat(4) { nibble -> value = value or ((this[index * 4 + nibble].toInt() and 0xF) shl (nibble * 4)) }
            value
        }
    }

    private fun ByteArray.deltaDecoded(): ByteArray {
        var previous = 0
        return ByteArray(size) { index ->
            ((this[index].toInt() + previous) and 0xF).also { previous = it }.toByte()
        }
    }

    private fun expand(lo: ByteArray, symbols: IntArray, outputSize: Int): ByteArray {
        val expectedSymbols = outputSize / 2
        val output = ArrayList<Int>(expectedSymbols)
        var loIndex = 0
        var symbolIndex = 0
        fun readValue(): Int {
            require(loIndex < lo.size) { "truncated SMOL instruction" }
            val first = lo[loIndex++].toInt() and 0xFF
            var value = first and 0x7F
            if (first and 0x80 != 0) {
                require(loIndex < lo.size) { "truncated SMOL extended instruction" }
                value += (lo[loIndex++].toInt() and 0xFF) shl 7
            }
            return value
        }
        while (loIndex < lo.size) {
            val length = readValue()
            val offset = readValue()
            if (length == 0) {
                require(offset > 0 && symbolIndex <= symbols.size - offset) { "invalid SMOL literal run" }
                repeat(offset) { output += symbols[symbolIndex++] }
            } else {
                require(symbolIndex < symbols.size) { "missing SMOL copy seed" }
                output += symbols[symbolIndex++]
                require(offset in 1..output.size) { "invalid SMOL copy offset $offset" }
                repeat(length) { output += output[output.size - offset] }
            }
            require(output.size <= expectedSymbols) { "SMOL output exceeds declared size" }
        }
        require(output.size == expectedSymbols) { "SMOL output ${output.size * 2} != declared $outputSize" }
        require(symbolIndex == symbols.size) { "unused SMOL symbols" }
        return ByteArray(outputSize) { byteIndex ->
            val value = output[byteIndex / 2]
            if (byteIndex and 1 == 0) value.toByte() else (value ushr 8).toByte()
        }
    }

    private fun ByteArray.u16le(offset: Int): Int {
        require(offset in 0 until size - 1)
        return (this[offset].toInt() and 0xFF) or ((this[offset + 1].toInt() and 0xFF) shl 8)
    }

    private fun ByteArray.u32le(offset: Int): Int {
        require(offset in 0 until size - 3)
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private data class DecodeEntry(val symbol: Int, val y: Int, val k: Int)
    private data class DecodedNibbles(val values: ByteArray, val finalState: Int)

    private class BitReader(private val input: ByteArray, offset: Int, byteCount: Int) {
        private val start = offset
        private val bitCount = Math.multiplyExact(byteCount, 8)
        private var index = 0

        fun read(count: Int): Int {
            require(index <= bitCount - count) { "truncated SMOL tANS bits" }
            var value = 0
            repeat(count) { bit ->
                val absolute = start + index / 8
                value = value or (((input[absolute].toInt() ushr (index % 8)) and 1) shl bit)
                index++
            }
            return value
        }
    }

    private const val HEADER_BYTES = 8
    private const val FREQUENCY_BYTES = 12
    private const val TABLE_SIZE = 64
    private const val MODE_MASK = 0xF
    private const val IMAGE_SIZE_OFFSET = 4
    private const val IMAGE_SIZE_MASK = 0x3FFF
    private const val SMOL_IMAGE_SIZE_MULTIPLIER = 4
    private const val SYMBOL_SIZE_OFFSET = 18
    private const val SYMBOL_SIZE_MASK = 0x3FFF
    private const val INITIAL_STATE_MASK = 0x3F
    private const val BITSTREAM_SIZE_OFFSET = 6
    private const val BITSTREAM_SIZE_MASK = 0x1FFF
    private const val LO_SIZE_OFFSET = 19
    private const val LO_SIZE_MASK = 0x1FFF
    private const val BASE_ONLY = 1
    private const val ENCODE_SYMS = 2
    private const val ENCODE_DELTA_SYMS = 3
    private const val ENCODE_LO = 4
    private const val ENCODE_BOTH = 5
    private const val ENCODE_BOTH_DELTA_SYMS = 6
    private const val IS_TILEMAP = 8
}
