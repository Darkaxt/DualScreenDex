package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.EncounterMaterializer
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
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

class Gen2WorldMapRealControlTest {
    @Test fun goldResolvesItsExactSourceOracle() = assertControl(CONTROLS[0])
    @Test fun silverResolvesItsExactSourceOracle() = assertControl(CONTROLS[1])
    @Test fun crystalResolvesItsExactSourceOracle() = assertControl(CONTROLS[2])

    private fun assertControl(control: Control) {
        val rom = realRom(control)
        val analysis = ParserOrchestrator.analyze(rom)
        assertEquals(control.env, SelectionStatus.SELECTED, analysis.status)
        val layout = requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout)
        val baseAreaIds = EncounterMaterializer.materialize(rom, layout).mapTo(linkedSetOf()) { it.id / 10 }

        val result = Gen2WorldMapResolver.resolve(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            baseAreaIds,
        )

        assertTrue("${control.env}: $result", result is WorldMapResolution.Resolved)
        val catalog = (result as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(listOf("gen2-johto", "gen2-kanto"), catalog.regions.map { it.key })
        catalog.regions.forEach { region ->
            assertEquals(160, region.pixelWidth)
            assertEquals(144, region.pixelHeight)
            assertEquals(20, region.gridWidth)
            assertEquals(18, region.gridHeight)
        }
        assertEquals(baseAreaIds, catalog.regions.flatMap { it.locations }.flatMapTo(linkedSetOf()) { it.baseAreaIds })
        assertEquals(JOHTO_RASTER_SHA, sha256(catalog.assets.getValue("world/gen2-johto")))
        assertEquals(KANTO_RASTER_SHA, sha256(catalog.assets.getValue("world/gen2-kanto")))
        assertEquals(control.locationSha256, locationFingerprint(catalog))
    }

    private fun realRom(control: Control): RomImage {
        val configured = System.getenv(control.env)
        assumeTrue("set ${control.env} to run this source-built control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("source-built ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(control.romSha256, it.sha256) }
    }

    private fun locationFingerprint(catalog: WorldMapCatalog): String {
        val canonical = catalog.regions.flatMap { region ->
            region.locations.flatMap { location ->
                val landmark = location.key.removePrefix("landmark-").toInt()
                val cell = location.geometry.single()
                location.baseAreaIds.map { baseAreaId ->
                    "$baseAreaId:$landmark:${cell.x},${cell.y}:${region.key.removePrefix("gen2-")}"
                }
            }
        }.sortedBy { it.substringBefore(':').toInt() }.joinToString(";")
        return sha256(canonical.toByteArray())
    }

    private fun sha256(sprite: RgbaSprite): String {
        val digest = MessageDigest.getInstance("SHA-256")
        sprite.argb.forEach { value -> digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(value).array()) }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private data class Control(val env: String, val romSha256: String, val locationSha256: String)

    private companion object {
        const val JOHTO_RASTER_SHA = "adb9cefb64aece67c7cff271b70183af5dafa7c3e95beffd31436a7cab79a5e9"
        const val KANTO_RASTER_SHA = "c53b3c2e032545fa2452bbadd4a29aea8619cc852b9ed45d17d6d8475cebe5b7"
        val CONTROLS = listOf(
            Control(
                "DUALDEX_POKEGOLD_ROM",
                "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
                "455b70cb56ebf3494334e179736a247723c83d10c41a477a5ee7239869942749",
            ),
            Control(
                "DUALDEX_POKESILVER_ROM",
                "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
                "455b70cb56ebf3494334e179736a247723c83d10c41a477a5ee7239869942749",
            ),
            Control(
                "DUALDEX_POKECRYSTAL_ROM",
                "d6702e353dcbe2d2c69183046c878ef13a0dae4006e8cdff521cca83dd1582fe",
                "355728883137963f6696793e9b5834a0155be312c8abd4d57a572c78981445d2",
            ),
        )
    }
}
