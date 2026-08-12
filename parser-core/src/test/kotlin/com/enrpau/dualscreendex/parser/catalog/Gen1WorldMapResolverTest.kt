package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.parse.Gen1WorldMapResolution
import com.enrpau.dualscreendex.parser.parse.Gen1WorldMapResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen1WorldMapResolverTest {
    @Test
    fun resolvesRomDerivedTownMapAndRawMapAnchors() {
        val outcome = Gen1WorldMapResolver.resolve(session(fixture()), setOf(0, 0x25, 0x30))

        assertTrue("unexpected outcome type: ${outcome::class.simpleName}", outcome is Gen1WorldMapResolution.Resolved)
        val resolved = outcome as Gen1WorldMapResolution.Resolved
        val region = resolved.catalog.regions.single()
        assertEquals(160, region.pixelWidth)
        assertEquals(144, region.pixelHeight)
        assertEquals(setOf(0, 0x25, 0x30), region.locations.flatMapTo(linkedSetOf()) { it.baseAreaIds })
        assertEquals(listOf(WorldMapCell(2, 11, 1, 1)), region.locations.first().geometry)
        assertTrue(resolved.catalog.assets.getValue(region.imageAssetKey).argb.toSet().size > 1)
    }

    @Test
    fun ignoresUnreferencedRleDecoy() {
        val bytes = fixture()
        bytes.fill(0, LOADER, LOADER + 96)

        assertTrue(
            Gen1WorldMapResolver.resolve(session(bytes), setOf(0, 0x25, 0x30))
                is Gen1WorldMapResolution.Unavailable,
        )
    }

    @Test
    fun rejectsTwoFullyReferencedTownMapChains() {
        val bytes = fixture()
        writeChain(bytes, bank = 2, variant = true)

        val outcome = Gen1WorldMapResolver.resolve(session(bytes), setOf(0, 0x25, 0x30))

        assertTrue("unexpected outcome type: ${outcome::class.simpleName}", outcome is Gen1WorldMapResolution.Ambiguous)
    }

    private fun session(bytes: ByteArray) = RomAnalysisSession(
        RomImage(bytes),
        RomHeader(Platform.GB, ""),
    )

    private fun fixture(): ByteArray = ByteArray(0x20000).also { writeChain(it, bank = 1) }

    private fun writeChain(rom: ByteArray, bank: Int, variant: Boolean = false) {
        val bankStart = bank * 0x4000
        val loader = bankStart + 0x100
        val rle = bankStart + 0x300
        val lookup = bankStart + 0x500
        val external = bankStart + 0x600
        val internal = bankStart + 0x700
        val names = bankStart + 0x900
        val gfx = (bank + 4) * 0x4000 + 0x200

        rom[loader] = 0x21
        putU16(rom, loader + 1, gbAddress(gfx))
        rom[loader + 3] = 0x11
        putU16(rom, loader + 4, 0x9000)
        rom[loader + 6] = 0x01
        putU16(rom, loader + 7, 0x100)
        rom[loader + 9] = 0x3e
        rom[loader + 10] = (gfx / 0x4000).toByte()
        rom[loader + 11] = 0xcd.toByte()
        putU16(rom, loader + 12, 0x4567)
        rom[loader + 20] = 0x11
        putU16(rom, loader + 21, gbAddress(rle))
        byteArrayOf(
            0x1a, 0xb7.toByte(), 0x28, 0x12, 0x47, 0xe6.toByte(), 0x0f, 0x4f,
            0x78, 0xcb.toByte(), 0x37, 0xe6.toByte(), 0x0f, 0xc6.toByte(), 0x60,
            0x22, 0x0d, 0x20, 0xfc.toByte(), 0x13, 0x18, 0xe9.toByte(),
        ).copyInto(rom, loader + 23)

        repeat(16) { tile ->
            repeat(8) { row ->
                val offset = gfx + tile * 16 + row * 2
                rom[offset] = if ((tile + row + if (variant) 1 else 0) % 3 == 0) 0xff.toByte() else 0
                rom[offset + 1] = if ((tile + row) % 4 == 0) 0xff.toByte() else 0
            }
        }
        var cursor = rle
        repeat(24) { run ->
            rom[cursor++] = (((run % 16) shl 4) or 15).toByte()
        }
        rom[cursor] = 0

        byteArrayOf(
            0xfe.toByte(), 0x25, 0x38, 0x10,
            0x01, 0x04, 0x00,
            0x21,
        ).copyInto(rom, lookup)
        putU16(rom, lookup + 8, gbAddress(internal))
        byteArrayOf(0xbe.toByte(), 0x38, 0x04, 0x09, 0x18, 0xfa.toByte(), 0x23, 0x18, 0x0a, 0x21)
            .copyInto(rom, lookup + 10)
        putU16(rom, lookup + 20, gbAddress(external))
        byteArrayOf(0x4f, 0x06, 0x00, 0x09, 0x09, 0x09).copyInto(rom, lookup + 22)

        repeat(0x25) { id ->
            writeEntry(
                rom,
                external + id * 3,
                x = if (id == 0) 2 else id % 16,
                y = if (id == 0) 11 else (id / 3) % 16,
                name = names,
                nameIndex = id,
            )
        }
        writeInternalEntry(rom, internal, limit = 0x30, x = 4, y = 5, name = names, nameIndex = 0x25)
        writeInternalEntry(rom, internal + 4, limit = 0x40, x = 6, y = 7, name = names, nameIndex = 0x30)
        rom[internal + 8] = 0xff.toByte()
        repeat(0x31) { id -> encodeGb("AREA $id").copyInto(rom, names + id * 12) }
    }

    private fun writeEntry(rom: ByteArray, offset: Int, x: Int, y: Int, name: Int, nameIndex: Int) {
        rom[offset] = ((y shl 4) or x).toByte()
        putU16(rom, offset + 1, gbAddress(name + nameIndex * 12))
    }

    private fun writeInternalEntry(
        rom: ByteArray,
        offset: Int,
        limit: Int,
        x: Int,
        y: Int,
        name: Int,
        nameIndex: Int,
    ) {
        rom[offset] = limit.toByte()
        rom[offset + 1] = ((y shl 4) or x).toByte()
        putU16(rom, offset + 2, gbAddress(name + nameIndex * 12))
    }

    private fun encodeGb(text: String): ByteArray = ByteArray(text.length + 1).also { encoded ->
        text.forEachIndexed { index, character ->
            encoded[index] = when (character) {
                ' ' -> 0x7f
                in 'A'..'Z' -> 0x80 + character.code - 'A'.code
                in '0'..'9' -> 0xf6 + character.code - '0'.code
                else -> error("unsupported fixture character")
            }.toByte()
        }
        encoded[text.length] = 0x50
    }

    private fun gbAddress(offset: Int): Int = 0x4000 + offset % 0x4000

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    companion object {
        private const val LOADER = 0x4100
    }
}
