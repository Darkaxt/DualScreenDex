package com.enrpau.dualscreendex.parser.dataset.types

import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.RecordMaterializers
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Real-ROM authority for the typed Gen III type-chart materialization cutover. */
class TypeChartLiveRomTest {
    @Test fun altairTypedChartExactlyMatchesThePreviouslySelectedBytes() = assertParity(
        "DUALDEX_ALTAIR_ROM",
        "333e4fcbf2b8039ad1848a84d0f6826e790109ed150243f6cf7c9934b22ae380",
    )

    @Test fun blazingEmeraldTypedChartExactlyMatchesThePreviouslySelectedBytes() = assertParity(
        "DUALDEX_BLAZING_EMERALD_ROM",
        "2ff14043118132e9816fac3f20b3a85011b3e8ac5361a0499264dbebe4f096dc",
    )

    @Test fun deltaEmeraldTypedChartExactlyMatchesThePreviouslySelectedBytes() = assertParity(
        "DUALDEX_DELTA_EMERALD_ROM",
        "7f4aa1aa68b1df783c3a44b38984640227a5eec22debffbf18db3713de2616bc",
    )

    @Test fun arcoirisTypedChartExactlyMatchesThePreviouslySelectedBytes() = assertParity(
        "DUALDEX_ARCOIRIS_ROM",
        "fe428c3a45747c9d1466506b5f6d9245e2faf7337660664b6ba3ee28a86ca4ab",
    )

    @Test fun cloudWhiteTwoTypedChartExactlyMatchesThePreviouslySelectedBytes() = assertParity(
        "DUALDEX_CLOUD_WHITE_2_ROM",
        "6d9075a559c289eee4f336c925b46fdba55f34c6baa0576626d4a3b71513d879",
    )

    private fun assertParity(environmentVariable: String, expectedSha256: String) {
        val configured = System.getenv(environmentVariable)
        assumeTrue("set $environmentVariable to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(expectedSha256, rom.sha256)

        val parsed = CatalogParser.parse(rom)
        val layout = requireNotNull(parsed.layout)
        val catalog = requireNotNull(parsed.catalog)
        val typed = layout.resolvedDatasets.typeChart
        assertNotNull("selected Gen III chart must carry typed row evidence", typed)
        val previous = RecordMaterializers.typeChart(rom, layout)

        assertEquals(previous, requireNotNull(typed).catalogMatchups())
        assertEquals(previous, catalog.typeChart)
        println(
            "TYPE_CHART_PARITY $environmentVariable count=${previous.size} " +
                "sha256=${chartSha256(previous)} root=0x${typed.table.offset.toString(16)} abi=${typed.table.abi}",
        )
    }

    private fun chartSha256(values: List<com.enrpau.dualscreendex.parser.catalog.TypeMatchup>): String {
        val bytes = values.joinToString(";") { value ->
            "${value.attackingTypeId},${value.defendingTypeId},${value.multiplierPercent}"
        }.toByteArray()
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }
}
