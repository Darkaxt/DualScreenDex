package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real-ROM ABI and payload characterization before the ordinary Gen III typed-evolution cutover. */
class EvolutionLiveRomTest {
    @Test fun alteredEmeraldHasExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_ALTERED_EMERALD_ROM",
        "8fe93d8245c96ea5aa49d61df2c74ee99a439b15cde7c0afa4f0b5a87aac34f0",
        expectedRows = 426,
        expectedEdges = 269,
        expectedPayloadSha256 = "82a555fa52aa3934e9287f3e7be304e2b0f3f36b94a7dc34b4f1640cc42e3b82",
    )

    @Test fun cloudWhiteTwoHasExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_CLOUD_WHITE_2_ROM",
        "6d9075a559c289eee4f336c925b46fdba55f34c6baa0576626d4a3b71513d879",
        expectedRows = 943,
        expectedEdges = 468,
        expectedPayloadSha256 = "eece3e72b13c2d81436ad9d090ec76617c4dafa3bca48286b50f1bbb4d3882ff",
    )

    @Test fun cloverHasExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_CLOVER_ROM",
        "42f99abd548934d77999ac3eb563fb9bc70a34701d37a262b21b882a43a8bdd9",
        expectedRows = 387,
        expectedEdges = 178,
        expectedPayloadSha256 = "5999acc0715cb6b2669d27d9c07922d7be6548ecfdb016544c4f517791a000a1",
    )

    @Test fun altairHasExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_ALTAIR_ROM",
        "333e4fcbf2b8039ad1848a84d0f6826e790109ed150243f6cf7c9934b22ae380",
        expectedRows = 385,
        expectedEdges = 106,
        expectedPayloadSha256 = "4a569a1e4f4caac818c1f699718b5fcbe94b65874329801ad173cf4f64d737a4",
    )

    @Test fun blazingEmeraldHasExactTypedPayloadParity() = assertCodecParity(
        "DUALDEX_BLAZING_EMERALD_ROM",
        "2ff14043118132e9816fac3f20b3a85011b3e8ac5361a0499264dbebe4f096dc",
        expectedRows = 410,
        expectedEdges = 213,
        expectedPayloadSha256 = "61d98d062c60693ca5959176ef7805158ee8e6561813d105d62947e19ce00735",
    )

    private fun assertCodecParity(
        environmentVariable: String,
        expectedSha256: String,
        expectedRows: Int,
        expectedEdges: Int,
        expectedPayloadSha256: String,
    ) {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedSha256, rom.sha256)
        val parsed = CatalogParser.parse(rom)
        val layout = requireNotNull(parsed.layout)
        val catalog = requireNotNull(parsed.catalog)
        assertEquals(3, layout.generation)
        assertNull("expansion evolutions remain on their characterized path", layout.pokeemeraldExpansion)
        val selected = requireNotNull(layout.tables.evolutions)
        val elementSize = requireNotNull(selected.elementSize)
        assertTrue("ordinary Gen III evolution element size must be typed", elementSize in setOf(6, 8))
        assertTrue("selected row stride must be divisible by its element ABI", selected.recordSize % elementSize == 0)
        val typed = requireNotNull(layout.resolvedDatasets.evolutions)
        assertEquals(selected.offset.toLong(), typed.table.offset)
        assertEquals(selected.count.toLong(), typed.table.count)
        assertEquals(selected.recordSize / elementSize, typed.table.slotsPerSpecies)
        assertEquals(elementSize, typed.table.recordSize)
        assertTrue(typed.rows[0] is EvolutionRowOutcome.StructuralEmpty)
        val typedCatalog = typed.catalogEvolutions()
        val navigable = catalog.navigableSpecies().associate { species ->
            species.id to species.evolutionEdges.value.orEmpty()
        }
        val expected = typedCatalog.filterKeys(navigable::containsKey)

        assertEquals(expected, navigable)
        assertEquals(expectedRows, navigable.size)
        assertEquals(expectedEdges, navigable.values.sumOf(List<*>::size))
        val semanticHash = evolutionSha256(navigable)
        assertEquals(expectedPayloadSha256, semanticHash)
        println(
            "EVOLUTION_CODEC_PARITY $environmentVariable rows=${navigable.size} edges=$expectedEdges " +
                "sha256=$semanticHash root=0x${selected.offset.toString(16)} " +
                "slots=${typed.table.slotsPerSpecies} record=$elementSize",
        )
    }

    private fun evolutionSha256(values: Map<Int, List<EvolutionEdge>>): String {
        val bytes = values.toSortedMap().entries.joinToString("\u001e") { (id, edges) ->
            "$id\u001f" + edges.joinToString("\u001d") { edge ->
                "${edge.targetSpeciesId},${edge.methodId},${edge.parameter},${edge.conditionValue}," +
                    edge.raw.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xFF) }
            }
        }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }
}
