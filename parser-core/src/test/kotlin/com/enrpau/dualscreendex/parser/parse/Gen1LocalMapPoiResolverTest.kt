package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.LocalMap
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiTextObligation
import com.enrpau.dualscreendex.parser.catalog.LocalMapPoiKind
import com.enrpau.dualscreendex.parser.io.RomImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Gen1LocalMapPoiResolverTest {
    @Test
    fun task415ObligationsComeFromEventsWithoutDecodedNames() {
        val bytes = ByteArray(0x8000)
        writeHeader(bytes, HEADER_1, OBJECT_ROOT_1_ADDRESS)
        writeTextScript(bytes, 0x00)
        byteArrayOf(
            0, 2, 1, 2, 1, 2, 8, 8, 1, 2,
            1, 1, 2, 1,
            1, 1, 5, 6, 0, 0, 0x80.toByte(), 4,
        ).copyInto(bytes, OBJECT_ROOT_1)
        val result = Gen1LocalMapPoiResolver.resolve(RomImage(bytes),
            listOf(Gen1LocalMapPoiResolver.Source(1, 1, HEADER_1)), listOf(localMap(1), localMap(2)), null)
        val points = result.pois.associateBy { it.key.substringAfter("local/1/") }
        assertEquals(setOf("warp/1", "bg/0", "object/0"), points.keys)
        assertEquals(LocalMapPoiTextObligation.DESTINATION_NAME, points.getValue("warp/1").textObligation)
        assertEquals(LocalMapPoiTextObligation.DIRECT_TEXT, points.getValue("bg/0").textObligation)
        assertEquals(2, points.getValue("bg/0").destinationBaseAreaId)
        assertEquals(null, points.getValue("bg/0").displayName)
        assertEquals(LocalMapPoiTextObligation.ITEM_NAME, points.getValue("object/0").textObligation)
        assertEquals(4, points.getValue("object/0").item?.itemId)
    }

    @Test
    fun classifiesLastMapWarpAsContextual() {
        val bytes = ByteArray(0x8000)
        writeHeader(bytes, HEADER_1, OBJECT_ROOT_1_ADDRESS)
        byteArrayOf(
            0,
            1,
            1, 2, 1, 0xff.toByte(),
            0,
            0,
        ).copyInto(bytes, OBJECT_ROOT_1)

        val poi = Gen1LocalMapPoiResolver.resolve(
            RomImage(bytes),
            listOf(Gen1LocalMapPoiResolver.Source(1, 1, HEADER_1)),
            listOf(localMap(1)),
            null,
        ).pois.single()

        assertEquals(LocalMapPoiTextObligation.CONTEXTUAL_TEXT, poi.textObligation)
        assertEquals(null, poi.destinationBaseAreaId)
    }

    @Test
    fun resolvesHomeBankSignScriptPointers() {
        assertEquals(
            LocalMapPoiTextObligation.DIRECT_TEXT,
            resolveSingleBackground(0x17, 0x0100).textObligation,
        )
    }

    @Test
    fun classifiesExecutableSignScriptAsUnresolved() {
        assertEquals(
            LocalMapPoiTextObligation.UNRESOLVED,
            resolveSingleBackground(0x08).textObligation,
        )
    }

    @Test
    fun classifiesDynamicStandardBackgroundAsContextual() {
        assertEquals(
            LocalMapPoiTextObligation.CONTEXTUAL_TEXT,
            resolveSingleBackground(0xf5).textObligation,
        )
    }

    @Test
    fun isolatesMalformedMapsAndOmitsOutOfBoundsEvents() {
        val bytes = ByteArray(0x8000)
        writeHeader(bytes, HEADER_1, OBJECT_ROOT_1_ADDRESS)
        writeHeader(bytes, HEADER_2, 0x7FFF)
        byteArrayOf(
            0,
            2,
            1, 2, 1, 2,
            20, 20, 1, 2,
            0,
            0,
        ).copyInto(bytes, OBJECT_ROOT_1)

        val resolution = Gen1LocalMapPoiResolver.resolve(
            rom = RomImage(bytes),
            sources = listOf(
                Gen1LocalMapPoiResolver.Source(1, 1, HEADER_1),
                Gen1LocalMapPoiResolver.Source(2, 1, HEADER_2),
            ),
            maps = listOf(localMap(1), localMap(2)),
            codec = null,
        )

        val poi = resolution.pois.single()
        assertEquals(LocalMapPoiKind.PLACE, poi.kind)
        assertEquals(1, poi.baseAreaId)
        assertEquals(2, poi.tileX)
        assertEquals(1, poi.tileY)
        assertEquals(2, poi.destinationBaseAreaId)
        assertTrue(resolution.skippedReasons.any { it.startsWith("map 0x0002 POIs:") })
    }

    @Test
    fun retainsTypedOperandEvidenceOnlyForAcceptedVisibleItems() {
        val bytes = ByteArray(0x8000)
        writeHeader(bytes, HEADER_1, OBJECT_ROOT_1_ADDRESS)
        byteArrayOf(0, 0, 0, 3,
            1, 5, 6, 0, 0, 0x80.toByte(), 201.toByte(),
            1, 5, 6, 0, 0, 0x80.toByte(), 0,
            1, 5, 6, 0, 0, 0).copyInto(bytes, OBJECT_ROOT_1)
        val result = Gen1LocalMapPoiResolver.resolve(RomImage(bytes),
            listOf(Gen1LocalMapPoiResolver.Source(1, 1, HEADER_1)), listOf(localMap(1)), null)
        assertEquals(listOf(201), result.pois.mapNotNull { it.item?.itemId })
        val getter = result.javaClass.methods.singleOrNull { it.name == "getItemReferences" }
        assertTrue("accepted structural items must retain source-bound operand evidence", getter != null)
        val references = getter!!.invoke(result) as List<*>
        assertEquals(1, references.size)
        val reference = references.single()!!
        assertEquals(OBJECT_ROOT_1 + 10, reference.javaClass.getMethod("getOperandOffset").invoke(reference))
        assertEquals(201, reference.javaClass.getMethod("getItemId").invoke(reference))
    }

    @Test fun exactJapaneseRedBlueReferenceEvidence() = nativeReferences("RED_BLUE",
        "3f0dc460ca8d06be1c9ac96307c939c0ea7baa366b40c2f1f4ad63242b6c4816")

    @Test fun exactJapaneseYellowReferenceEvidence() = nativeReferences("YELLOW",
        "1349408f328f633b33e059e654edabd19810530df9c883eda03a85d5bb10161a")

    private fun nativeReferences(family: String, sha: String) {
        val directory = java.io.File(requireNotNull(System.getenv("DUALDEX_NATIVE_CONTROLS")), "ja/$family")
        val file = requireNotNull(directory.listFiles()).single { it.isFile }
        val rom = RomImage(file.readBytes())
        assertEquals(sha, rom.sha256)
        lateinit var session: com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
        val analysis = ParserOrchestrator.analyze(rom) { image, header, profile ->
            com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession(image, header, profile).also { session = it }
        }
        assertEquals(com.enrpau.dualscreendex.parser.model.SelectionStatus.SELECTED, analysis.status)
        val layout = requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout)
        val catalog = com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer.materialize(rom, analysis, layout,
            resolveLocalMaps = { selected, ids -> ParserOrchestrator.resolveLocalMaps(session, selected, analysis.selectedFamily, ids) })
        val refs = session.gen1ItemReferences
        val items = catalog.localMaps.pois.filter { it.item != null }.associateBy { it.key }
        assertEquals(items.keys, refs.map { it.poiKey }.toSet())
        assertEquals(items.size, refs.size)
        assertTrue(refs.any { it.kind == com.enrpau.dualscreendex.parser.analysis.Gen1ItemReference.Kind.HIDDEN_EVENT })
        refs.forEach { ref ->
            assertEquals(items.getValue(ref.poiKey).item!!.itemId, ref.itemId)
            assertEquals(ref.itemId, rom.u8(ref.operandOffset))
            assertTrue(ref.itemId != 0)
            if (ref.kind == com.enrpau.dualscreendex.parser.analysis.Gen1ItemReference.Kind.VISIBLE_OBJECT) {
                assertEquals(0x80, rom.u8(ref.operandOffset - 1) and 0xC0)
                assertTrue(ref.mapBankTable != null && ref.mapPointerTable != null && ref.mapHeader != null)
            } else {
                val record = ref.operandOffset - 2
                assertEquals(ref.hiddenHandler, rom.gbBankAddress(rom.u8(record + 3), rom.u16le(record + 4)))
                assertTrue(ref.coordinateRoot != null && ref.coordinateIndex != null)
            }
        }
        val output = java.io.File(requireNotNull(System.getenv("DUALDEX_TEST_TEMP_ROOT")), "$family-references.tsv")
        output.parentFile.mkdirs()
        output.writeText("sha256\t$sha\ncodec\t${layout.languageManifest.projections.single().codecId}\n" +
            "poiKey\titemId\toperandOffset\tkind\tbaseAreaId\tsourceBank\trecordRoot\tmapHeader\tobjectPointerField\tmapBankTable\tmapPointerTable\thiddenHandler\tcoordinateRoot\tcoordinateIndex\n" +
            refs.joinToString("\n", postfix = "\n") { ref -> listOf(ref.poiKey, ref.itemId, ref.operandOffset, ref.kind,
                ref.baseAreaId, ref.sourceBank, ref.recordRoot, ref.mapHeader, ref.objectPointerField, ref.mapBankTable,
                ref.mapPointerTable, ref.hiddenHandler, ref.coordinateRoot, ref.coordinateIndex).joinToString("\t") { it?.toString().orEmpty() } })
        println("GEN1_REFERENCE_EVIDENCE $family records=${refs.size} ids=${refs.map { it.itemId }.toSet().size} file=$output")
    }

    private fun resolveSingleBackground(
        command: Int,
        scriptAddress: Int = TEXT_SCRIPT_ADDRESS,
    ): com.enrpau.dualscreendex.parser.catalog.LocalMapPoi {
        val bytes = ByteArray(0x8000)
        writeHeader(bytes, HEADER_1, OBJECT_ROOT_1_ADDRESS)
        writeTextScript(bytes, command, scriptAddress)
        byteArrayOf(
            0,
            0,
            1,
            1, 2, 1,
            0,
        ).copyInto(bytes, OBJECT_ROOT_1)
        return Gen1LocalMapPoiResolver.resolve(
            RomImage(bytes),
            listOf(Gen1LocalMapPoiResolver.Source(1, 1, HEADER_1)),
            listOf(localMap(1)),
            null,
        ).pois.single()
    }

    private fun writeHeader(bytes: ByteArray, header: Int, objectAddress: Int) {
        putU16(bytes, header + 5, TEXT_POINTER_TABLE_ADDRESS)
        bytes[header + 9] = 0
        putU16(bytes, header + 10, objectAddress)
    }

    private fun writeTextScript(
        bytes: ByteArray,
        command: Int,
        scriptAddress: Int = TEXT_SCRIPT_ADDRESS,
    ) {
        putU16(bytes, TEXT_POINTER_TABLE, scriptAddress)
        val scriptOffset = if (scriptAddress < 0x4000) scriptAddress else TEXT_SCRIPT
        bytes[scriptOffset] = command.toByte()
    }

    private fun localMap(baseAreaId: Int) = LocalMap(
        key = "local/$baseAreaId",
        displayName = null,
        baseAreaId = baseAreaId,
        pixelWidth = 160,
        pixelHeight = 160,
        gridWidth = 10,
        gridHeight = 10,
        imageAssetKey = "asset/$baseAreaId",
    )

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = value.toByte()
        bytes[offset + 1] = (value ushr 8).toByte()
    }

    private companion object {
        const val HEADER_1 = 0x4000
        const val HEADER_2 = 0x4020
        const val OBJECT_ROOT_1 = 0x4100
        const val OBJECT_ROOT_1_ADDRESS = 0x4100
        const val TEXT_POINTER_TABLE = 0x4200
        const val TEXT_POINTER_TABLE_ADDRESS = 0x4200
        const val TEXT_SCRIPT = 0x4300
        const val TEXT_SCRIPT_ADDRESS = 0x4300
    }
}
