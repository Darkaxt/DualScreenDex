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

class Gen1WorldMapRealControlTest {
    @Test fun redResolvesItsExactSourceOracle() = assertControl(CONTROLS[0])
    @Test fun blueResolvesItsExactSourceOracle() = assertControl(CONTROLS[1])
    @Test fun yellowResolvesItsExactSourceOracle() = assertControl(CONTROLS[2])
    @Test fun shinRedResolvesRelocatedCompiledNamesAndMap() = assertControl(CONTROLS[3])
    @Test fun shinBlueResolvesRelocatedCompiledNamesAndMap() = assertControl(CONTROLS[4])
    @Test fun beyondRedResolvesCompiledExpandedCoreAndMap() = assertControl(CONTROLS[5])
    @Test fun beyondBlueResolvesCompiledExpandedCoreAndMap() = assertControl(CONTROLS[6])

    private fun assertControl(control: Control) {
        val rom = realRom(control)
        val analysis = ParserOrchestrator.analyze(rom)
        assertEquals(control.env, SelectionStatus.SELECTED, analysis.status)
        val layout = requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout)
        control.speciesCount?.let { assertEquals(it, layout.speciesCount) }
        control.baseRecordSize?.let { assertEquals(it, layout.tables.baseStats?.recordSize) }
        val baseAreaIds = EncounterMaterializer.materialize(rom, layout).mapTo(linkedSetOf()) { it.id / 10 }
        control.baseAreaCount?.let { assertEquals(it, baseAreaIds.size) }

        val result = Gen1WorldMapResolver.resolve(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            baseAreaIds,
        )

        assertTrue("${control.env}: $result", result is WorldMapResolution.Resolved)
        val catalog = (result as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(1, catalog.regions.size)
        val region = catalog.regions.single()
        assertEquals("gen1-kanto", region.key)
        assertEquals(160, region.pixelWidth)
        assertEquals(144, region.pixelHeight)
        assertEquals(20, region.gridWidth)
        assertEquals(18, region.gridHeight)
        assertEquals(baseAreaIds, region.locations.flatMapTo(linkedSetOf()) { it.baseAreaIds })
        val locationNames = region.locations.mapTo(linkedSetOf()) { it.displayName }
        assertTrue(
            "${control.env}: missing ${control.locationNames - locationNames}; actual=$locationNames",
            locationNames.containsAll(control.locationNames),
        )
        val argbSha256 = sha256(catalog.assets.getValue(region.imageAssetKey))
        val locationSha256 = locationFingerprint(region)
        assertEquals(control.argbSha256, argbSha256)
        assertEquals(control.locationSha256, locationSha256)
    }

    private fun realRom(control: Control): RomImage {
        val configured = System.getenv(control.env)
        assumeTrue("set ${control.env} to run this source-built control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("source-built ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(control.romSha256, it.sha256) }
    }

    private fun locationFingerprint(region: com.enrpau.dualscreendex.parser.catalog.WorldMapRegion): String {
        val canonical = region.locations.flatMap { location ->
            location.baseAreaIds.map { baseAreaId ->
                val cell = location.geometry.single()
                "$baseAreaId:${cell.x},${cell.y}"
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

    private data class Control(
        val env: String,
        val romSha256: String,
        val argbSha256: String,
        val locationSha256: String,
        val speciesCount: Int? = null,
        val baseRecordSize: Int? = null,
        val baseAreaCount: Int? = null,
        val locationNames: Set<String> = emptySet(),
    )

    private companion object {
        const val RASTER_SHA = "d55384218790ed7744af655bef486bcba8b1a932aa81e3d5701871f8ac60eca4"
        val CONTROLS = listOf(
            Control(
                "DUALDEX_POKERED_ROM",
                "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
                RASTER_SHA,
                "165454e8cc5450e8a1edd0c6dcbfebb47e3254f1eb198d396efcdd5ef97d4433",
            ),
            Control(
                "DUALDEX_POKEBLUE_ROM",
                "2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d",
                RASTER_SHA,
                "165454e8cc5450e8a1edd0c6dcbfebb47e3254f1eb198d396efcdd5ef97d4433",
            ),
            Control(
                "DUALDEX_POKEYELLOW_ROM",
                "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
                RASTER_SHA,
                "3c2f8177ae8d2073822e04d85bc76fb7f45e056e48c6ba5bf7297e22ef54dfbf",
            ),
            Control(
                "DUALDEX_SHIN_RED_ROM",
                "024a1c4dab1b12d0b963c6cf756d2c1082de0ccd53fe31384787dcf34edef718",
                RASTER_SHA,
                "165454e8cc5450e8a1edd0c6dcbfebb47e3254f1eb198d396efcdd5ef97d4433",
            ),
            Control(
                "DUALDEX_SHIN_BLUE_ROM",
                "25e39e5ef5ef0de0f7faf481827927a4033ac1d31782a2b9be9a8412d8fd1158",
                RASTER_SHA,
                "165454e8cc5450e8a1edd0c6dcbfebb47e3254f1eb198d396efcdd5ef97d4433",
            ),
            Control(
                "DUALDEX_BEYOND_RED_ROM",
                "3640ed0493287136cd9321cb3428f44113e87354cf90402665ba60e41c8fc61a",
                "f4f50cf1b04029ca5dfcc853e8e164c76da9105a6ec578c666d828f7716de16e",
                "3cced54dde0a817beb6458f9f406337457b049e6ad92a8c072b004e045abd527",
                speciesCount = 254,
                baseRecordSize = 29,
                baseAreaCount = 83,
                locationNames = setOf("POKéMON TOWER", "DIGLETT CAVE", "POKéMON MANSION"),
            ),
            Control(
                "DUALDEX_BEYOND_BLUE_ROM",
                "33eacb9917498505ac0dc669323d506cde7ee4c2e3a17a80ca2f9d8944ef217c",
                "f4f50cf1b04029ca5dfcc853e8e164c76da9105a6ec578c666d828f7716de16e",
                "3cced54dde0a817beb6458f9f406337457b049e6ad92a8c072b004e045abd527",
                speciesCount = 254,
                baseRecordSize = 29,
                baseAreaCount = 83,
                locationNames = setOf("POKéMON TOWER", "DIGLETT CAVE", "POKéMON MANSION"),
            ),
        )
    }
}
