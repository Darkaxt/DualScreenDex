package com.enrpau.dualscreendex.parser.catalog

import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class RomDerivedThemeLiveRomTest {
    @Test
    fun exactReferenceRomsProduceStableReadableThemesAcrossFreshParses() {
        val actual = controls.associate { control ->
            val configured = System.getenv(control.environmentVariable)
            assumeTrue("set ${control.environmentVariable} to run this exact theme control", !configured.isNullOrBlank())
            val path = Path.of(requireNotNull(configured))
            assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
            val firstRom = RomImage(Files.readAllBytes(path))
            val secondRom = RomImage(Files.readAllBytes(path))
            assertEquals(control.sha256, firstRom.sha256)
            assertEquals(firstRom.sha256, secondRom.sha256)

            val first = requireNotNull(CatalogParser.parse(firstRom).catalog).theme.validate()
            val second = requireNotNull(CatalogParser.parse(secondRom).catalog).theme.validate()

            assertEquals(first, second)
            control.label to first
        }

        assertEquals(expectedThemes, actual)
    }

    private data class Control(val label: String, val environmentVariable: String, val sha256: String)

    private companion object {
        val controls = listOf(
            Control("Red", "DUALDEX_POKERED_ROM", "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b"),
            Control("Crystal", "DUALDEX_POKECRYSTAL_ROM", "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2"),
            Control("Emerald", "DUALDEX_OFFICIAL_EMERALD_ROM", "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af"),
            Control("Unbound", "DUALDEX_UNBOUND_ROM", "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7"),
            Control("Odyssey", "DUALDEX_ODYSSEY_ROM", "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0"),
        )
        val expectedThemes = linkedMapOf(
            "Red" to theme(
                setOf(
                    CatalogThemeAssetClass.TRAINER,
                    CatalogThemeAssetClass.WORLD_MAP,
                    CatalogThemeAssetClass.LOCAL_MAP,
                    CatalogThemeAssetClass.SPECIES,
                ),
                CatalogThemeTokens(
                    0x4C4C4C, 0x616161, 0x6E6E6E, 0x444444, 0xFCFCFC, 0x929292,
                    0xFDFDFD, 0x010101, 0x030303, 0x000000, 0x030303, 0xFFFFFF,
                ),
            ),
            "Crystal" to theme(
                setOf(
                    CatalogThemeAssetClass.TRAINER,
                    CatalogThemeAssetClass.WORLD_MAP,
                    CatalogThemeAssetClass.SPECIES,
                ),
                CatalogThemeTokens(
                    0xE6360A, 0xE64D20, 0x6767EA, 0x3F3F91, 0xEDFCC5, 0x899272,
                    0xF7FDE5, 0x010101, 0x030303, 0x000000, 0x03FB03, 0x000000,
                ),
            ),
            "Emerald" to theme(
                setOf(
                    CatalogThemeAssetClass.TRAINER,
                    CatalogThemeAssetClass.WORLD_MAP,
                    CatalogThemeAssetClass.LOCAL_MAP,
                    CatalogThemeAssetClass.SPECIES,
                ),
                CatalogThemeTokens(
                    0x0245E6, 0x205AE8, 0xDCDC02, 0x888801, 0xFCFCFC, 0x929292,
                    0xFDFDFD, 0x010101, 0x030303, 0x000000, 0x356FFB, 0x000000,
                ),
            ),
            "Unbound" to theme(
                setOf(
                    CatalogThemeAssetClass.TRAINER,
                    CatalogThemeAssetClass.WORLD_MAP,
                    CatalogThemeAssetClass.LOCAL_MAP,
                    CatalogThemeAssetClass.SPECIES,
                ),
                CatalogThemeTokens(
                    0xE63D02, 0xE85320, 0x0E73DD, 0x084789, 0xFCFCFC, 0x929292,
                    0xFDFDFD, 0x010101, 0x030303, 0x000000, 0xFBD30B, 0x000000,
                ),
            ),
            "Odyssey" to theme(
                setOf(
                    CatalogThemeAssetClass.TRAINER,
                    CatalogThemeAssetClass.WORLD_MAP,
                    CatalogThemeAssetClass.LOCAL_MAP,
                    CatalogThemeAssetClass.SPECIES,
                ),
                CatalogThemeTokens(
                    0x0253E6, 0x2067E8, 0xDCC002, 0x887701, 0xFCFCFC, 0x929292,
                    0xFDFDFD, 0x010101, 0x030303, 0x000000, 0xFB03FB, 0x000000,
                ),
            ),
        )

        fun theme(assetClasses: Set<CatalogThemeAssetClass>, tokens: CatalogThemeTokens) = CatalogTheme(
            CatalogThemeMethod.MULTI_ASSET_QUANTIZATION,
            assetClasses,
            contrastCorrected = true,
            tokens = tokens,
        )
    }
}
