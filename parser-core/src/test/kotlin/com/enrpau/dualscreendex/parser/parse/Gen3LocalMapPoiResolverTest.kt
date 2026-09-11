package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapNameDisposition
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.text.JapanesePokemonTextCodecs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Gen3LocalMapPoiResolverTest {
    @Test
    fun task415GenderObligationSurvivesMissingCodecAndText() {
        val bytes = ByteArray(0x800)
        putPointer(bytes, MAP_HEADER + 4, EVENTS)
        bytes[EVENTS + 1] = 2
        bytes[EVENTS + 3] = 2
        putPointer(bytes, EVENTS + 8, WARPS)
        putPointer(bytes, EVENTS + 0x10, BACKGROUNDS)
        putU16(bytes, WARPS, 2)
        putU16(bytes, WARPS + 2, 3)
        bytes[WARPS + 6] = 5
        putU16(bytes, WARPS + 8, 8)
        putU16(bytes, WARPS + 10, 8)
        bytes[WARPS + 14] = 5
        putU16(bytes, BACKGROUNDS, 2)
        putU16(bytes, BACKGROUNDS + 2, 3)
        putPointer(bytes, BACKGROUNDS + 8, 0x500)
        bytes[0x500] = 0x69
        bytes[0x501] = 0xa0.toByte()
        putU16(bytes, BACKGROUNDS + 12, 6)
        putU16(bytes, BACKGROUNDS + 14, 6)
        bytes[BACKGROUNDS + 17] = 8
        for (gendered in listOf(true, false)) {
            if (!gendered) {
                bytes[0x500] = 0x0f
                bytes[0x501] = 0
                putPointer(bytes, 0x502, 0x600)
                bytes[0x506] = 0x09
                bytes[0x507] = 2
            }
            val result = Gen3LocalMapPoiResolver.resolve(RomImage(bytes), mapOf(1 to MAP_HEADER),
                listOf(localMap()), EngineFamily.EMERALD, null)
            val points = result.pois.associateBy { it.key.substringAfter("local/1/") }
            assertEquals(setOf("warp/1", "bg/0", "bg/1"), points.keys)
            assertEquals(LocalMapPoiTextObligation.DESTINATION_NAME, points.getValue("warp/1").textObligation)
            assertEquals(if (gendered) LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT else LocalMapPoiTextObligation.DIRECT_TEXT,
                points.getValue("bg/0").textObligation)
            assertEquals(5, points.getValue("bg/0").destinationBaseAreaId)
            assertNull(points.getValue("bg/0").displayName)
            assertEquals(LocalMapPoiTextObligation.NO_TEXT, points.getValue("bg/1").textObligation)
        }
    }

    @Test
    fun dynamicWarpsRequireContextualText() {
        val bytes = ByteArray(0x800)
        putPointer(bytes, MAP_HEADER + 4, EVENTS)
        bytes[EVENTS + 1] = 1
        putPointer(bytes, EVENTS + 8, WARPS)
        putU16(bytes, WARPS, 2)
        putU16(bytes, WARPS + 2, 3)
        bytes[WARPS + 6] = 0x7f
        bytes[WARPS + 7] = 0x7f

        val warp = Gen3LocalMapPoiResolver.resolve(
            RomImage(bytes),
            mapOf(1 to MAP_HEADER),
            listOf(localMap()),
            EngineFamily.EMERALD,
            null,
        ).pois.single()

        assertEquals(0x7f7f, warp.destinationBaseAreaId)
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, warp.textObligation)
    }

    @Test
    fun warpsToDynamicMapSectionsRequireContextualText() {
        val bytes = ByteArray(0x800)
        putPointer(bytes, MAP_HEADER + 4, EVENTS)
        bytes[EVENTS + 1] = 1
        putPointer(bytes, EVENTS + 8, WARPS)
        putU16(bytes, WARPS, 2)
        putU16(bytes, WARPS + 2, 3)
        bytes[WARPS + 6] = 2

        val warp = Gen3LocalMapPoiResolver.resolve(
            RomImage(bytes),
            mapOf(1 to MAP_HEADER),
            listOf(
                localMap(),
                localMap(2, LocalMapNameDisposition.CONTEXT_DEPENDENT),
            ),
            EngineFamily.EMERALD,
            null,
        ).pois.single()

        assertEquals(2, warp.destinationBaseAreaId)
        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, warp.textObligation)
    }

    @Test
    fun acceptsBoundedNonPlayerPlaceholderInJapaneseSignHeadline() {
        val bytes = singleSignFixture(SCRIPT)
        writeSimpleSign(bytes, SCRIPT, TEXT)
        byteArrayOf(0x01, 0xfd.toByte(), 0x08, 0x02, 0xff.toByte()).copyInto(bytes, TEXT)

        val poi = resolveSingleSign(bytes)

        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, poi.textObligation)
        assertEquals("あ い", poi.displayName)
    }

    @Test
    fun decodesGenderedJapaneseSignBranchAfterBoundedSoundPrelude() {
        val bytes = singleSignFixture(SCRIPT)
        byteArrayOf(
            0x69, 0xa0.toByte(),
            0x21, 0x0d, 0x80.toByte(), 0x00, 0x00,
            0x06, 0x01, 0x00, 0x06, 0x00, 0x08,
            0x21, 0x0d, 0x80.toByte(), 0x01, 0x00,
            0x06, 0x01, 0x20, 0x06, 0x00, 0x08,
            0x02,
        ).copyInto(bytes, SCRIPT)
        writeSimpleSign(bytes, 0x600, TEXT)
        byteArrayOf(0x01, 0x02, 0xff.toByte()).copyInto(bytes, TEXT)
        byteArrayOf(
            0x16, 0x04, 0x80.toByte(), 0x01, 0x00,
            0x25, 0xd6.toByte(), 0x00,
            0x2f, 0x04, 0x00,
            0x0f, 0x00, 0x10, 0x07, 0x00, 0x08,
            0x09, 0x04,
            0x25, 0xf9.toByte(), 0x00,
            0x27, 0x6b, 0x02,
        ).copyInto(bytes, 0x620)
        byteArrayOf(0x03, 0x04, 0xff.toByte()).copyInto(bytes, 0x710)

        val poi = resolveSingleSign(bytes)

        assertEquals(LocalMapPoiTextObligation.GENDERED_DIRECT_TEXT, poi.textObligation)
        assertEquals(mapOf(0 to "あい", 1 to "うえ"), poi.displayNamesByTrainerGender)
    }

    @Test
    fun preservesStructuralSignsWarpsAndItemsWithoutTextAuthority() {
        val bytes = ByteArray(0x800)
        putPointer(bytes, MAP_HEADER + 4, EVENTS)
        bytes[EVENTS + 1] = 1
        bytes[EVENTS + 3] = 2
        putPointer(bytes, EVENTS + 8, WARPS)
        putPointer(bytes, EVENTS + 0x10, BACKGROUNDS)

        putU16(bytes, WARPS, 2)
        putU16(bytes, WARPS + 2, 3)
        bytes[WARPS + 6] = 5

        putU16(bytes, BACKGROUNDS, 2)
        putU16(bytes, BACKGROUNDS + 2, 3)
        bytes[BACKGROUNDS + 5] = 0

        val hidden = BACKGROUNDS + 0x0C
        putU16(bytes, hidden, 4)
        putU16(bytes, hidden + 2, 5)
        bytes[hidden + 5] = 7
        putU16(bytes, hidden + 8, 42)
        bytes[hidden + 10] = 7

        val resolution = Gen3LocalMapPoiResolver.resolve(
            rom = RomImage(bytes),
            headers = mapOf(1 to MAP_HEADER),
            maps = listOf(localMap()),
            family = EngineFamily.FIRERED_LEAFGREEN,
            codec = null,
        )

        assertEquals(emptyList<String>(), resolution.skippedReasons)
        assertEquals(2, resolution.pois.size)
        val sign = resolution.pois.single { it.key.endsWith("/bg/0") }
        assertEquals(LocalMapPoiKind.PLACE, sign.kind)
        assertEquals(2, sign.tileX)
        assertEquals(3, sign.tileY)
        assertEquals(5, sign.destinationBaseAreaId)
        assertNull(sign.displayName)
        assertEquals(emptyMap<Int, String>(), sign.displayNamesByTrainerGender)
        assertEquals(LocalMapPoiTextObligation.UNRESOLVED, sign.textObligation)
        val item = resolution.pois.single { it.key.endsWith("/bg/1") }
        assertEquals(LocalMapPoiKind.HIDDEN_ITEM, item.kind)
        assertEquals(42, item.item?.itemId)
        assertEquals(1007, item.item?.collectionFlagId)
    }

    private fun singleSignFixture(script: Int): ByteArray = ByteArray(0x800).also { bytes ->
        putPointer(bytes, MAP_HEADER + 4, EVENTS)
        bytes[EVENTS + 3] = 1
        putPointer(bytes, EVENTS + 0x10, BACKGROUNDS)
        putU16(bytes, BACKGROUNDS, 2)
        putU16(bytes, BACKGROUNDS + 2, 3)
        putPointer(bytes, BACKGROUNDS + 8, script)
    }

    private fun resolveSingleSign(bytes: ByteArray) = Gen3LocalMapPoiResolver.resolve(
        RomImage(bytes),
        mapOf(1 to MAP_HEADER),
        listOf(localMap()),
        EngineFamily.RUBY_SAPPHIRE,
        JapanesePokemonTextCodecs.gen3RubySapphire,
    ).pois.single()

    private fun writeSimpleSign(bytes: ByteArray, script: Int, text: Int) {
        bytes[script] = 0x0f
        bytes[script + 1] = 0
        putPointer(bytes, script + 2, text)
        bytes[script + 6] = 0x09
        bytes[script + 7] = 0x03
        bytes[script + 8] = 0x02
    }

    private fun localMap(
        baseAreaId: Int = 1,
        nameDisposition: LocalMapNameDisposition = LocalMapNameDisposition.STATIC_NAME_REQUIRED,
    ) = LocalMap(
        key = "local/$baseAreaId",
        displayName = null,
        baseAreaId = baseAreaId,
        pixelWidth = 160,
        pixelHeight = 160,
        gridWidth = 10,
        gridHeight = 10,
        imageAssetKey = "asset/$baseAreaId",
        nameDisposition = nameDisposition,
    )

    private fun putPointer(bytes: ByteArray, offset: Int, target: Int) {
        val value = 0x08000000L + target
        repeat(4) { index -> bytes[offset + index] = (value ushr (index * 8)).toByte() }
    }

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val MAP_HEADER = 0x100
        const val EVENTS = 0x200
        const val WARPS = 0x300
        const val BACKGROUNDS = 0x400
        const val SCRIPT = 0x500
        const val TEXT = 0x700
    }
}
