package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.EncounterMaterializer
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3WorldMapResolverRealControlTest {
    @Test
    fun officialEmeraldResolvesItsExactSourceOraclePixels() = assertControl(controls[0])

    @Test
    fun modernEmeraldResolvesItsExactSourceOraclePixels() = assertControl(controls[1])

    @Test
    fun classicResolvesItsExactSourceOraclePixels() = assertControl(controls[2])

    @Test
    fun fireRedResolvesFourExactSourceOracleRegions() = assertControl(controls[3])

    @Test
    fun leafGreenResolvesFourExactSourceOracleRegions() = assertControl(controls[4])

    private fun assertControl(control: Control) {
        val rom = realRom(control)
            val analysisStarted = System.nanoTime()
            val analysis = ParserOrchestrator.analyze(rom)
            val analysisMs = (System.nanoTime() - analysisStarted) / 1_000_000
            assertEquals("${control.environmentVariable} parser selection", SelectionStatus.SELECTED, analysis.status)
            val layout = analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout
            requireNotNull(layout)
            val encounterBaseIds = EncounterMaterializer.materialize(rom, layout)
                .mapTo(linkedSetOf()) { it.id / 10 }
            val resolutionStarted = System.nanoTime()
            val resolution = Gen3WorldMapResolver.resolve(
                RomAnalysisSession(rom, RomHeaderReader.read(rom)),
                encounterBaseIds,
            )
            val resolutionMs = (System.nanoTime() - resolutionStarted) / 1_000_000
            assertTrue("${control.environmentVariable}: $resolution", resolution is Gen3WorldMapResolution.Resolved)
            val resolved = resolution as Gen3WorldMapResolution.Resolved
            assertTrue(
                "${control.environmentVariable} must report one-pass function indexing",
                resolved.reasons.any { it.matches(Regex("indexed [1-9][0-9]* distinct compiled reference sites once")) },
            )
            println(
                "world-map-control ${control.environmentVariable} analysisMs=$analysisMs " +
                    "resolutionMs=$resolutionMs " +
                    resolved.reasons.first { it.startsWith("indexed ") },
            )
            val catalog = resolved.catalog.validate()

            assertEquals("${control.environmentVariable} region count", control.argbHashes.size, catalog.regions.size)
            assertEquals(control.argbHashes.size, catalog.assets.size)
            val actualHashes = catalog.regions.map { region ->
                assertEquals(control.pixelWidth, region.pixelWidth)
                assertEquals(control.pixelHeight, region.pixelHeight)
                assertEquals(control.gridWidth, region.gridWidth)
                assertEquals(15, region.gridHeight)
                sha256(requireNotNull(catalog.assets[region.imageAssetKey]))
            }
            assertEquals("${control.environmentVariable} normalized pixels", control.argbHashes, actualHashes)
    }

    private fun realRom(control: Control): RomImage {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(control.romSha256, it.sha256) }
    }

    private fun sha256(sprite: RgbaSprite): String {
        val digest = MessageDigest.getInstance("SHA-256")
        sprite.argb.forEach { value -> digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()) }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class Control(
        val environmentVariable: String,
        val romSha256: String,
        val pixelWidth: Int,
        val pixelHeight: Int,
        val gridWidth: Int,
        val argbHashes: List<String>,
    )

    private companion object {
        val controls = listOf(
            Control(
                "DUALDEX_OFFICIAL_EMERALD_ROM",
                "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                224,
                120,
                28,
                listOf("1c3a1bf13c851dcc707f1f3f71c8f90e703a0faf0832917a0195618952a77aab"),
            ),
            Control(
                "DUALDEX_MODERN_EMERALD_ROM",
                "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
                224,
                120,
                28,
                listOf("0163d9b5e747d788db925776c25a087a1cc4bbfa34fd3e021580aa8756717fb0"),
            ),
            Control(
                "DUALDEX_CLASSIC_ROM",
                "01c0177b2498e1842a1bf9ee2ddac145fb95275321bd3813dbf17341d63ad16c",
                224,
                120,
                28,
                listOf("dc326776034d066f0b2691e14f2325e78d6761b40db6da52c8454ab8fe46a46f"),
            ),
            Control(
                "DUALDEX_FIRERED_ROM",
                "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                176,
                120,
                22,
                listOf(
                    "250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654",
                    "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135",
                    "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a",
                    "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b",
                ),
            ),
            Control(
                "DUALDEX_LEAFGREEN_ROM",
                "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
                176,
                120,
                22,
                listOf(
                    "250195a226d642147bb594e30cb03596ef94dd88237204f761fb164286d53654",
                    "8e1d6f588bf4bd24913a559e70f6af8f42c32d484f523ee197a09b73c03b4135",
                    "eebdbb58c4d7fbbc875d6fbc465751625c26baf2a2c728c06fa8331d92fd7e4a",
                    "b96065661b1848860cc69db7e9370194df740568e4352d7288e2b4ee17640a3b",
                ),
            ),
        )
    }
}
