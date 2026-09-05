package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.LanguageResolutionStatus
import com.enrpau.dualscreendex.parser.model.EngineFamily
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Opt-in nine-cell B001 confirmation. Hashes identify test inputs only, never production routing. */
@RunWith(Parameterized::class)
class NativeOfficialLanguageLiveRomTest(
    private val folder: String, private val sha: String, private val family: EngineFamily,
    private val codecId: String, private val speciesWidth: Int,
) {
    @Test fun actualParserEntrypointResolvesNativeNamesAndExactCodec() {
        val configured = System.getenv("DUALDEX_NATIVE_CONTROLS")
        assumeTrue("set DUALDEX_NATIVE_CONTROLS for the bounded nine-control gate", !configured.isNullOrBlank())
        val directory = Path.of(requireNotNull(configured)).resolve(folder)
        val path = Files.list(directory).use { paths -> paths.filter { Files.isRegularFile(it) }.toList().single() }
        val rom = RomImage(Files.readAllBytes(path))
        assertEquals(sha, rom.sha256)
        val result = ParserOrchestrator.analyze(rom)
        val probe = result.probes.single { it.family == family }
        val layout = probe.resolvedLayout
        val manifest = layout?.languageManifest
        println("NATIVE_CONTROL $folder sha=$sha selection=${result.status}/${result.selectedFamily} language=${manifest?.status}/${manifest?.defaultProjection()?.codecId} species=${layout?.tables?.speciesNames} moves=${layout?.tables?.moveNames} diagnostics=${manifest?.diagnostics}")
        assertEquals(SelectionStatus.SELECTED, result.status)
        assertEquals(family, result.selectedFamily)
        assertEquals(LanguageResolutionStatus.RESOLVED, manifest?.status)
        assertEquals(codecId, manifest?.defaultProjection()?.codecId)
        assertEquals(speciesWidth, manifest?.defaultProjection()?.localizedTables?.speciesNames?.recordSize)
        assertEquals(if (speciesWidth == 6) 8 else 0, manifest?.defaultProjection()?.localizedTables?.moveNames?.recordSize)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun controls(): List<Array<Any>> = listOf(
            arrayOf("ja/RED_BLUE", "3f0dc460ca8d06be1c9ac96307c939c0ea7baa366b40c2f1f4ad63242b6c4816", EngineFamily.RED_BLUE, "gb-gen1-ja-red-blue", 5),
            arrayOf("ja/YELLOW", "1349408f328f633b33e059e654edabd19810530df9c883eda03a85d5bb10161a", EngineFamily.YELLOW, "gb-gen1-ja-yellow", 5),
            arrayOf("ja/GOLD_SILVER", "27a07a1d3faf9c6a0b1b60d5e88ee3a4159a751a47b4c46ab09f1202d52bac3e", EngineFamily.GOLD_SILVER, "gb-gen2-ja", 5),
            arrayOf("ja/CRYSTAL", "136ada06cb68656b7de475fa4b278d37dbeff8f5257e7dfdf7f4a4aec19a90f3", EngineFamily.CRYSTAL, "gb-gen2-ja", 5),
            arrayOf("ja/RUBY_SAPPHIRE", "a7ea012b67a27da2893bfdfcb5f64915607b26904b4fc635a1055e8e40e692ab", EngineFamily.RUBY_SAPPHIRE, "gba-gen3-ja-ruby-sapphire", 6),
            arrayOf("ja/EMERALD", "33f5610b9186b4add09fef68895deb00f552b997b3d133b5a961e5123506343c", EngineFamily.EMERALD, "gba-gen3-ja-emerald-frlg", 6),
            arrayOf("ja/FIRERED_LEAFGREEN", "cec5fc4dbe38cd8026bd6664a1a041d9dc91e8d4249bab04e7bde70c3cdf4e06", EngineFamily.FIRERED_LEAFGREEN, "gba-gen3-ja-emerald-frlg", 6),
            arrayOf("ko/GOLD", "9c273e86e6120c6a038160ccb0153b8b20425b84fc08a496281c1d1bcac492f6", EngineFamily.GOLD_SILVER, "gb-gen2-ko", 10),
            arrayOf("ko/SILVER", "ebbac63c0c4309c82dbb6723e7163369784f962b4fd3e2f486075307c3008a22", EngineFamily.GOLD_SILVER, "gb-gen2-ko", 10),
        )
    }
}
