package com.enrpau.dualscreendex.parser.dataset.types

import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.catalog.CatalogMaterializer
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.ParseResult
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.ProfileTables
import com.enrpau.dualscreendex.parser.model.ResolvedDatasetLayouts
import com.enrpau.dualscreendex.parser.model.ResolvedRomLayout
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import com.enrpau.dualscreendex.parser.model.TableLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeChartCatalogProjectionTest {
    @Test
    fun genThreeCatalogConsumesThePropagatedTypedChartInsteadOfDecodingLegacyBytesAgain() {
        val bytes = ByteArray(0x200)
        byteArrayOf(1, 2, 20, 0xFF.toByte(), 0xFF.toByte(), 0).copyInto(bytes, 0x80)
        val typed = ResolvedTypeChartLayout(
            TypeChartTableLayout(0x40, TypeChartAbi.LEGACY_TRIPLETS),
            listOf(TypeChartRow(0, TypeChartMatchup(4, 5, 50), 5)),
        )
        val rom = RomImage(bytes)
        val layout = ResolvedRomLayout(
            family = EngineFamily.EMERALD,
            generation = 3,
            platform = Platform.GBA,
            speciesCount = 0,
            moveCount = 0,
            tables = ProfileTables(typeChart = TableLayout(0x80, 1, 3, variableLength = true)),
            resolvedDatasets = ResolvedDatasetLayouts(typeChart = typed),
        )
        val analysis = ParseResult(
            header = RomHeader(Platform.GBA, "TEST", "TEST"),
            sha256 = rom.sha256,
            crc32 = rom.crc32,
            size = rom.size,
            status = SelectionStatus.SELECTED,
            selectedFamily = EngineFamily.EMERALD,
            selectedProfile = null,
            runnerUpMargin = 20,
            probes = emptyList(),
            capabilities = emptyList(),
        )

        val chart = CatalogMaterializer.materialize(rom, analysis, layout).typeChart

        assertEquals(listOf(com.enrpau.dualscreendex.parser.catalog.TypeMatchup(4, 5, 50)), chart)
    }

    @Test
    fun denseProjectionSuppressesNeutralCellsAndMatchesLegacyCatalogMaterialization() {
        val typeCount = 18
        val bytes = ByteArray(typeCount * typeCount * 4)
        putU32Q412Matrix(bytes, 0, typeCount)
        val decoded = TypeChartCodec().decode(
            typeChartSession(bytes),
            TypeChartTableLayout(0, TypeChartAbi.DENSE_U32_Q412, typeCount),
        ) as TypeChartTableOutcome.Decoded
        val resolved = ResolvedTypeChartLayout(decoded.layout, decoded.rows)
        val legacy = RecordMaterializers.typeChart(
            RomImage(bytes),
            ResolvedRomLayout(
                family = EngineFamily.EMERALD,
                generation = 3,
                platform = Platform.GBA,
                speciesCount = null,
                moveCount = null,
                tables = ProfileTables(
                    typeChart = TableLayout(
                        offset = 0,
                        count = typeCount * typeCount,
                        recordSize = typeCount * 4,
                        elementSize = 4,
                    ),
                ),
            ),
        )

        val projected = resolved.catalogMatchups()

        assertEquals(legacy, projected)
        assertTrue(resolved.rows.any { it.encodedMultiplier == 4096L })
        assertTrue(projected.none { matchup ->
            resolved.rows.any { row ->
                row.matchup.attackingTypeId == matchup.attackingTypeId &&
                    row.matchup.defendingTypeId == matchup.defendingTypeId &&
                    row.encodedMultiplier == 4096L
            }
        })
    }
}
