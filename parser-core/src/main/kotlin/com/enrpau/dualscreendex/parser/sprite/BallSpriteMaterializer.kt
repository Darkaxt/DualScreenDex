package com.enrpau.dualscreendex.parser.sprite

import com.enrpau.dualscreendex.parser.catalog.CaptureBallRecord
import com.enrpau.dualscreendex.parser.catalog.CatalogField
import com.enrpau.dualscreendex.parser.io.RomImage

object BallSpriteMaterializer {
    fun captureBalls(rom: RomImage): Map<Int, CaptureBallRecord> {
        val sheets = locateSheetTable(rom) ?: return expansionCaptureBalls(rom)
        val tagBase = rom.u16le(sheets + 6)
        val palettes = locatePaletteTable(rom, tagBase) ?: return emptyMap()
        return BALL_ITEM_IDS.indices.associate { ballIndex ->
            val itemId = BALL_ITEM_IDS[ballIndex]
            val sprite = runCatching {
                val gfxPointer = rom.gbaPointer(sheets + ballIndex * 8) ?: error("invalid ball gfx pointer")
                val palettePointer = rom.gbaPointer(palettes + ballIndex * 8) ?: error("invalid ball palette pointer")
                val gfx = GbaRomCompression.decodeAt(rom, gfxPointer)
                require(gfx.size >= FIRST_FRAME_SIZE)
                val paletteBytes = paletteBytesAt(rom, palettePointer)
                require(paletteBytes.size >= 32)
                val palette = ShortArray(16) { index ->
                    ((paletteBytes[index * 2].toInt() and 0xFF) or
                        ((paletteBytes[index * 2 + 1].toInt() and 0xFF) shl 8)).toShort()
                }
                TileRenderer.applyBgr555Palette(
                    TileRenderer.gba4Bpp(gfx.copyOf(FIRST_FRAME_SIZE), 2, 2),
                    palette,
                )
            }.getOrElse { error ->
                return@associate itemId to CaptureBallRecord(
                    id = itemId,
                    name = CatalogField.notFound("ball name was not materialized"),
                    sprite = CatalogField.notFound(error.message ?: "ball sprite could not be decoded"),
                    generic = itemId == POKE_BALL_ITEM_ID,
                )
            }
            itemId to CaptureBallRecord(
                id = itemId,
                name = CatalogField.notFound("ball name was not materialized"),
                sprite = CatalogField.available(sprite),
                generic = itemId == POKE_BALL_ITEM_ID,
            )
        }
    }

    private fun expansionCaptureBalls(rom: RomImage): Map<Int, CaptureBallRecord> {
        val table = locateIntegratedBallTable(rom) ?: return emptyMap()
        return (0 until table.count).associate { ballIndex ->
            val entry = table.offset + ballIndex * EXPANSION_BALL_STRIDE
            val itemId = rom.u16le(entry + 40)
            val sprite = runCatching {
                val gfxPointer = rom.gbaPointer(entry) ?: error("invalid integrated ball gfx pointer")
                val palettePointer = rom.gbaPointer(entry + 8) ?: error("invalid integrated ball palette pointer")
                val gfx = if (rom.u8(gfxPointer) == 0x10) GbaRomCompression.decodeAt(rom, gfxPointer)
                    else rom.slice(gfxPointer, SHEET_SIZE)
                require(gfx.size >= FIRST_FRAME_SIZE)
                val paletteBytes = paletteBytesAt(rom, palettePointer)
                val palette = ShortArray(16) { index ->
                    ((paletteBytes[index * 2].toInt() and 0xFF) or
                        ((paletteBytes[index * 2 + 1].toInt() and 0xFF) shl 8)).toShort()
                }
                TileRenderer.applyBgr555Palette(
                    TileRenderer.gba4Bpp(gfx.copyOf(FIRST_FRAME_SIZE), 2, 2),
                    palette,
                )
            }.getOrNull()
            itemId to CaptureBallRecord(
                id = itemId,
                name = CatalogField.notFound("ball name was not materialized"),
                sprite = sprite?.let(CatalogField.Companion::available)
                    ?: CatalogField.notFound("integrated ball sprite could not be decoded"),
                generic = ballIndex == 1,
            )
        }
    }

    private fun locateIntegratedBallTable(rom: RomImage): IntegratedBallTable? {
        val candidates = mutableListOf<IntegratedBallTable>()
        var offset = 0
        while (offset + EXPANSION_BALL_STRIDE * 2 <= rom.size) {
            if (validIntegratedBallRecord(rom, offset) && validIntegratedBallRecord(rom, offset + EXPANSION_BALL_STRIDE)) {
                var count = 2
                while (count < 64 && offset + (count + 1) * EXPANSION_BALL_STRIDE <= rom.size &&
                    validIntegratedBallRecord(rom, offset + count * EXPANSION_BALL_STRIDE)
                ) count++
                if (count >= 12) candidates += IntegratedBallTable(offset, count)
                offset += count * EXPANSION_BALL_STRIDE
            } else {
                offset += 4
            }
        }
        return candidates.singleOrNull()
    }

    private fun validIntegratedBallRecord(rom: RomImage, offset: Int): Boolean {
        val tag = rom.u16le(offset + 6)
        return rom.u16le(offset + 4) == SHEET_SIZE &&
            rom.u16le(offset + 12) == tag && rom.u16le(offset + 16) == tag && rom.u16le(offset + 18) == tag &&
            rom.gbaPointer(offset) != null && rom.gbaPointer(offset + 8) != null &&
            rom.gbaPointer(offset + 20) != null && rom.gbaPointer(offset + 24) != null &&
            rom.gbaPointer(offset + 32) != null && rom.gbaPointer(offset + 36) != null &&
            rom.u16le(offset + 40) > 0
    }

    private fun locateSheetTable(rom: RomImage): Int? {
        val tableSize = BALL_COUNT * 8
        var offset = 0
        while (offset <= rom.size - tableSize) {
            if (rom.u16le(offset + 4) == SHEET_SIZE) {
                val tag = rom.u16le(offset + 6)
                val valid = (0 until BALL_COUNT).all { index ->
                    val entry = offset + index * 8
                    rom.u16le(entry + 4) == SHEET_SIZE && rom.u16le(entry + 6) == tag + index &&
                        compressedPointerDeclares(rom, entry, SHEET_SIZE)
                }
                if (valid) return offset
            }
            offset += 4
        }
        return null
    }

    private fun locatePaletteTable(rom: RomImage, tagBase: Int): Int? {
        val tableSize = BALL_COUNT * 8
        var offset = 0
        while (offset <= rom.size - tableSize) {
            if (rom.u16le(offset + 4) == tagBase) {
                val valid = (0 until BALL_COUNT).all { index ->
                    val entry = offset + index * 8
                    rom.u16le(entry + 4) == tagBase + index && palettePointerContainsData(rom, entry)
                }
                if (valid) return offset
            }
            offset += 4
        }
        return null
    }

    private fun compressedPointerDeclares(rom: RomImage, pointerField: Int, outputSize: Int): Boolean {
        val pointer = runCatching { rom.gbaPointer(pointerField) }.getOrNull() ?: return false
        return runCatching { rom.u8(pointer) == 0x10 && rom.u24le(pointer + 1) == outputSize }.getOrDefault(false)
    }

    private fun palettePointerContainsData(rom: RomImage, pointerField: Int): Boolean {
        val pointer = runCatching { rom.gbaPointer(pointerField) }.getOrNull() ?: return false
        if (pointer < 0 || pointer + 32 > rom.size) return false
        return rom.u8(pointer) != 0x10 || runCatching { rom.u24le(pointer + 1) == 32 }.getOrDefault(false)
    }

    private fun paletteBytesAt(rom: RomImage, pointer: Int): ByteArray =
        if (rom.u8(pointer) == 0x10 && rom.u24le(pointer + 1) == 32) {
            GbaRomCompression.decodeAt(rom, pointer)
        } else {
            rom.slice(pointer, 32)
        }

    private const val BALL_COUNT = 12
    private const val SHEET_SIZE = 384
    private const val FIRST_FRAME_SIZE = 128
    private const val POKE_BALL_ITEM_ID = 4
    private const val EXPANSION_BALL_STRIDE = 44
    private val BALL_ITEM_IDS = intArrayOf(4, 3, 5, 2, 1, 6, 7, 8, 9, 10, 11, 12)

    private data class IntegratedBallTable(val offset: Int, val count: Int)
}
