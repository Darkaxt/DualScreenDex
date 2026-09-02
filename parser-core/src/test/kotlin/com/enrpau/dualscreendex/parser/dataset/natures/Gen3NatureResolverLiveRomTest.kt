package com.enrpau.dualscreendex.parser.dataset.natures

import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndex
import com.enrpau.dualscreendex.parser.analysis.GbaReferenceIndexFactory
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationException
import com.enrpau.dualscreendex.parser.analysis.ParserCancellationToken
import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.Platform
import com.enrpau.dualscreendex.parser.model.RomHeader
import com.enrpau.dualscreendex.parser.text.PokemonTextCodec
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3NatureResolverLiveRomTest {
    @Test
    fun missingLanguageCodecDoesNotPreemptNatureMechanicsResolution() {
        val rom = RomImage(ByteArray(0x100))

        val result = Gen3NatureResolver.resolve(
            RomAnalysisSession(rom, RomHeader(Platform.GBA, "TEST", "TEST")),
            null,
        )

        assertTrue(result is NatureResolution.Unavailable)
        assertFalse((result as NatureResolution.Unavailable).reason.contains("language codec"))
    }

    @Test
    fun cancellationInterruptsNatureWholeRomScanning() {
        val rom = RomImage(ByteArray(0x10_000))
        val cancellation = CancelAfterChecks(successfulChecks = 3)
        val session = RomAnalysisSession(
            rom = rom,
            header = RomHeader(Platform.GBA, "TEST", "TEST"),
            gbaReferenceIndexFactory = GbaReferenceIndexFactory { _, _ ->
                GbaReferenceIndex.countsOnlyForTesting(emptyMap())
            },
            cancellation = cancellation,
        )

        assertThrows(ParserCancellationException::class.java) {
            Gen3NatureResolver.resolve(session, PokemonTextCodec.gbaEnglish)
        }
        assertEquals(4, cancellation.checks)
    }

    @Test
    fun `real Emerald FireRed Modern and Classic resolve ROM-native Nature catalogs`() {
        controls.forEach { control ->
            val path = Path.of(control.path)
            assumeTrue("missing ${control.path}", Files.isRegularFile(path))
            val rom = Files.newInputStream(path).use(RomImage::from)
            assertEquals(control.sha256, rom.sha256)

            val result = Gen3NatureResolver.resolve(
                RomAnalysisSession(rom, RomHeaderReader.read(rom)),
                PokemonTextCodec.gbaEnglish,
            )
            assertTrue("${control.label}: $result", result is NatureResolution.Resolved)
            val catalog = (result as NatureResolution.Resolved).catalog

            assertEquals(control.expectedNameTable, catalog.nameTableOffset)
            assertEquals(control.expectedStatTable, catalog.statTableOffset)
            assertEquals(control.expectedFlavorTable, catalog.flavorTableOffset)
            assertEquals(25, catalog.records.size)
            assertEquals(control.hardyName, catalog.records[0].name)
            assertEquals(control.adamantName, catalog.records[3].name)
            assertEquals(listOf(1, 0, 0, -1, 0), catalog.records[3].statModifiers)
            assertEquals(listOf(1, -1, 0, 0, 0), catalog.records[3].flavorModifiers)
            assertEquals(listOf(-1, 0, 0, 1, 0), catalog.records[15].statModifiers)
            assertEquals(listOf(-1, 1, 0, 0, 0), catalog.records[15].flavorModifiers)
            assertEquals(110, catalog.records[3].positivePercent)
            assertEquals(90, catalog.records[3].negativePercent)

            val numericOnly = Gen3NatureResolver.resolve(
                RomAnalysisSession(rom, RomHeaderReader.read(rom)),
                null,
            ) as NatureResolution.Resolved
            assertNull(numericOnly.catalog.nameTableOffset)
            assertTrue(numericOnly.catalog.records.all { it.name == null })
            assertEquals(
                catalog.records.map(NatureRecord::statModifiers),
                numericOnly.catalog.records.map(NatureRecord::statModifiers),
            )
            assertEquals(
                catalog.records.map(NatureRecord::flavorModifiers),
                numericOnly.catalog.records.map(NatureRecord::flavorModifiers),
            )
        }
    }

    @Test
    fun `integrated NatureInfo controls resolve compiled names and stat effects`() {
        integratedControls.forEach { control ->
            val path = Path.of(control.path)
            assumeTrue("missing ${control.path}", Files.isRegularFile(path))
            val rom = Files.newInputStream(path).use(RomImage::from)
            assertEquals(control.sha256, rom.sha256)

            val result = Gen3NatureResolver.resolve(
                RomAnalysisSession(rom, RomHeaderReader.read(rom)),
                PokemonTextCodec.gbaEnglish,
            )
            assertTrue("${control.label}: $result", result is NatureResolution.Resolved)
            val catalog = (result as NatureResolution.Resolved).catalog

            assertEquals(control.expectedRoot, catalog.nameTableOffset)
            assertEquals(control.expectedRoot, catalog.statTableOffset)
            assertNull(catalog.flavorTableOffset)
            assertEquals(25, catalog.records.size)
            assertEquals(25, catalog.records.map(NatureRecord::name).distinct().size)
            assertTrue(catalog.records.all { it.flavorModifiers == null })
            assertEquals(listOf(0, 0, 0, 0, 0), catalog.records[0].statModifiers)
            assertEquals(listOf(1, 0, 0, -1, 0), catalog.records[3].statModifiers)
            assertEquals(listOf(-1, 0, 0, 1, 0), catalog.records[15].statModifiers)
            assertEquals(110, catalog.records[3].positivePercent)
            assertEquals(90, catalog.records[3].negativePercent)

            val numericOnly = Gen3NatureResolver.resolve(
                RomAnalysisSession(rom, RomHeaderReader.read(rom)),
                null,
            ) as NatureResolution.Resolved
            assertNull(numericOnly.catalog.nameTableOffset)
            assertTrue(numericOnly.catalog.records.all { it.name == null })
            assertEquals(
                catalog.records.map(NatureRecord::statModifiers),
                numericOnly.catalog.records.map(NatureRecord::statModifiers),
            )
        }
    }

    @Test
    fun `Unbound resolves a complete ROM-native Nature catalog`() = assertHeldOut(heldOutControls[0])

    @Test
    fun `Odyssey resolves a complete ROM-native Nature catalog`() = assertHeldOut(heldOutControls[1])

    private fun assertHeldOut(control: HeldOutControl) {
        val path = Path.of(control.path)
        assumeTrue("missing ${control.path}", Files.isRegularFile(path))
        val rom = Files.newInputStream(path).use(RomImage::from)
        assertEquals(control.sha256, rom.sha256)

        val result = Gen3NatureResolver.resolve(
                RomAnalysisSession(rom, RomHeaderReader.read(rom)),
                PokemonTextCodec.gbaEnglish,
            )
        assertTrue("${control.label}: $result", result is NatureResolution.Resolved)
        val catalog = (result as NatureResolution.Resolved).catalog
        assertEquals(25, catalog.records.size)
        assertTrue("${control.label} flavor table", catalog.flavorTableOffset != null)
        assertTrue(catalog.records.all { it.flavorModifiers != null })
        assertTrue(catalog.records.all { !it.name.isNullOrBlank() })
        assertTrue(catalog.records.any { it.raisedStat != null && it.loweredStat != null })
    }

    private data class Control(
        val label: String,
        val path: String,
        val sha256: String,
        val expectedNameTable: Int,
        val expectedStatTable: Int,
        val expectedFlavorTable: Int,
        val hardyName: String,
        val adamantName: String,
    )

    private val controls = listOf(
        Control(
            "Emerald",
            "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - Emerald Version (USA, Europe).gba",
            "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
            0x61CB50, 0x31E818, 0x5B25A0, "HARDY", "ADAMANT",
        ),
        Control(
            "FireRed",
            "D:/Temp/PokemonHacks/roms/official/Gen III/Pokemon - FireRed Version (USA, Europe) (Rev 1).gba",
            "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
            0x463EC0, 0x252BB8, 0x25DE94, "HARDY", "ADAMANT",
        ),
        Control(
            "Modern",
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0116-a0b4e5e9c0c4/Modern Emerald (v3.5).gba",
            "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
            0x905AE4, 0x8D8D78, 0x8BB87C, "Hardy", "Adamant",
        ),
        Control(
            "Classic",
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0029-a5f22adc2c2f/Classic (v1.5.0b).gba",
            "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
            0x9A88E0, 0x35CEF4, 0x9233A8, "Hardy", "Adamant",
        ),
    )

    private val integratedControls = listOf(
        IntegratedControl(
            "Battle Theater",
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0017-f4b971d56e3f/Battle Theater (V2.3.0).gba",
            "99c84950e2be2f887a84bdc32c741c92385bb4a54843d871a8876e9b47e1d59d",
            0xC9CE88,
        ),
        IntegratedControl(
            "Dreamstone",
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0049-ab6ee58c2896/Dreamstone Mysteries.gba",
            "ac31df9cc158823861294b17bd4e66857deab2a53dd81620ddcf6fc03a6a4220",
            0xE658D8,
        ),
    )

    private val heldOutControls = listOf(
        HeldOutControl(
            "Unbound",
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0199-a275be0f927e/Unbound (v2.1.1.1).gba",
            "7aa25bbf568f7cfcf6ee1cf2e9e6ff637350b3d0705c2375cabb6baa7d9739f7",
        ),
        HeldOutControl(
            "Odyssey",
            "D:/Temp/PokemonHacks/corpus/expanded/roms/0123-5e7ce46db2ce/Odyssey (v4.1.1).gba",
            "44c7e3eafab19c39df7c39d54bafb78a1d9caf7c371244b6f5efb12cfd98d0d0",
        ),
    )

    private class CancelAfterChecks(
        private val successfulChecks: Int,
    ) : ParserCancellationToken {
        var checks: Int = 0
            private set

        override fun throwIfCancellationRequested() {
            checks += 1
            if (checks > successfulChecks) throw ParserCancellationException()
        }
    }

    private data class IntegratedControl(
        val label: String,
        val path: String,
        val sha256: String,
        val expectedRoot: Int,
    )

    private data class HeldOutControl(val label: String, val path: String, val sha256: String)
}
