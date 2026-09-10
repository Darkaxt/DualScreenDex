package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen2DescriptionConsumerLiveRomTest {
    @Test
    fun `official Western Gen II descriptions follow the compiled pointer and metadata consumers`() {
        listOf(
            Control(
                "DUALDEX_OFFICIAL_GS_EN",
                "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
                "The seed on its back is filled with nutrients.",
            ),
            Control(
                "DUALDEX_OFFICIAL_CRYSTAL_EN",
                "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2",
                "While it is young, it uses the nutrients that are",
            ),
            Control(
                "DUALDEX_OFFICIAL_GS_DE",
                "542f275f8632ef5265e5cde80a8ba1f0ec11ce714ef9d773088a92680bd17d73",
                "Der Samen auf dem Rücken enthält Nährstoffe.",
            ),
            Control(
                "DUALDEX_OFFICIAL_CRYSTAL_DE",
                "22c0dfec9ce004dceebe280d71fa2579fa376064f70284bbf46322e163aa1160",
                "Da es noch klein ist, frisst es die Samen auf seinem",
            ),
            Control(
                "DUALDEX_OFFICIAL_GS_ES",
                "7b78e33a348a0729e38ee0fd778cf49b3e07c35d2175ebc74d8f9be41f41455b",
                "La semilla de su lomo está llena de nutrientes.",
            ),
            Control(
                "DUALDEX_OFFICIAL_CRYSTAL_ES",
                "4aacb5b3ac7a741d99f507e7c31393c015dea26a3ce8876021c50e7c761c1141",
                "Cuando es joven, crece con los nutrientes que",
            ),
            Control(
                "DUALDEX_OFFICIAL_GS_FR",
                "6103cadf2ae505f4b489a8a414c8db27a2307d797ddf8a3a848659b591dd4023",
                "La graine sur son dos est gorgée de nutriments.",
            ),
            Control(
                "DUALDEX_OFFICIAL_CRYSTAL_FR",
                "178b0da870a3cc58414940412eb6473f515b8941cb83d81a1a42d05ca82749e7",
                "Jeune, il absorbe les nutriments gardés dans",
            ),
            Control(
                "DUALDEX_OFFICIAL_GS_IT",
                "367e606f82b9e2e16d0cc8011c5ba2df1862da574e57ffd66e786a82af5b6e22",
                "Il seme che ha sul dorso è ricco di sostanze",
            ),
            Control(
                "DUALDEX_OFFICIAL_CRYSTAL_IT",
                "2592aef20701fa1d5df5cf3343ffb4293092c911177868fa4a94698c27f7be89",
                "Da piccolo usa le sostanze nutritive dei",
            ),
        ).forEach { control ->
            val configured = System.getenv(control.environmentVariable)
            assumeTrue(
                "set ${control.environmentVariable} to run this live-ROM regression",
                !configured.isNullOrBlank(),
            )
            val rom = RomImage(Files.readAllBytes(Path.of(configured)))
            assertEquals(control.sha256, rom.sha256)

            val catalog = requireNotNull(CatalogParser.parse(rom).catalog)
            val text = catalog.defaultTextProjection()
            val evidence = text.localizedCapabilities.getValue(LocalizedTextCapability.SPECIES_DESCRIPTIONS)
            assertEquals(CapabilityStatus.AVAILABLE, evidence.status)
            assertEquals(251, evidence.coveredRecords)
            assertEquals(251, evidence.expectedRecords)
            assertTrue(
                "${control.environmentVariable} species 1 was ${text.speciesDescription(1)}",
                requireNotNull(text.speciesDescription(1)).startsWith(control.speciesOnePrefix),
            )
        }
    }

    private data class Control(
        val environmentVariable: String,
        val sha256: String,
        val speciesOnePrefix: String,
    )
}
