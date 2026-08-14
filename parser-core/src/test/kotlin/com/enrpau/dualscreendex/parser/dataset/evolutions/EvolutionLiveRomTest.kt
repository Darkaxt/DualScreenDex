package com.enrpau.dualscreendex.parser.dataset.evolutions

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.EvolutionEdge
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
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
    @Test fun incompleteFirstFiftyEvolutionTablesDecodeEverySelectedRow() {
        val controls = listOf(
            RealControl(
                "DUALDEX_CRIPPLING_ROM",
                "79882b5e276f6c0386fe7c4d5cce122c56ff969d694ffc530b1a534ab57d25cb",
                0xD959B8,
                1528, 1, 8, 1525, 652,
                "6b1edcde61b170c4196c9afc4142c167674763dd951adc57a812f2636a11f239",
            ),
            RealControl(
                "DUALDEX_CRYSTAL_ADVANCE_ROM",
                "fbbcbf32afd427afa5de45799923c414c21b77917004477f214c9f5cd87537b6",
                0x149CB54,
                760, 7, 8, 699, 273,
                "b23af1252729a75af9c0648036452e639e91f83e58f1c226b7bd139f04b1d6bf",
            ),
            RealControl(
                "DUALDEX_DARK_VIOLET_ROM",
                "6b7e6df19c974371a4f80ea5c0f1e8d68a2cfee248faf34080a48ae3f0135e21",
                0xA13330,
                412, 7, 8, 366, 151,
                "3dce97bd2242a0dab5aec7f05fc85d6b4ce2cbe8f75f83978588f2dc8107ffe1",
            ),
            RealControl(
                "DUALDEX_DARK_VIOLET_FAN_PATCH_ROM",
                "d171d29b691ced98178b4370826f0627f9c2ed6e0313d813f909ba147031c717",
                0xA13330,
                412, 7, 8, 366, 151,
                "3dce97bd2242a0dab5aec7f05fc85d6b4ce2cbe8f75f83978588f2dc8107ffe1",
            ),
            RealControl(
                "DUALDEX_DARKFIRE_ROM",
                "8c564fcd1e419d81a56eaf6734ae9eb70d0f9849d08200c1807d31d674a48d69",
                0x3F0E84,
                494, 10, 8, 493, 269,
                "f449a971b4c63977b8d35d500f47d14adcbf6afd735acffd045983ff3d73b806",
            ),
            RealControl(
                "DUALDEX_DREAMSTONE_ROM",
                "ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220",
                0x7B0200,
                1525, 1, 8, 1522, 631,
                "1aac9d884eee5025b25e7ad8c916eeec3867ba39ba9d99fb47e8973083884c9d",
            ),
        ).filter { !System.getenv(it.environmentVariable).isNullOrBlank() }
        assumeTrue("set at least one incomplete first-50 evolution ROM", controls.isNotEmpty())
        val failures = controls.mapNotNull { control ->
            runCatching { assertCompleteEvolutionTable(control) }.exceptionOrNull()?.let { error ->
                "${control.environmentVariable}: ${error.message}"
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun modernEmeraldEvolutionTableDecodesEverySelectedRow() {
        val control = RealControl(
            "DUALDEX_MODERN_EMERALD_ROM",
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
            0x8E606C,
            462, 8, 8, 428, 217,
            "b9ccecb45ce67286de3a7e57a3497372d117068625dfd1a3c278d8a5061ef038",
        )
        assertCompleteEvolutionTable(control)

        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this live-ROM regression", !configured.isNullOrBlank())
        val parsed = CatalogParser.parse(RomImage(Files.readAllBytes(Path.of(requireNotNull(configured)))))
        val typed = requireNotNull(parsed.layout?.resolvedDatasets?.evolutions)
        val bulbasaur = typed.rows[1] as EvolutionRowOutcome.Decoded
        val ivysaur = typed.rows[2] as EvolutionRowOutcome.Decoded
        assertEquals(Triple(4, 16, 2), bulbasaur.edges.single().let { Triple(it.methodId, it.parameter, it.targetSpeciesId) })
        assertEquals(Triple(4, 32, 3), ivysaur.edges.single().let { Triple(it.methodId, it.parameter, it.targetSpeciesId) })
        assertEquals(listOf(0, 0), bulbasaur.edges.single().raw.takeLast(2).map { it.toInt() and 0xff })
    }

    @Test fun classicKeepsItsSourceProvenTenSlotEvolutionStride() = assertCompleteEvolutionTable(
        RealControl(
            "DUALDEX_CLASSIC_ROM",
            "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
            0x368A88,
            429, 10, 8, 403, 190,
            "2f6437ba40dec067aa2af4c522c6d0ae90d065b7936285fd1b71905e045ebb7f",
        ),
    )

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

    private fun assertCompleteEvolutionTable(control: RealControl) {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue(
            "set ${control.environmentVariable} to run this live-ROM regression",
            !configured.isNullOrBlank(),
        )
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(control.sha256, rom.sha256)

        val first = CatalogParser.parse(rom)
        val firstLayout = requireNotNull(first.layout)
        val selected = requireNotNull(firstLayout.tables.evolutions) {
            first.analysis.capabilities.firstOrNull {
                it.capability == com.enrpau.dualscreendex.parser.model.RomCapability.EVOLUTIONS
            }.toString()
        }
        val typed = requireNotNull(firstLayout.resolvedDatasets.evolutions) {
            "${control.environmentVariable} has no typed evolution table"
        }
        assertEquals(control.tableOffset.toLong(), typed.table.offset)
        assertEquals(control.selectedRows, selected.count)
        assertEquals(selected.count, typed.rows.size)
        assertEquals(
            "${control.environmentVariable} selected ${typed.table}",
            control.slotsPerSpecies,
            typed.table.slotsPerSpecies,
        )
        assertEquals("${control.environmentVariable} selected ${typed.table}", control.recordSize, typed.table.recordSize)
        if (control.slotsPerSpecies > 1) {
            val references = requireNotNull(
                RomAnalysisSession(rom, RomHeaderReader.read(rom)).gbaReferenceIndex?.target(control.tableOffset),
            )
            assertTrue("${control.environmentVariable} table root must have compiled references", references.count > 0)
        }
        val malformed = typed.rows.filterIsInstance<EvolutionRowOutcome.Malformed>()
        assertTrue(
            "${control.environmentVariable} selected ${typed.table} with " +
                "${malformed.size}/${typed.rows.size} malformed evolution rows: " +
                malformed.take(5).joinToString { "${it.rowIndex}:${it.reasons}" },
            malformed.isEmpty(),
        )
        val firstEdges = requireNotNull(first.catalog).navigableSpecies().associate { species ->
            species.id to species.evolutionEdges.value.orEmpty()
        }
        assertTrue(
            "${control.environmentVariable} must publish at least one evolution edge",
            firstEdges.values.sumOf(List<*>::size) > 0,
        )
        assertEquals(control.navigableRows, firstEdges.size)
        assertEquals(control.edges, firstEdges.values.sumOf(List<*>::size))
        assertEquals(control.semanticSha256, evolutionSha256(firstEdges))

        val second = CatalogParser.parse(RomImage(Files.readAllBytes(path)))
        val secondEdges = requireNotNull(second.catalog).navigableSpecies().associate { species ->
            species.id to species.evolutionEdges.value.orEmpty()
        }
        assertEquals(evolutionSha256(firstEdges), evolutionSha256(secondEdges))
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

    private data class RealControl(
        val environmentVariable: String,
        val sha256: String,
        val tableOffset: Int,
        val selectedRows: Int,
        val slotsPerSpecies: Int,
        val recordSize: Int,
        val navigableRows: Int,
        val edges: Int,
        val semanticSha256: String,
    )
}
