package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NatureCatalogMaterializationLiveRomTest {
    @Test
    fun `official Italian and Japanese Emerald reject unrelated menu labels as Nature names`() {
        val western = System.getenv("DUALDEX_WESTERN_CONTROLS")
        val native = System.getenv("DUALDEX_NATIVE_CONTROLS")
        assumeTrue("set both official-control roots for the two-control Nature gate", !western.isNullOrBlank() && !native.isNullOrBlank())
        val expected = listOf(
            ControlNames(
                Path.of(western).resolve("it/EMERALD"),
                "63cbff3500b657cb6966568beb0780de3655d3a0b2e5ac6e0ec33d5d01a916ad",
                listOf(
                    "ARDITA", "SCHIVA", "AUDACE", "DECISA", "BIRBONA", "SICURA", "DOCILE", "PLACIDA", "SCALTRA", "FIACCA",
                    "TIMIDA", "LESTA", "SERIA", "ALLEGRA", "INGENUA", "MODESTA", "MITE", "QUIETA", "RITROSA", "ARDENTE",
                    "CALMA", "GENTILE", "VIVACE", "CAUTA", "FURBA",
                ),
            ),
            ControlNames(
                Path.of(native).resolve("ja/EMERALD"),
                "33f5610b9186b4add09fef68895deb00f552b997b3d133b5a961e5123506343c",
                listOf(
                    "がんばりや", "さみしがり", "ゆうかん", "いじっぱり", "やんちゃ", "ずぶとい", "すなお", "のんき", "わんぱく", "のうてんき",
                    "おくびょう", "せっかち", "まじめ", "ようき", "むじゃき", "ひかえめ", "おっとり", "れいせい", "てれや", "うっかりや",
                    "おだやか", "おとなしい", "なまいき", "しんちょう", "きまぐれ",
                ),
            ),
        )

        expected.forEach { control ->
            val path = Files.list(control.directory).use { files ->
                files.filter(Files::isRegularFile).toList().single()
            }
            val rom = Files.newInputStream(path).use(RomImage::from)
            assertEquals(control.sha256, rom.sha256)
            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)

            assertEquals(
                control.names,
                requireNotNull(catalog.defaultLocalizedText()).natureNames.toSortedMap().values.map { it.value },
            )
        }
    }

    @Test
    fun `Modern Emerald materializes its ROM-native Nature catalog`() = assertMaterialized(controls[0])

    @Test
    fun `Unbound materializes its ROM-native Nature catalog`() = assertMaterialized(controls[1])

    @Test
    fun `Odyssey materializes its ROM-native Nature catalog`() = assertMaterialized(controls[2])

    private fun assertMaterialized(control: Control) {
        val path = Path.of(control.path)
        assumeTrue("missing ${control.path}", Files.isRegularFile(path))
        val rom = Files.newInputStream(path).use(RomImage::from)
        assertEquals(control.sha256, rom.sha256)

        val parsed = CatalogParser.parse(rom)
        val catalog = requireNotNull(parsed.catalog)
        val text = catalog.defaultTextProjection()
        val moveNames = parsed.layout?.tables?.moveNames
        val controls = moveNames?.let { table ->
            (1..3).map { index ->
                val codec = com.enrpau.dualscreendex.parser.text.PokemonTextCodec.gbaEnglish
                val offset = table.offset + index * (table.stride ?: table.recordSize)
                RecordMaterializers.readName(rom, table, index, codec) to
                    codec.decodeDetailed(
                        rom,
                        offset,
                        table.recordSize,
                        com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken.NONE,
                    )
            }
        }
        val evidence = catalog.capabilities.getValue(RomCapability.NATURES)
        assertEquals(
            "language=${catalog.languageManifest.status}; diagnostics=${catalog.languageManifest.diagnostics}; " +
                "moveNames=$moveNames; controls=$controls; capability=${evidence.reasons.joinToString("; ")}",
            CapabilityStatus.AVAILABLE,
            evidence.status,
        )
        assertEquals(25, catalog.naturesById.size)
        assertEquals((0 until 25).toSet(), catalog.naturesById.keys)
        assertTrue(
            "language=${catalog.languageManifest.status}; diagnostics=${catalog.languageManifest.diagnostics}; " +
                "moveNames=$moveNames; controls=$controls; " +
                "natureNames=${catalog.naturesById.keys.map(text::natureName).take(3)}",
            catalog.naturesById.keys.all { !text.natureName(it).isNullOrBlank() },
        )
        assertTrue(catalog.naturesById.values.all { it.flavorModifiers != null })
        assertTrue(catalog.naturesById.values.any { it.raisedStat != null && it.loweredStat != null })
        assertTrue(catalog.naturesById.values.all { it.positivePercent == 110 && it.negativePercent == 90 })
    }

    private data class Control(val path: String, val sha256: String)
    private data class ControlNames(val directory: Path, val sha256: String, val names: List<String>)

    private val controls = listOf(
        Control(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
        ),
        Control(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
            "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
        ),
        Control(
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
            "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
        ),
    )
}
