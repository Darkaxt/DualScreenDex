package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import kotlin.math.sqrt

object SpriteMaterializer {
    fun pokemon(
        rom: RomImage,
        layout: ResolvedRomLayout,
        gbaPaletteTableOffset: Int? = null,
    ): Map<Int, RgbaSprite> = when (layout.generation) {
        1 -> gen1(rom, layout)
        2 -> gen2(rom, layout)
        3 -> gen3(rom, layout, gbaPaletteTableOffset)
        else -> emptyMap()
    }

    private fun gen3(
        rom: RomImage,
        layout: ResolvedRomLayout,
        requestedPaletteTable: Int?,
    ): Map<Int, RgbaSprite> {
        val table = layout.tables.sprites ?: return emptyMap()
        val paletteTable = requestedPaletteTable
            ?: headerPaletteTable(rom)
            ?: locateGbaPaletteTable(rom, table.count, table.offset)
        return buildMap {
            repeat(table.count) { id ->
                val sprite = runCatching {
                    val entry = table.offset + id * table.recordSize
                    val pointer = rom.gbaPointer(entry) ?: error("invalid GBA sprite pointer")
                    val frameSize = rom.u16le(entry + 4)
                    val decoded = GbaRomCompression.decodeAt(rom, pointer)
                    val frame = decoded.copyOf(minOf(frameSize, decoded.size))
                    val tiles = squareTileWidth(frame.size, 32)
                    val indexed = TileRenderer.gba4Bpp(frame, tiles, tiles)
                    val palette = paletteTable?.let { readGbaPalette(rom, it, id) }
                    if (palette != null) TileRenderer.applyBgr555Palette(indexed, palette)
                    else TileRenderer.applyArgbPalette(indexed, GBA_GRAYSCALE)
                }.getOrNull()
                if (sprite != null) put(id, sprite)
            }
        }
    }

    private fun gen2(rom: RomImage, layout: ResolvedRomLayout): Map<Int, RgbaSprite> {
        val table = layout.tables.sprites ?: return emptyMap()
        return buildMap {
            repeat(table.count) { index ->
                val sprite = runCatching {
                    val entry = table.offset + index * table.recordSize
                    val bank = rom.u8(entry) + table.bankAdjustment
                    val pointer = rom.gbBankAddress(bank, rom.u16le(entry + 1)) ?: error("invalid Gen 2 sprite pointer")
                    val bankEnd = minOf(rom.size, (pointer / 0x4000 + 1) * 0x4000)
                    val decoded = Lz3Decoder.decode(rom.slice(pointer, bankEnd - pointer))
                    val tiles = squareTileWidth(decoded.size, 16)
                    TileRenderer.applyArgbPalette(TileRenderer.gameBoy2Bpp(decoded, tiles, tiles), GB_GRAYSCALE)
                }.getOrNull()
                if (sprite != null) put(index + 1, sprite)
            }
        }
    }

    private fun gen1(rom: RomImage, layout: ResolvedRomLayout): Map<Int, RgbaSprite> {
        val table = layout.tables.sprites ?: return emptyMap()
        return buildMap {
            repeat(table.count) { index ->
                val entry = table.offset + index * table.recordSize
                val expectedDimensions = rom.u8(entry + 10)
                val address = rom.u16le(entry + 11)
                val indexed = table.banks.firstNotNullOfOrNull { bank ->
                    runCatching {
                        val pointer = rom.gbBankAddress(bank, address) ?: error("invalid Gen 1 sprite pointer")
                        val bankEnd = minOf(rom.size, (pointer / 0x4000 + 1) * 0x4000)
                        Gen1SpriteDecoder.decode(rom.slice(pointer, bankEnd - pointer))
                    }.getOrNull()?.takeIf {
                        expectedDimensions == ((it.width / 8) shl 4 or (it.height / 8))
                    }
                }
                if (indexed != null) put(index + 1, TileRenderer.applyArgbPalette(indexed, GB_GRAYSCALE))
            }
        }
    }

    private fun headerPaletteTable(rom: RomImage): Int? =
        if (rom.size > 0x134) runCatching { rom.gbaPointer(0x130) }.getOrNull() else null

    private fun locateGbaPaletteTable(rom: RomImage, count: Int, spriteTableOffset: Int): Int? {
        if (count < 2) return null
        val radius = 0x400000
        val start = maxOf(0, spriteTableOffset - radius).let { it - it % 4 }
        val end = minOf(rom.size - count * 8, spriteTableOffset + radius)
        var candidate = start
        while (candidate <= end) {
            if (rom.u16le(candidate + 4) == 0 && rom.u16le(candidate + 12) == 1 &&
                validGbaPaletteEntry(rom, candidate) && validGbaPaletteEntry(rom, candidate + 8)
            ) {
                val samples = listOf(0, 1, count / 2, count - 1).distinct()
                if (samples.all { id -> rom.u16le(candidate + id * 8 + 4) == id && validGbaPaletteEntry(rom, candidate + id * 8) }) {
                    return candidate
                }
            }
            candidate += 4
        }
        return null
    }

    private fun validGbaPaletteEntry(rom: RomImage, entry: Int): Boolean {
        val pointer = runCatching { rom.gbaPointer(entry) }.getOrNull() ?: return false
        return runCatching { rom.u8(pointer) == 0x10 && rom.u24le(pointer + 1) == 32 }.getOrDefault(false)
    }

    private fun readGbaPalette(rom: RomImage, table: Int, id: Int): ShortArray? = runCatching {
        val pointer = rom.gbaPointer(table + id * 8) ?: error("invalid GBA palette pointer")
        val decoded = GbaRomCompression.decodeAt(rom, pointer)
        require(decoded.size >= 32)
        ShortArray(16) { index ->
            ((decoded[index * 2].toInt() and 0xFF) or ((decoded[index * 2 + 1].toInt() and 0xFF) shl 8)).toShort()
        }
    }.getOrNull()

    private fun squareTileWidth(byteCount: Int, bytesPerTile: Int): Int {
        require(byteCount > 0 && byteCount % bytesPerTile == 0)
        val tiles = byteCount / bytesPerTile
        val width = sqrt(tiles.toDouble()).toInt()
        require(width * width == tiles) { "sprite frame is not a square tile grid" }
        return width
    }

    private val GB_GRAYSCALE = intArrayOf(0, 0xFFC8D0C0.toInt(), 0xFF687060.toInt(), 0xFF182018.toInt())
    private val GBA_GRAYSCALE = IntArray(16) { index ->
        if (index == 0) 0 else {
            val shade = 255 - index * 12
            0xFF000000.toInt() or (shade shl 16) or (shade shl 8) or shade
        }
    }
}
