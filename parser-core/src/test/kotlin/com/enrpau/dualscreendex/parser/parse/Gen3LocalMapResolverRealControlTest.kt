package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.EncounterMaterializer
import com.enrpau.dualscreendex.parser.catalog.LocalMapCatalog
import com.enrpau.dualscreendex.parser.catalog.LocalMapScenePlacement
import com.enrpau.dualscreendex.parser.catalog.MapTimeOfDay
import com.enrpau.dualscreendex.parser.catalog.TimedLocalMapRasterRenderer
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.language.defaultTextCodec
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import com.enrpau.dualscreendex.parser.model.SelectionStatus
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3LocalMapResolverRealControlTest {
    @Test
    fun officialRubyResolvesCanonicalRseLocalMaps() { assertControl(controls[0]) }

    @Test
    fun officialSapphireResolvesCanonicalRseLocalMaps() { assertControl(controls[1]) }

    @Test
    fun officialEmeraldResolvesCanonicalEmeraldLocalMaps() { assertControl(controls[2]) }

    @Test
    fun officialEmeraldRetainsStructuralLocalMapsWithoutTextAuthority() {
        assertStructuralControl(controls[2])
    }

    @Test
    fun officialFireRedResolvesCanonicalFrlgLocalMaps() { assertControl(controls[3]) }

    @Test
    fun officialLeafGreenResolvesCanonicalFrlgLocalMaps() { assertControl(controls[4]) }

    @Test
    fun modernEmeraldRetainsPrimaryAndSecondaryTilesetLocalMaps() {
        val localMaps = assertControl(controls[5])
        assertTrue(localMaps.maps.any { it.baseAreaId == 0x0009 && it.displayName == "Littleroot Town" })
        assertTrue(localMaps.timedAssets.isNotEmpty())
        assertTrue(localMaps.assets.isNotEmpty())
        val route102Key = localMaps.maps.single { it.baseAreaId == 0x0011 }.imageAssetKey
        val route102 = localMaps.timedAssets.getValue(route102Key)
        val timeHashes = listOf(
            MapTimeOfDay(12, 0),
            MapTimeOfDay(19, 0),
            MapTimeOfDay(21, 0),
            MapTimeOfDay(23, 0),
        ).map { time -> argbSha256(TimedLocalMapRasterRenderer.render(route102, time).argb) }
        assertEquals(4, timeHashes.toSet().size)
    }

    private fun assertStructuralControl(control: Control) {
        val rom = realRom(control)
        val analysis = ParserOrchestrator.analyze(rom)
        val layout = requireNotNull(
            analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout,
        )
        val family = requireNotNull(analysis.selectedFamily)
        val encounterIdDivisor = if (layout.pokeemeraldExpansion == null) 10 else 100
        val baseAreaIds = EncounterMaterializer.materialize(rom, layout)
            .mapTo(linkedSetOf()) { it.id / encounterIdDivisor }
        val session = RomAnalysisSession(rom, RomHeaderReader.read(rom))
        val named = Gen3LocalMapResolver.resolve(session, baseAreaIds, family, requireNotNull(layout.defaultTextCodec()))
        val structural = Gen3LocalMapResolver.resolve(session, baseAreaIds, family, null)

        assertTrue("${control.environmentVariable}: $named", named is LocalMapResolution.Resolved)
        assertTrue("${control.environmentVariable}: $structural", structural is LocalMapResolution.Resolved)
        val namedCatalog = (named as LocalMapResolution.Resolved).catalog.validate()
        val structuralCatalog = (structural as LocalMapResolution.Resolved).catalog.validate()
        assertEquals(namedCatalog.maps.map { it.copy(displayName = null) }, structuralCatalog.maps)
        assertEquals(namedCatalog.assets, structuralCatalog.assets)
        assertEquals(namedCatalog.timedAssets, structuralCatalog.timedAssets)
        assertEquals(namedCatalog.scenes, structuralCatalog.scenes)
        assertEquals(
            namedCatalog.pois.map { poi ->
                poi.copy(
                    displayName = null,
                    item = poi.item?.copy(displayName = null),
                    displayNamesByTrainerGender = emptyMap(),
                )
            },
            structuralCatalog.pois,
        )
    }

    private fun assertControl(control: Control): LocalMapCatalog {
        val attempt = CatalogParser.parseCatching(realRom(control))
        assertEquals(SelectionStatus.SELECTED, attempt.analysis.status)
        val catalog = requireNotNull(attempt.catalog).getOrThrow()
        val localMaps = catalog.localMaps

        assertEquals(control.mapCount, localMaps.maps.size)
        assertTrue("expected at least one connection-derived Local-map scene", localMaps.scenes.isNotEmpty())
        val placedBaseAreaIds = localMaps.scenes.flatMap { scene ->
            scene.placements.map { placement -> placement.baseAreaId }
        }.toSet()
        val controlMapsShareScene = control.sceneBaseAreaIds.isEmpty() || localMaps.scenes.any { scene ->
            scene.placements.mapTo(mutableSetOf(), LocalMapScenePlacement::baseAreaId)
                .containsAll(control.sceneBaseAreaIds)
        }
        assertTrue(
            "expected control maps in one seamless scene; " +
                "scenes=${localMaps.scenes.map { it.key to it.placements.size }}; " +
                "placed=${placedBaseAreaIds.sorted().joinToString { it.toString(16).padStart(4, '0') }}",
            controlMapsShareScene,
        )
        val localMapEvidence = catalog.capabilities.getValue(RomCapability.LOCAL_MAP)
        assertEquals(localMapEvidence.reasons.joinToString("; "), CapabilityStatus.AVAILABLE, localMapEvidence.status)
        val worldMapEvidence = catalog.capabilities.getValue(RomCapability.WORLD_MAP)
        assertEquals(worldMapEvidence.reasons.joinToString("; "), CapabilityStatus.AVAILABLE, worldMapEvidence.status)
        control.maps.forEach { expected ->
            assertMap(localMaps, expected)
        }
        return localMaps
    }

    private fun assertMap(catalog: LocalMapCatalog, expected: ExpectedMap) {
        val map = catalog.maps.single { it.baseAreaId == expected.baseAreaId }
        assertEquals(expected.gridWidth, map.gridWidth)
        assertEquals(expected.gridHeight, map.gridHeight)
        assertEquals(expected.gridWidth * 16, map.pixelWidth)
        assertEquals(expected.gridHeight * 16, map.pixelHeight)
        val argb = catalog.assets[map.imageAssetKey]?.let { png ->
            val image = requireNotNull(ImageIO.read(ByteArrayInputStream(png.bytes)))
            IntArray(image.width * image.height) { index -> image.getRGB(index % image.width, index / image.width) }
        } ?: catalog.timedAssets[map.imageAssetKey]?.let { timed ->
            TimedLocalMapRasterRenderer.render(timed, MapTimeOfDay(12, 0)).argb
        } ?: error("map has no raster asset")
        assertEquals(expected.argbSha256, argbSha256(argb))
    }

    private fun argbSha256(argb: IntArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        argb.forEach { pixel ->
            buffer.clear()
            buffer.putInt(pixel)
            digest.update(buffer.array())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun realRom(control: Control): RomImage {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also {
            assertEquals(control.romSha256, it.sha256)
        }
    }

    private data class Control(
        val environmentVariable: String,
        val romSha256: String,
        val mapCount: Int,
        val maps: List<ExpectedMap>,
        val sceneBaseAreaIds: Set<Int> = emptySet(),
    )

    private data class ExpectedMap(
        val baseAreaId: Int,
        val gridWidth: Int,
        val gridHeight: Int,
        val argbSha256: String,
    )

    private companion object {
        val rseMaps = listOf(
            ExpectedMap(
                baseAreaId = 0x0010,
                gridWidth = 20,
                gridHeight = 20,
                argbSha256 = "943f029dec2de0efad3cc520822ab01b90079d0a029133b0b5ce1d9e29e4a735",
            ),
            ExpectedMap(
                baseAreaId = 0x0011,
                gridWidth = 50,
                gridHeight = 20,
                argbSha256 = "8f0d1f2908cca9c3740b9640c99db7b8a6276383d10dc3d77bc35ce1e982be07",
            ),
        )
        val frlgMaps = listOf(
            ExpectedMap(
                baseAreaId = 0x0100,
                gridWidth = 54,
                gridHeight = 69,
                argbSha256 = "1a6c5fe8b7b2330ab3401f3515503cb242e82f376cc7952c34272353e8319837",
            ),
            ExpectedMap(
                baseAreaId = 0x013F,
                gridWidth = 51,
                gridHeight = 36,
                argbSha256 = "74c6c505e524bc9e8da9dc083e99c0dd81dfe842da950192455730a31b52cc5c",
            ),
        )
        val controls = listOf(
            Control(
                environmentVariable = "DUALDEX_OFFICIAL_RUBY_ROM",
                romSha256 = "0fdd36e92b75bed65d09df4635ab0b707b288c2bf1dc4c6e7a4a4f0eebe9d64c",
                mapCount = 394,
                maps = rseMaps,
                sceneBaseAreaIds = setOf(0x0010, 0x0011),
            ),
            Control(
                environmentVariable = "DUALDEX_OFFICIAL_SAPPHIRE_ROM",
                romSha256 = "02ca41513580a8b780989dee428df747b52a0b1a55bec617886b4059eb1152fb",
                mapCount = 394,
                maps = rseMaps,
                sceneBaseAreaIds = setOf(0x0010, 0x0011),
            ),
            Control(
                environmentVariable = "DUALDEX_OFFICIAL_EMERALD_ROM",
                romSha256 = "a9dec84dfe7f62ab2220bafaef7479da0929d066ece16a6885f6226db19085af",
                mapCount = 518,
                maps = listOf(
                    ExpectedMap(
                        baseAreaId = 0x0010,
                        gridWidth = 20,
                        gridHeight = 20,
                        argbSha256 = "f744a7ac9d86cdc5a8c773df8318152d51f273b486a589ac1400e3bafe7da212",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x0011,
                        gridWidth = 50,
                        gridHeight = 20,
                        argbSha256 = "cf4eb8b377dae9cd7ca7f3e26e42f370540aa1220899953360eaafe4da2668a8",
                    ),
                ),
                sceneBaseAreaIds = setOf(0x0010, 0x0011),
            ),
            Control(
                environmentVariable = "DUALDEX_FIRERED_ROM",
                romSha256 = "729041b940afe031302d630fdbe57c0c145f3f7b6d9b8eca5e98678d0ca4d059",
                mapCount = 425,
                maps = frlgMaps,
            ),
            Control(
                environmentVariable = "DUALDEX_LEAFGREEN_ROM",
                romSha256 = "2f978f635b9593f6ca26ec42481c53a6b39f6cddd894ad5c062c1419fac58825",
                mapCount = 425,
                maps = frlgMaps,
            ),
            Control(
                environmentVariable = "DUALDEX_MODERN_EMERALD_ROM",
                romSha256 = "21a0306c4e5b5dc15ca70b74e713e3140612c1045aa298072993a6c5dd8d6895",
                mapCount = 557,
                maps = listOf(
                    ExpectedMap(
                        baseAreaId = 0x0009,
                        gridWidth = 20,
                        gridHeight = 20,
                        argbSha256 = "fab7c44f6cadecb33a10733242c35b5632f86c645b13fd66dfd72131b58f7654",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x0010,
                        gridWidth = 20,
                        gridHeight = 20,
                        argbSha256 = "78b05f42cced70d02c30bc7443b3829032af00642c36d4b4725b55943ee72e58",
                    ),
                    ExpectedMap(
                        baseAreaId = 0x0011,
                        gridWidth = 50,
                        gridHeight = 20,
                        argbSha256 = "39f0b8578ec8933fa817d134d9e7a196bfc6da1c8e9f7d9f21380ce2ae1b1af6",
                    ),
                ),
                sceneBaseAreaIds = setOf(0x0009, 0x0010, 0x0011),
            ),
        )
    }
}
