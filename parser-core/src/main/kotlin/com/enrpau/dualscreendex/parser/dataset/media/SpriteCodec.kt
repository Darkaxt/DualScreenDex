package com.enrpau.dualscreendex.parser.dataset.media

import com.enrpau.dualscreendex.parser.analysis.ExtentCheck
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.sprite.GbaSmolDecoder
import com.enrpau.dualscreendex.parser.sprite.TileRenderer
import kotlin.math.sqrt

fun interface SpriteTableDecoder {
    fun decode(session: RomAnalysisSession, layout: SpriteTableLayout): SpriteTableOutcome
}

/** Sole byte interpreter for sprite validation and later materialization. */
class SpriteCodec(
    private val decodeLimits: SpriteDecodeLimits = SpriteDecodeLimits(),
) : SpriteTableDecoder {
    override fun decode(session: RomAnalysisSession, layout: SpriteTableLayout): SpriteTableOutcome {
        when (
            val extent = session.limits.checkTableExtent(
                layout.tableOffset,
                layout.count,
                layout.recordStride.toLong(),
                session.rom.size.toLong(),
            )
        ) {
            is ExtentCheck.Invalid -> return SpriteTableOutcome.Rejected(layout, extent.reason)
            is ExtentCheck.BudgetExceeded -> return SpriteTableOutcome.BudgetExceeded(
                layout,
                SpriteBudgetKind.TABLE_EXTENT,
                extent.observedBytes,
                extent.limitBytes,
                "sprite table extent exceeds deterministic budget",
            )
            is ExtentCheck.Valid -> Unit
        }
        if (layout.count > Int.MAX_VALUE.toLong()) {
            return SpriteTableOutcome.Rejected(layout, "sprite row count cannot be represented by indexed outcomes")
        }
        if (layout.count > decodeLimits.maxRowsPerTable.toLong()) {
            return SpriteTableOutcome.BudgetExceeded(
                layout,
                SpriteBudgetKind.TABLE_ROWS,
                layout.count,
                decodeLimits.maxRowsPerTable.toLong(),
                "sprite table row/outcome preallocation budget exceeded",
            )
        }
        if (layout is GbaSpriteTableLayout) {
            layout.palette?.let { palette ->
                when (
                    val extent = session.limits.checkTableExtent(
                        palette.tableOffset,
                        layout.count,
                        palette.recordStride.toLong(),
                        session.rom.size.toLong(),
                    )
                ) {
                    is ExtentCheck.Invalid -> return SpriteTableOutcome.Rejected(
                        layout,
                        "palette table: ${extent.reason}",
                    )
                    is ExtentCheck.BudgetExceeded -> return SpriteTableOutcome.BudgetExceeded(
                        layout,
                        SpriteBudgetKind.TABLE_EXTENT,
                        extent.observedBytes,
                        extent.limitBytes,
                        "palette table extent exceeds deterministic budget",
                    )
                    is ExtentCheck.Valid -> Unit
                }
            }
        }

        return try {
            val meter = DecodeMeter(decodeLimits)
            meter.retainOutcomeList(layout.count.toInt())
            SpriteTableOutcome.Decoded(
                layout,
                List(layout.count.toInt()) { rowIndex ->
                    decodeRow(session, layout, rowIndex, meter)
                },
            )
        } catch (exhausted: SpriteBudgetException) {
            SpriteTableOutcome.BudgetExceeded(
                layout,
                exhausted.kind,
                exhausted.observed,
                exhausted.limit,
                exhausted.message ?: "sprite decode budget exceeded",
            )
        }
    }

    private fun decodeRow(
        session: RomAnalysisSession,
        layout: SpriteTableLayout,
        rowIndex: Int,
        meter: DecodeMeter,
    ): SpriteRowOutcome {
        return try {
            // Charge the fixed record slice/structural scan before any row-specific early return.
            meter.work(layout.recordStride)
            when (layout) {
                is Gen1SpriteTableLayout -> decodeGen1Row(session.rom, layout, rowIndex, meter)
                is Gen2SpriteTableLayout -> decodeGen2Row(session, layout, rowIndex, meter)
                is GbaSpriteTableLayout -> decodeGbaRow(session.rom, layout, rowIndex, meter)
            }
        } catch (exhausted: SpriteBudgetException) {
            throw exhausted
        } catch (failure: IllegalArgumentException) {
            SpriteRowOutcome.Malformed(
                rowIndex,
                listOf(failure.message ?: "malformed sprite row"),
            )
        }
    }

    private fun decodeGbaRow(
        rom: RomImage,
        layout: GbaSpriteTableLayout,
        rowIndex: Int,
        meter: DecodeMeter,
    ): SpriteRowOutcome {
        val entry = checkedIndexedOffset(layout.tableOffset, rowIndex, layout.recordStride)
        val row = rom.slice(entry, layout.recordStride)
        if (row.all { it == 0.toByte() } || row.all { it == 0xFF.toByte() }) {
            return SpriteRowOutcome.StructuralEmpty(rowIndex)
        }
        val pointerField = Math.addExact(entry, layout.graphicsPointerOffset)
        val rawPointer = rom.u32le(pointerField)
        if (rawPointer == 0L || rawPointer == 0xFFFFFFFFL) return SpriteRowOutcome.StructuralEmpty(rowIndex)
        val graphicsOffset = rom.gbaPointer(pointerField)
            ?: throw IllegalArgumentException("invalid GBA graphics pointer")
        if (graphicsOffset.toLong() in layout.placeholderGraphicsOffsets) {
            return SpriteRowOutcome.StandardPlaceholder(rowIndex, graphicsOffset.toLong())
        }
        val frameSize = layout.fixedFrameSize ?: rom.u16le(Math.addExact(entry, layout.frameSizeOffset))
        require(frameSize > 0) { "GBA sprite frame size is empty" }
        val decoded = when (layout.graphicsMode) {
            GbaGraphicsMode.RAW_4BPP -> decodeRaw(rom, graphicsOffset, frameSize, meter)
            GbaGraphicsMode.LZ77_4BPP -> decodeGbaLz77(rom, graphicsOffset, meter)
            GbaGraphicsMode.SMOL_4BPP -> decodeGbaSmol(rom, graphicsOffset, meter)
        }
        require(decoded.size >= frameSize && decoded.size % frameSize == 0) {
            "decoded GBA sheet ${decoded.size} does not contain whole $frameSize-byte frames"
        }
        val graphics = decoded.copyOf(frameSize)
        val indexed = renderGbaFrame(graphics, meter)
        val palette = layout.palette?.let { decodeGbaPalette(rom, it, rowIndex, meter) } ?: shortArrayOf()
        meter.retain(
            Math.addExact(
                Math.addExact(graphics.size, indexed.indices.size),
                Math.multiplyExact(palette.size, 2),
            ),
        )
        return SpriteRowOutcome.Decoded(
            rowIndex,
            DecodedSpriteFrame(indexed.width, indexed.height, graphics, indexed.indices, palette),
        )
    }

    private fun decodeGbaPalette(
        rom: RomImage,
        layout: GbaPaletteLayout,
        rowIndex: Int,
        meter: DecodeMeter,
    ): ShortArray {
        val entry = checkedIndexedOffset(layout.tableOffset, rowIndex, layout.recordStride)
        if (layout.requireRowTag) {
            val tag = rom.u16le(Math.addExact(entry, layout.rowTagOffset))
            require(tag == rowIndex) { "palette row tag $tag does not match sprite row $rowIndex" }
        }
        val pointerField = Math.addExact(entry, layout.pointerOffset)
        val offset = rom.gbaPointer(pointerField) ?: throw IllegalArgumentException("invalid GBA palette pointer")
        val decoded = when (layout.mode) {
            GbaPaletteMode.RAW_BGR555 -> decodeRaw(rom, offset, PALETTE_BYTES, meter)
            GbaPaletteMode.LZ77_BGR555 -> decodeGbaLz77(rom, offset, meter)
            GbaPaletteMode.SMOL_BGR555 -> decodeGbaSmol(rom, offset, meter)
        }
        require(decoded.size == PALETTE_BYTES) { "GBA palette output ${decoded.size} is not $PALETTE_BYTES bytes" }
        meter.work(PALETTE_COLORS)
        return ShortArray(PALETTE_COLORS) { color ->
            ((decoded[color * 2].toInt() and 0xFF) or
                ((decoded[color * 2 + 1].toInt() and 0xFF) shl 8)).toShort()
        }
    }

    private fun decodeGen2Row(
        session: RomAnalysisSession,
        layout: Gen2SpriteTableLayout,
        rowIndex: Int,
        meter: DecodeMeter,
    ): SpriteRowOutcome {
        val rom = session.rom
        val mainEntry = checkedIndexedOffset(layout.tableOffset, rowIndex, layout.recordStride)
        val row = rom.slice(mainEntry, layout.recordStride)
        if (row.all { it == 0.toByte() }) return SpriteRowOutcome.StructuralEmpty(rowIndex)
        val unownSentinel = row.size >= Gen2SpriteTableLayout.POINTER_ROW_BYTES &&
            row.take(Gen2SpriteTableLayout.POINTER_ROW_BYTES).all { it == 0xFF.toByte() }
        if (unownSentinel) {
            if (rowIndex != Gen2SpriteTableLayout.UNOWN_SPECIES_ROW) {
                return SpriteRowOutcome.StructuralEmpty(rowIndex)
            }
            val proof = layout.unownIndirectTable
                ?: throw IllegalArgumentException("Gen 2 Unown FFx6 row lacks an independently proven indirect table")
            require(proof.isBoundTo(session, layout)) {
                "Gen 2 Unown indirect-table evidence is not bound to this ROM and main sprite layout"
            }
            val root = proof.indirectTableOffset
            checkSpan(
                rom,
                root,
                Math.multiplyExact(
                    Gen2SpriteTableLayout.UNOWN_FORM_COUNT.toLong(),
                    layout.recordStride.toLong(),
                ),
            )
            var first: ByteArray? = null
            repeat(Gen2SpriteTableLayout.UNOWN_FORM_COUNT) { form ->
                val entry = checkedIndexedOffset(root, form, layout.recordStride)
                val pair = decodeGen2PointerPair(rom, layout, entry, meter)
                if (form == 0) first = pair.first
            }
            return gen2Frame(rowIndex, requireNotNull(first), layout.dimensionsByRow[rowIndex], meter)
        }
        if (row.all { it == 0xFF.toByte() }) return SpriteRowOutcome.StructuralEmpty(rowIndex)
        val pair = decodeGen2PointerPair(rom, layout, mainEntry, meter)
        return gen2Frame(rowIndex, pair.first, layout.dimensionsByRow[rowIndex], meter)
    }

    private fun decodeGen2PointerPair(
        rom: RomImage,
        layout: Gen2SpriteTableLayout,
        entry: Int,
        meter: DecodeMeter,
    ): Pair<ByteArray, ByteArray> {
        val front = gen2Pointer(rom, layout, entry, layout.frontBankOffset, layout.frontPointerOffset)
            ?: throw IllegalArgumentException("invalid Gen 2 front-sprite pointer")
        val back = gen2Pointer(rom, layout, entry, layout.backBankOffset, layout.backPointerOffset)
            ?: throw IllegalArgumentException("invalid Gen 2 back-sprite pointer")
        return decodeLz3(rom, front, meter) to decodeLz3(rom, back, meter)
    }

    private fun gen2Pointer(
        rom: RomImage,
        layout: Gen2SpriteTableLayout,
        entry: Int,
        bankOffset: Int,
        pointerOffset: Int,
    ): Int? {
        val storedBank = rom.u8(Math.addExact(entry, bankOffset))
        val bank = layout.bankRemap[storedBank] ?: Math.addExact(storedBank, layout.bankAdjustment)
        return rom.gbBankAddress(bank, rom.u16le(Math.addExact(entry, pointerOffset)))
    }

    private fun gen2Frame(
        rowIndex: Int,
        decoded: ByteArray,
        declaredDimensions: Int?,
        meter: DecodeMeter,
    ): SpriteRowOutcome.Decoded {
        val dimensions = declaredDimensions ?: squareTileWidth(decoded.size, GEN2_BYTES_PER_TILE)
        val frameBytes = Math.multiplyExact(Math.multiplyExact(dimensions, dimensions), GEN2_BYTES_PER_TILE)
        require(decoded.size >= frameBytes) { "truncated Gen 2 front sprite" }
        val graphics = decoded.copyOf(frameBytes)
        val pixelCount = Math.multiplyExact(Math.multiplyExact(dimensions, 8), Math.multiplyExact(dimensions, 8))
        meter.output(pixelCount)
        meter.work(pixelCount)
        val indexed = TileRenderer.gameBoy2Bpp(graphics, dimensions, dimensions)
        meter.retain(Math.addExact(graphics.size, indexed.indices.size))
        return SpriteRowOutcome.Decoded(
            rowIndex,
            DecodedSpriteFrame(indexed.width, indexed.height, graphics, indexed.indices),
        )
    }

    private fun decodeGen1Row(
        rom: RomImage,
        layout: Gen1SpriteTableLayout,
        rowIndex: Int,
        meter: DecodeMeter,
    ): SpriteRowOutcome {
        val entry = checkedIndexedOffset(layout.tableOffset, rowIndex, layout.recordStride)
        val row = rom.slice(entry, layout.recordStride)
        if (row.all { it == 0.toByte() } || row.all { it == 0xFF.toByte() }) {
            return SpriteRowOutcome.StructuralEmpty(rowIndex)
        }
        val expected = rom.u8(Math.addExact(entry, layout.dimensionsOffset))
        val width = expected ushr 4
        val height = expected and 0x0F
        require(width in 1..15 && height == width) { "invalid Gen 1 sprite dimensions" }
        val frontAddress = rom.u16le(Math.addExact(entry, layout.frontPointerOffset))
        val backAddress = rom.u16le(Math.addExact(entry, layout.backPointerOffset))
        require(backAddress in 0x4000..0x7FFF) { "invalid Gen 1 back-sprite pointer" }
        val valid = mutableListOf<Pair<Gen1SpriteSource, Gen1Decoded>>()
        var mappedCandidates = 0
        layout.candidateBanks.forEach { bank ->
            // Invalid/out-of-ROM banks are attempts too; they may not bypass the shared work ledger.
            meter.work(GEN1_CANDIDATE_BANK_ATTEMPT_WORK)
            val offset = rom.gbBankAddress(bank, frontAddress) ?: return@forEach
            mappedCandidates++
            try {
                val decoded = decodeGen1Bitplane(rom, offset, meter)
                if (decoded.width == width && decoded.height == height) {
                    valid += Gen1SpriteSource(bank, offset) to decoded
                }
            } catch (exhausted: SpriteBudgetException) {
                throw exhausted
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
        require(mappedCandidates > 0) { "invalid Gen 1 front-sprite pointer" }
        if (valid.isEmpty()) {
            throw IllegalArgumentException("no candidate Gen 1 picture bank contains the declared stream")
        }
        if (valid.size > 1) {
            return SpriteRowOutcome.AmbiguousSources(rowIndex, valid.map { it.first })
        }
        val decoded = valid.single().second
        meter.retain(Math.addExact(decoded.graphics.size, decoded.indexedPixels.size))
        return SpriteRowOutcome.Decoded(
            rowIndex,
            DecodedSpriteFrame(
                decoded.width * 8,
                decoded.height * 8,
                decoded.graphics,
                decoded.indexedPixels,
            ),
        )
    }

    private fun decodeRaw(rom: RomImage, offset: Int, size: Int, meter: DecodeMeter): ByteArray {
        require(size > 0)
        checkSpan(rom, offset.toLong(), size.toLong())
        meter.input(size)
        meter.output(size)
        meter.work(size)
        return rom.slice(offset, size)
    }

    private fun decodeGbaLz77(rom: RomImage, offset: Int, meter: DecodeMeter): ByteArray {
        require(checkSpan(rom, offset.toLong(), 4L).toInt() == offset)
        var cursor = offset
        fun read(): Int {
            meter.input(1)
            return rom.u8(cursor++)
        }
        require(read() == 0x10) { "invalid GBA LZ77 header for explicit LZ77 mode" }
        val declared = read() or (read() shl 8) or (read() shl 16)
        require(declared > 0) { "GBA LZ77 output is empty" }
        meter.output(declared)
        val output = ByteArray(declared)
        var written = 0
        while (written < declared) {
            val flags = read()
            for (bit in 7 downTo 0) {
                if (written == declared) break
                if (flags and (1 shl bit) == 0) {
                    output[written++] = read().toByte()
                    meter.work(1)
                } else {
                    val first = read()
                    val second = read()
                    val length = (first ushr 4) + 3
                    val distance = ((first and 0x0F) shl 8 or second) + 1
                    require(distance <= written) { "invalid GBA LZ77 back-reference" }
                    repeat(minOf(length, declared - written)) {
                        output[written] = output[written - distance]
                        written++
                        meter.work(1)
                    }
                }
            }
        }
        return output
    }

    private fun decodeGbaSmol(rom: RomImage, offset: Int, meter: DecodeMeter): ByteArray {
        checkSpan(rom, offset.toLong(), 8L)
        val first = rom.u32le(offset)
        val second = rom.u32le(offset + 4)
        val mode = (first and 0xFL).toInt()
        require(mode in 1..6) { "invalid SMOL header for explicit SMOL mode" }
        val outputSize = Math.multiplyExact(((first ushr 4) and 0x3FFFL).toInt(), 4)
        require(outputSize > 0) { "SMOL output is empty" }
        val symbolCount = ((first ushr 18) and 0x3FFFL).toInt()
        val bitstreamWords = ((second ushr 6) and 0x1FFFL).toInt()
        val loCount = ((second ushr 19) and 0x1FFFL).toInt()
        val loEncoded = mode == 4 || mode == 5 || mode == 6
        val symbolsEncoded = mode == 2 || mode == 3 || mode == 5 || mode == 6
        var encoded = 8L
        if (loEncoded) encoded = Math.addExact(encoded, 12L)
        if (symbolsEncoded) encoded = Math.addExact(encoded, 12L)
        encoded = Math.addExact(encoded, Math.multiplyExact(bitstreamWords.toLong(), 4L))
        if (!symbolsEncoded) encoded = Math.addExact(encoded, Math.multiplyExact(symbolCount.toLong(), 2L))
        if (!loEncoded) encoded = Math.addExact(encoded, loCount.toLong())
        encoded = Math.addExact(encoded, 3L) and -4L
        require(encoded <= Int.MAX_VALUE.toLong()) { "SMOL encoded length cannot be indexed" }
        checkSpan(rom, offset.toLong(), encoded)
        meter.input(encoded.toInt())
        meter.output(outputSize)
        meter.work(Math.addExact(encoded.toInt(), outputSize))
        val decoded = GbaSmolDecoder.decode(rom.slice(offset, encoded.toInt()))
        require(decoded.size == outputSize)
        return decoded
    }

    private fun decodeLz3(rom: RomImage, offset: Int, meter: DecodeMeter): ByteArray {
        val output = ArrayList<Byte>()
        var cursor = offset
        val bankEnd = minOf(rom.size, Math.multiplyExact(offset / GB_BANK_SIZE + 1, GB_BANK_SIZE))
        fun read(): Int {
            require(cursor < bankEnd) { "LZ3 stream crosses its source bank" }
            meter.input(1)
            return rom.u8(cursor++)
        }
        while (true) {
            val control = read()
            if (control == 0xFF) return output.toByteArray()
            var command = control ushr 5
            val length = if (command == 7) {
                command = (control ushr 2) and 0x07
                require(command != 7) { "invalid long LZ3 command" }
                (((control and 0x03) shl 8) or read()) + 1
            } else {
                (control and 0x1F) + 1
            }
            meter.output(length)
            when (command) {
                0 -> repeat(length) { output += read().toByte(); meter.work(1) }
                1 -> {
                    val value = read().toByte()
                    repeat(length) { output += value; meter.work(1) }
                }
                2 -> {
                    val first = read().toByte()
                    val second = read().toByte()
                    repeat(length) { output += if (it and 1 == 0) first else second; meter.work(1) }
                }
                3 -> repeat(length) { output += 0; meter.work(1) }
                in 4..6 -> {
                    val first = read()
                    val source = if (first and 0x80 != 0) {
                        output.size - ((first and 0x7F) + 1)
                    } else {
                        ((first and 0x7F) shl 8) or read()
                    }
                    repeat(length) { index ->
                        val position = if (command == 6) source - index else source + index
                        require(position in output.indices) { "invalid LZ3 copy source" }
                        val value = output[position]
                        output += if (command == 5) reverseBits(value) else value
                        meter.work(1)
                    }
                }
                else -> throw IllegalArgumentException("unsupported LZ3 command $command")
            }
        }
    }

    private fun decodeGen1Bitplane(rom: RomImage, offset: Int, meter: DecodeMeter): Gen1Decoded {
        val bankEnd = minOf(rom.size, Math.multiplyExact(offset / GB_BANK_SIZE + 1, GB_BANK_SIZE))
        val reader = MeteredBitReader(rom, offset, bankEnd, meter)
        val width = reader.readBits(4)
        val height = reader.readBits(4)
        require(width > 0 && height > 0)
        val graphicsOutput = Math.multiplyExact(Math.multiplyExact(width, height), 16)
        val pixelOutput = Math.multiplyExact(Math.multiplyExact(width, 8), Math.multiplyExact(height, 8))
        meter.ensureOutputCapacity(Math.addExact(graphicsOutput, pixelOutput))
        meter.output(Math.addExact(graphicsOutput, pixelOutput))
        val firstBuffer = reader.readBit()
        val buffers = arrayOf(ByteArray(width * height * 8), ByteArray(width * height * 8))
        buffers[firstBuffer] = decodeGen1Plane(reader, width, height)
        val mode = if (reader.readBit() == 0) 0 else 1 + reader.readBit()
        val secondBuffer = 1 - firstBuffer
        buffers[secondBuffer] = decodeGen1Plane(reader, width, height)
        when (mode) {
            0 -> {
                differentialDecode(buffers[0], width, height, meter)
                differentialDecode(buffers[1], width, height, meter)
            }
            1 -> {
                differentialDecode(buffers[firstBuffer], width, height, meter)
                xorInto(buffers[firstBuffer], buffers[secondBuffer], meter)
            }
            2 -> {
                differentialDecode(buffers[secondBuffer], width, height, meter)
                differentialDecode(buffers[firstBuffer], width, height, meter)
                xorInto(buffers[firstBuffer], buffers[secondBuffer], meter)
            }
        }
        val pixelWidth = width * 8
        val pixelHeight = height * 8
        val pixels = ByteArray(pixelWidth * pixelHeight)
        repeat(pixelHeight) { y ->
            repeat(pixelWidth) { x ->
                val bufferOffset = (x / 8) * pixelHeight + y
                val bit = 7 - x % 8
                val msb = buffers[0][bufferOffset].toInt() ushr bit and 1
                val lsb = buffers[1][bufferOffset].toInt() ushr bit and 1
                pixels[y * pixelWidth + x] = (lsb or (msb shl 1)).toByte()
                meter.work(1)
            }
        }
        return Gen1Decoded(width, height, buffers[0] + buffers[1], pixels)
    }

    private fun decodeGen1Plane(reader: MeteredBitReader, width: Int, height: Int): ByteArray {
        val rows = height * 8
        val groups = width * height * 32
        val values = IntArray(groups)
        var position = 0
        var zeroMode = reader.readBit() == 0
        while (position < groups) {
            if (zeroMode) {
                var encodedWidth = 0
                while (reader.readBit() != 0) {
                    encodedWidth++
                    require(encodedWidth < 30) { "Gen 1 zero-run width is excessive" }
                }
                val run = (1 shl (encodedWidth + 1)) - 1 + reader.readBits(encodedWidth + 1)
                require(position + run <= groups) { "Gen 1 zero run exceeds sprite plane" }
                position += run
            } else {
                while (position < groups) {
                    val value = reader.readBits(2)
                    if (value == 0) break
                    values[position++] = value
                }
            }
            zeroMode = !zeroMode
        }
        val output = ByteArray(width * rows)
        position = 0
        repeat(width) { tileX ->
            for (pairOffset in 3 downTo 0) {
                repeat(rows) { y ->
                    output[tileX * rows + y] =
                        (output[tileX * rows + y].toInt() or (values[position++] shl (pairOffset * 2))).toByte()
                }
            }
        }
        return output
    }

    private fun differentialDecode(buffer: ByteArray, width: Int, height: Int, meter: DecodeMeter) {
        val rows = height * 8
        repeat(rows) { y ->
            var previous = 0
            repeat(width) { tileX ->
                val offset = tileX * rows + y
                val encoded = buffer[offset].toInt() and 0xFF
                var decoded = 0
                for (bit in 7 downTo 0) {
                    val value = previous xor (encoded ushr bit and 1)
                    decoded = decoded or (value shl bit)
                    previous = value
                    meter.work(1)
                }
                buffer[offset] = decoded.toByte()
            }
        }
    }

    private fun xorInto(source: ByteArray, destination: ByteArray, meter: DecodeMeter) {
        source.indices.forEach { index ->
            destination[index] = (destination[index].toInt() xor source[index].toInt()).toByte()
            meter.work(1)
        }
    }

    private fun renderGbaFrame(graphics: ByteArray, meter: DecodeMeter): com.enrpau.dualscreendex.parser.sprite.IndexedSprite {
        val tileWidth = squareTileWidth(graphics.size, GBA_BYTES_PER_TILE)
        val pixels = Math.multiplyExact(Math.multiplyExact(tileWidth, 8), Math.multiplyExact(tileWidth, 8))
        meter.output(pixels)
        meter.work(pixels)
        return TileRenderer.gba4Bpp(graphics, tileWidth, tileWidth)
    }

    private fun squareTileWidth(byteCount: Int, bytesPerTile: Int): Int {
        require(byteCount > 0 && byteCount % bytesPerTile == 0) { "sprite frame is not whole tiles" }
        val tiles = byteCount / bytesPerTile
        val width = sqrt(tiles.toDouble()).toInt()
        require(width * width == tiles) { "sprite frame is not a square tile grid" }
        return width
    }

    private fun checkedIndexedOffset(base: Long, index: Int, stride: Int): Int {
        val offset = Math.addExact(base, Math.multiplyExact(index.toLong(), stride.toLong()))
        require(offset in 0..Int.MAX_VALUE.toLong()) { "sprite row offset cannot be indexed" }
        return offset.toInt()
    }

    private fun checkSpan(rom: RomImage, offset: Long, length: Long): Long {
        require(offset >= 0 && length >= 0)
        val end = Math.addExact(offset, length)
        require(end <= rom.size.toLong()) { "sprite data span exceeds ROM bounds" }
        require(offset <= Int.MAX_VALUE && length <= Int.MAX_VALUE && end <= Int.MAX_VALUE)
        return offset
    }

    private fun reverseBits(value: Byte): Byte = Integer.reverse(value.toInt() and 0xFF).ushr(24).toByte()

    private data class Gen1Decoded(
        val width: Int,
        val height: Int,
        val graphics: ByteArray,
        val indexedPixels: ByteArray,
    )

    private class MeteredBitReader(
        private val rom: RomImage,
        private val start: Int,
        private val endExclusive: Int,
        private val meter: DecodeMeter,
    ) {
        private var bitIndex = 0
        private var lastChargedByte = -1

        fun readBit(): Int {
            val relativeByte = bitIndex / 8
            val absoluteByte = Math.addExact(start, relativeByte)
            require(absoluteByte < endExclusive) { "Gen 1 stream crosses its source bank" }
            if (relativeByte != lastChargedByte) {
                meter.input(1)
                lastChargedByte = relativeByte
            }
            meter.work(1)
            val value = rom.u8(absoluteByte) ushr (7 - bitIndex % 8) and 1
            bitIndex++
            return value
        }

        fun readBits(count: Int): Int {
            var value = 0
            repeat(count) { value = value shl 1 or readBit() }
            return value
        }
    }

    private class DecodeMeter(private val limits: SpriteDecodeLimits) {
        private var compressedInput = 0
        private var decodedOutput = 0
        private var decodeWork = 0
        private var retainedOutput = 0
        fun input(amount: Int) {
            compressedInput = Math.addExact(compressedInput, amount)
            if (compressedInput > limits.maxCompressedBytesPerTable) {
                throw SpriteBudgetException(
                    SpriteBudgetKind.COMPRESSED_INPUT,
                    compressedInput.toLong(),
                    limits.maxCompressedBytesPerTable.toLong(),
                    "sprite table compressed-input budget exceeded",
                )
            }
        }

        fun ensureOutputCapacity(amount: Int) {
            reserveOutput(amount)
        }

        fun reserveOutput(amount: Int) {
            val observed = Math.addExact(decodedOutput, amount)
            if (observed > limits.maxDecodedBytesPerTable) {
                throw SpriteBudgetException(
                    SpriteBudgetKind.DECODE_OUTPUT,
                    observed.toLong(),
                    limits.maxDecodedBytesPerTable.toLong(),
                    "sprite table decoded-output budget exceeded",
                )
            }
        }

        fun commitReservedOutput(amount: Int) {
            decodedOutput = Math.addExact(decodedOutput, amount)
        }

        fun output(amount: Int) {
            reserveOutput(amount)
            commitReservedOutput(amount)
        }

        fun work(amount: Int) {
            decodeWork = Math.addExact(decodeWork, amount)
            if (decodeWork > limits.maxDecodeWorkPerTable) {
                throw SpriteBudgetException(
                    SpriteBudgetKind.DECODE_WORK,
                    decodeWork.toLong(),
                    limits.maxDecodeWorkPerTable.toLong(),
                    "sprite table decode-work budget exceeded",
                )
            }
        }

        fun retain(amount: Int) {
            retain(amount.toLong())
        }

        fun retainOutcomeList(rowCount: Int) {
            val rowMetadata = Math.multiplyExact(rowCount.toLong(), RETAINED_BYTES_PER_ROW_OUTCOME)
            retain(Math.addExact(RETAINED_OUTCOME_LIST_BYTES, rowMetadata))
        }

        private fun retain(amount: Long) {
            val observed = Math.addExact(retainedOutput.toLong(), amount)
            if (observed > limits.maxRetainedBytesPerTable.toLong()) {
                throw SpriteBudgetException(
                    SpriteBudgetKind.RETAINED_OUTPUT,
                    observed,
                    limits.maxRetainedBytesPerTable.toLong(),
                    "sprite table retained-output budget exceeded",
                )
            }
            retainedOutput = observed.toInt()
        }
    }

    private class SpriteBudgetException(
        val kind: SpriteBudgetKind,
        val observed: Long,
        val limit: Long,
        message: String,
    ) : IllegalArgumentException(message)

    private companion object {
        const val PALETTE_BYTES = 32
        const val PALETTE_COLORS = 16
        const val GBA_BYTES_PER_TILE = 32
        const val GEN2_BYTES_PER_TILE = 16
        const val GB_BANK_SIZE = 0x4000
        const val GEN1_CANDIDATE_BANK_ATTEMPT_WORK = 1
        const val RETAINED_OUTCOME_LIST_BYTES = 16L
        const val RETAINED_BYTES_PER_ROW_OUTCOME = 32L
    }
}
