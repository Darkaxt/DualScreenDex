package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.CatalogParser
import com.enrpau.dualscreendex.parser.catalog.EncounterArea
import com.enrpau.dualscreendex.parser.catalog.EncounterMaterializer
import com.enrpau.dualscreendex.parser.catalog.EncounterMethods
import com.enrpau.dualscreendex.parser.catalog.Gen2CompiledEncounterResolver
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.CapabilityStatus
import com.enrpau.dualscreendex.parser.model.RomCapability
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen2WorldMapHackRealControlTest {
    @Test fun bronze2ResolvesThroughItsCompiledOneThresholdRegionConsumer() {
        val rom = bronze2Rom()
        val result = resolve(rom)

        assertTrue("Bronze2: $result", result is WorldMapResolution.Resolved)
        val catalog = (result as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(listOf("gen2-johto", "gen2-kanto"), catalog.regions.map { it.key })
        assertEquals(101, catalog.regions.flatMap { it.locations }.flatMap { it.baseAreaIds }.toSet().size)
        assertEquals(listOf(34 to 68, 26 to 33), catalog.regions.map { region ->
            region.locations.size to region.locations.sumOf { it.baseAreaIds.size }
        })
        assertEquals(
            listOf(BRONZE2_JOHTO_RASTER_SHA, BRONZE2_KANTO_RASTER_SHA),
            catalog.regions.map { sha256(catalog.assets.getValue(it.imageAssetKey)) },
        )
        assertEquals(BRONZE2_LOCATION_SHA, locationFingerprint(catalog))
    }

    @Test fun crystalLegacyResolvesSharedExtendedPaletteMapConsumer() {
        val result = resolve(realRom("DUALDEX_CRYSTAL_LEGACY_ROM", CRYSTAL_LEGACY_SHA))

        assertTrue("Crystal Legacy: $result", result is WorldMapResolution.Resolved)
        val catalog = (result as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(listOf("gen2-johto", "gen2-kanto"), catalog.regions.map { it.key })
        assertEquals(114, catalog.regions.flatMap { it.locations }.flatMap { it.baseAreaIds }.toSet().size)
        assertEquals(listOf(45 to 78, 34 to 36), catalog.regions.map { region ->
            region.locations.size to region.locations.sumOf { it.baseAreaIds.size }
        })
        assertEquals(
            listOf(CRYSTAL_LEGACY_JOHTO_RASTER_SHA, CRYSTAL_LEGACY_KANTO_RASTER_SHA),
            catalog.regions.map { sha256(catalog.assets.getValue(it.imageAssetKey)) },
        )
        assertEquals(CRYSTAL_LEGACY_LOCATION_SHA, locationFingerprint(catalog))
    }

    @Test fun orangeResolvesExpandedCoreAndSingleEncounterBearingRegion() {
        val rom = realRom("DUALDEX_ORANGE_ROM", ORANGE_SHA)
        val core = requireNotNull(Gen2CompiledCoreResolver.resolve(rom))
        assertEquals(253, core.speciesCount)
        assertEquals(27, core.tables.baseStats?.recordSize)
        assertEquals(253, requireNotNull(Gen2CompiledSpriteResolver.resolve(rom, core.speciesCount)).count)
        val encounters = encounters(rom)
        val baseAreaIds = encounters.mapTo(linkedSetOf()) { it.id / 10 }
        assertEquals(253, encounters.size)
        assertEquals(86, baseAreaIds.size)
        assertTrue(encounters.all { area ->
            area.slots.isNotEmpty() && area.slots.all { slot ->
                slot.speciesId in 1..253 && slot.minimumLevel in 1..100 &&
                    slot.maximumLevel in slot.minimumLevel..100 && (slot.weight ?: 0) > 0
            }
        })

        val result = resolve(rom, encounters)

        assertTrue("Orange: ${result::class.simpleName}", result is WorldMapResolution.Resolved)
        val catalog = (result as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(listOf("gen2-johto"), catalog.regions.map { it.key })
        val region = catalog.regions.single()
        assertNull(region.displayName)
        assertEquals(54 to 86, region.locations.size to region.locations.sumOf { it.baseAreaIds.size })
        assertEquals(baseAreaIds, region.locations.flatMapTo(linkedSetOf()) { it.baseAreaIds })
        assertEquals(ORANGE_RASTER_SHA, sha256(catalog.assets.getValue(region.imageAssetKey)))
        assertEquals(ORANGE_NAMED_LOCATION_SHA, namedLocationFingerprint(catalog))

        val second = (resolve(rom, encounters) as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(ORANGE_RASTER_SHA, sha256(second.assets.getValue(region.imageAssetKey)))
        assertEquals(ORANGE_NAMED_LOCATION_SHA, namedLocationFingerprint(second))

        val integratedCatalog = requireNotNull(CatalogParser.parse(rom).catalog)
        assertEquals(253, integratedCatalog.speciesById.size)
        assertTrue(integratedCatalog.speciesById.values.all { it.sprite.status == CapabilityStatus.AVAILABLE })
        assertEquals(
            CapabilityStatus.AVAILABLE,
            integratedCatalog.capabilities.getValue(RomCapability.WORLD_MAP).status,
        )
        assertEquals(ORANGE_RASTER_SHA, sha256(integratedCatalog.worldMaps.assets.getValue(region.imageAssetKey)))
        assertEquals(ORANGE_NAMED_LOCATION_SHA, namedLocationFingerprint(integratedCatalog.worldMaps))
    }

    @Test fun peridotRetainsCompiledEncounterSupportButFailsClosedAtLandmarkJoin() {
        val rom = realRom("DUALDEX_PERIDOT_ROM", PERIDOT_SHA)
        val core = requireNotNull(Gen2CompiledCoreResolver.resolve(rom))
        assertEquals(253, core.speciesCount)
        assertEquals(34, core.tables.baseStats?.recordSize)
        val compiled = Gen2CompiledEncounterResolver.resolve(rom, core.speciesCount)
        assertTrue(compiled.detected)
        assertEquals(253, requireNotNull(compiled.layout).maximumSpeciesId)
        val encounters = encounters(rom)
        assertEquals(434, encounters.size)
        assertEquals(140, encounters.map { it.id / 10 }.toSet().size)
        assertEquals(
            mapOf(
                EncounterMethods.WATER to 65,
                EncounterMethods.GRASS_MORNING to 123,
                EncounterMethods.GRASS_DAY to 123,
                EncounterMethods.GRASS_NIGHT to 123,
            ),
            encounters.groupingBy { it.methodId }.eachCount(),
        )
        assertEquals(PERIDOT_ENCOUNTER_SHA, encounterFingerprint(encounters))

        val result = resolve(rom, encounters)

        assertTrue("Peridot must remain unavailable: $result", result is WorldMapResolution.Unavailable)
        assertEquals("landmark-join", (result as WorldMapResolution.Unavailable).stage)
        assertTrue(result.reason.contains("classifiers=1"))
        val integratedCatalog = requireNotNull(CatalogParser.parse(rom).catalog)
        assertTrue(integratedCatalog.speciesById.values.all { it.sprite.status == CapabilityStatus.AVAILABLE })
        assertEquals(
            CapabilityStatus.NOT_FOUND,
            integratedCatalog.capabilities.getValue(RomCapability.WORLD_MAP).status,
        )
        assertTrue(integratedCatalog.worldMaps.regions.isEmpty())
        assertTrue(integratedCatalog.worldMaps.assets.isEmpty())
    }

    @Test fun anniversaryCrystalResolvesGuardedMapAndCompactEncounterConsumers() {
        val rom = realRom("DUALDEX_ANNIVERSARY_CRYSTAL_ROM", ANNIVERSARY_CRYSTAL_SHA)
        val encounters = encounters(rom)
        assertEquals(410, encounters.size)
        assertEquals(137, encounters.map { it.id / 10 }.toSet().size)
        assertEquals(
            mapOf(
                EncounterMethods.WATER to 59,
                EncounterMethods.GRASS_MORNING to 117,
                EncounterMethods.GRASS_DAY to 117,
                EncounterMethods.GRASS_NIGHT to 117,
            ),
            encounters.groupingBy { it.methodId }.eachCount(),
        )
        assertTrue(encounters.all { area ->
            area.slots.isNotEmpty() && area.slots.all { slot ->
                slot.speciesId in 1..251 && slot.minimumLevel in 1..100 &&
                    slot.maximumLevel in slot.minimumLevel..100 && (slot.weight ?: 0) > 0
            }
        })
        assertEquals(ANNIVERSARY_CRYSTAL_ENCOUNTER_SHA, encounterFingerprint(encounters))

        val result = resolve(rom, encounters)

        assertTrue("Anniversary Crystal: $result", result is WorldMapResolution.Resolved)
        val catalog = (result as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(listOf("gen2-johto", "gen2-kanto"), catalog.regions.map { it.key })
        assertEquals(137, catalog.regions.flatMap { it.locations }.flatMap { it.baseAreaIds }.toSet().size)
        assertEquals(listOf(42 to 65, 39 to 72), catalog.regions.map { region ->
            region.locations.size to region.locations.sumOf { it.baseAreaIds.size }
        })
        assertEquals(
            listOf(ANNIVERSARY_CRYSTAL_JOHTO_RASTER_SHA, ANNIVERSARY_CRYSTAL_KANTO_RASTER_SHA),
            catalog.regions.map { sha256(catalog.assets.getValue(it.imageAssetKey)) },
        )
        assertEquals(ANNIVERSARY_CRYSTAL_LOCATION_SHA, locationFingerprint(catalog))
    }

    @Test fun kalosCrystalResolvesThroughPairedCompiledCoreConsumers() {
        val rom = realRom("DUALDEX_KALOS_CRYSTAL_ROM", KALOS_CRYSTAL_SHA)
        val encounters = encounters(rom)
        assertEquals(335, encounters.size)
        assertEquals(114, encounters.map { it.id / 10 }.toSet().size)
        assertEquals(
            mapOf(
                EncounterMethods.WATER to 62,
                EncounterMethods.GRASS_MORNING to 91,
                EncounterMethods.GRASS_DAY to 91,
                EncounterMethods.GRASS_NIGHT to 91,
            ),
            encounters.groupingBy { it.methodId }.eachCount(),
        )
        assertTrue(encounters.all { area ->
            area.slots.isNotEmpty() && area.slots.all { slot ->
                slot.speciesId in 1..229 && slot.minimumLevel in 1..100 &&
                    slot.maximumLevel in slot.minimumLevel..100 && (slot.weight ?: 0) > 0
            }
        })
        assertEquals(KALOS_CRYSTAL_ENCOUNTER_SHA, encounterFingerprint(encounters))

        val result = resolve(rom, encounters)

        assertTrue("Kalos Crystal: $result", result is WorldMapResolution.Resolved)
        val catalog = (result as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(listOf("gen2-johto", "gen2-kanto"), catalog.regions.map { it.key })
        assertEquals(114, catalog.regions.flatMap { it.locations }.flatMap { it.baseAreaIds }.toSet().size)
        assertEquals(listOf(45 to 78, 34 to 36), catalog.regions.map { region ->
            region.locations.size to region.locations.sumOf { it.baseAreaIds.size }
        })
        assertEquals(
            listOf(KALOS_CRYSTAL_JOHTO_RASTER_SHA, KALOS_CRYSTAL_KANTO_RASTER_SHA),
            catalog.regions.map { sha256(catalog.assets.getValue(it.imageAssetKey)) },
        )
        assertEquals(KALOS_CRYSTAL_LOCATION_SHA, locationFingerprint(catalog))
    }

    @Test fun gold97ReforgedResolvesCompiledMoveAndExtendedPaletteConsumers() {
        assertGold97Variant(
            env = "DUALDEX_GOLD97_ROM",
            expectedRomSha = GOLD97_SHA,
            expectedEncounterSha = GOLD97_ENCOUNTER_SHA,
        )
    }

    @Test fun silver97ReforgedResolvesCompiledMoveAndExtendedPaletteConsumers() {
        assertGold97Variant(
            env = "DUALDEX_SILVER97_ROM",
            expectedRomSha = SILVER97_SHA,
            expectedEncounterSha = SILVER97_ENCOUNTER_SHA,
        )
    }

    @Test fun malformedOneThresholdFailsClosed() {
        val source = bronze2Bytes()
        val classifier = findOneThresholdClassifier(source)
        source[classifier.thresholdOffset] = (source[classifier.shipOffset].u8() + 1).toByte()

        val result = resolve(RomImage(source))

        assertTrue("malformed threshold must fail closed: $result", result is WorldMapResolution.Unavailable)
        assertEquals("landmark-join", (result as WorldMapResolution.Unavailable).stage)
    }

    @Test fun mismatchedDynamicSpecialCallFailsClosed() {
        val source = bronze2Bytes()
        val classifier = findOneThresholdClassifier(source)
        source[classifier.backupCallTargetOffset] = (source[classifier.backupCallTargetOffset].toInt() xor 1).toByte()

        val result = resolve(RomImage(source))

        assertTrue("mismatched map-location call must fail closed: $result", result is WorldMapResolution.Unavailable)
        assertEquals("landmark-join", (result as WorldMapResolution.Unavailable).stage)
    }

    @Test fun bronzeNullControlledLandmarkNameFailsClosed() {
        val result = resolve(realRom("DUALDEX_BRONZE_ROM", BRONZE_SHA))

        assertTrue("runtime-dependent NULL name must fail closed: $result", result is WorldMapResolution.Unavailable)
        assertEquals("landmark-join", (result as WorldMapResolution.Unavailable).stage)
    }

    private fun assertGold97Variant(
        env: String,
        expectedRomSha: String,
        expectedEncounterSha: String,
    ) {
        val rom = realRom(env, expectedRomSha)
        val encounters = encounters(rom)
        assertEquals(347, encounters.size)
        assertEquals(116, encounters.map { it.id / 10 }.toSet().size)
        assertEquals(
            mapOf(
                EncounterMethods.WATER to 71,
                EncounterMethods.GRASS_MORNING to 92,
                EncounterMethods.GRASS_DAY to 92,
                EncounterMethods.GRASS_NIGHT to 92,
            ),
            encounters.groupingBy { it.methodId }.eachCount(),
        )
        assertTrue(encounters.all { area ->
            area.slots.isNotEmpty() && area.slots.all { slot ->
                slot.speciesId in 1..251 && slot.minimumLevel in 1..100 &&
                    slot.maximumLevel in slot.minimumLevel..100 && (slot.weight ?: 0) > 0
            }
        })
        assertEquals(expectedEncounterSha, encounterFingerprint(encounters))

        val result = resolve(rom, encounters)

        assertTrue("$env: $result", result is WorldMapResolution.Resolved)
        val catalog = (result as WorldMapResolution.Resolved).catalog.validate()
        assertEquals(listOf("gen2-johto", "gen2-kanto"), catalog.regions.map { it.key })
        assertEquals(116, catalog.regions.flatMap { it.locations }.flatMap { it.baseAreaIds }.toSet().size)
        assertEquals(listOf(44 to 75, 26 to 41), catalog.regions.map { region ->
            region.locations.size to region.locations.sumOf { it.baseAreaIds.size }
        })
        assertEquals(
            listOf(GOLD97_JOHTO_RASTER_SHA, GOLD97_KANTO_RASTER_SHA),
            catalog.regions.map { sha256(catalog.assets.getValue(it.imageAssetKey)) },
        )
        assertEquals(GOLD97_LOCATION_SHA, locationFingerprint(catalog))

        val integratedCatalog = requireNotNull(CatalogParser.parse(rom).catalog)
        assertEquals(
            CapabilityStatus.AVAILABLE,
            integratedCatalog.capabilities.getValue(RomCapability.WORLD_MAP).status,
        )
        assertEquals(
            listOf(GOLD97_JOHTO_RASTER_SHA, GOLD97_KANTO_RASTER_SHA),
            integratedCatalog.worldMaps.regions.map { region ->
                sha256(integratedCatalog.worldMaps.assets.getValue(region.imageAssetKey))
            },
        )
        assertEquals(GOLD97_LOCATION_SHA, locationFingerprint(integratedCatalog.worldMaps))
    }

    private fun resolve(rom: RomImage): WorldMapResolution = resolve(rom, encounters(rom))

    private fun resolve(rom: RomImage, encounters: List<EncounterArea>): WorldMapResolution =
        Gen2WorldMapResolver.resolve(
            RomAnalysisSession(rom, RomHeaderReader.read(rom)),
            encounters.mapTo(linkedSetOf()) { it.id / 10 },
        )

    private fun encounters(rom: RomImage): List<EncounterArea> {
        val analysis = ParserOrchestrator.analyze(rom)
        val layout = requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout)
        return EncounterMaterializer.materialize(rom, layout)
    }

    private fun bronze2Rom(): RomImage {
        return realRom("DUALDEX_BRONZE2_ROM", BRONZE2_SHA)
    }

    private fun bronze2Bytes(): ByteArray {
        val configured = System.getenv("DUALDEX_BRONZE2_ROM")
        assumeTrue("set DUALDEX_BRONZE2_ROM to run this real control", !configured.isNullOrBlank())
        return Files.readAllBytes(Path.of(requireNotNull(configured)))
    }

    private fun realRom(env: String, expectedSha: String): RomImage {
        val configured = System.getenv(env)
        assumeTrue("set $env to run this real control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real control ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(expectedSha, it.sha256) }
    }

    private fun findOneThresholdClassifier(bytes: ByteArray): ClassifierOffsets {
        for (ship in 11 until bytes.size - 32) {
            if (
                bytes[ship].u8() != 0xfe || bytes[ship + 2].u8() != 0x28 ||
                bytes[ship + 4].u8() != 0xfe || bytes[ship + 5].u8() != 0 || bytes[ship + 6].u8() != 0x20
            ) continue
            val check = ship + 19
            if (
                bytes[check].u8() == 0xfe && bytes[check + 2].u8() == 0x30 &&
                bytes[check + 4].u8() == 0xaf && bytes[check + 5].u8() == 0xc9 &&
                bytes[check + 6].u8() == 0x3e && bytes[check + 7].u8() == 1 && bytes[check + 8].u8() == 0xc9
            ) return ClassifierOffsets(ship + 1, check + 1, ship + 17)
        }
        error("complete one-threshold classifier not found")
    }

    private fun encounterFingerprint(areas: List<EncounterArea>): String {
        val canonical = areas.sortedBy { it.id }.joinToString(";") { area ->
            val slots = area.slots.joinToString(",") { slot ->
                "${slot.speciesId}:${slot.minimumLevel}-${slot.maximumLevel}:${slot.weight}"
            }
            "${area.id}:${area.methodId}:${area.windows.map { it.name }.sorted()}:$slots"
        }
        return sha256(canonical.toByteArray())
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

    private fun namedLocationFingerprint(catalog: WorldMapCatalog): String {
        val canonical = catalog.regions.flatMap { region ->
            region.locations.map { location ->
                val cells = location.geometry.joinToString(",") { "${it.x}:${it.y}:${it.width}:${it.height}" }
                "${region.key}:${location.key}:${location.displayName}:" +
                    "${location.baseAreaIds.sorted().joinToString(",")}:$cells"
            }
        }.joinToString(";")
        return sha256(canonical.toByteArray())
    }

    private fun sha256(sprite: RgbaSprite): String {
        val digest = MessageDigest.getInstance("SHA-256")
        sprite.argb.forEach { digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(it).array()) }
        return digest.digest().toHex()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun Byte.u8(): Int = toInt() and 0xff

    private data class ClassifierOffsets(
        val shipOffset: Int,
        val thresholdOffset: Int,
        val backupCallTargetOffset: Int,
    )

    private companion object {
        const val ORANGE_SHA = "037f5ba913953f2387175c5e0549347d162ef3b224d25660e8055acdac4564be"
        const val ORANGE_RASTER_SHA = "d913a0621593e68935b10fbf8259455f3e53c56381d7e967780fbe3fa597cacb"
        const val ORANGE_NAMED_LOCATION_SHA =
            "954ba7995a85480f358bd812510dc6869756d5163144d71f7f3bb1c8797a02b4"
        const val PERIDOT_SHA = "22dc749a396a353d6724aa96e28d4fef89d4b89e38e594852c8930c3c4a7f160"
        const val PERIDOT_ENCOUNTER_SHA =
            "523aad5c34175a89597b6b865e8cf49da5706dd6eb243215778ec95c86e9592e"
        const val BRONZE_SHA = "3cf45157784fe70ddf9f07639236022321bf62b70797c412457625b2704c3269"
        const val BRONZE2_SHA = "87758fbc06a9abc73577bbc16d184bc3fb6f35d5abf22d776156629b5e5ae811"
        const val BRONZE2_JOHTO_RASTER_SHA = "6e36d20b35f904a06fec5e11750c8938b9163f2d05ccdc848bd44b16e883497c"
        const val BRONZE2_KANTO_RASTER_SHA = "17a94384a359aaa5c9179249800442388dd1042fe9956a83f1fad319c7e275f1"
        const val BRONZE2_LOCATION_SHA = "9646f17b9ca2a9559f3f2c6bf50ebac374171f8d8683ce75039f91c67329bf8a"
        const val CRYSTAL_LEGACY_SHA = "18153207488a9e2b4837d677ec9f1240dc2674a29dd6a0319553b73cafccceaa"
        const val CRYSTAL_LEGACY_JOHTO_RASTER_SHA = "9d348e028f32fe38f23c3ae561ee2f512fd41fa360d9313d412b5337c178411a"
        const val CRYSTAL_LEGACY_KANTO_RASTER_SHA = "ae6bd49974c5d87260f8567b0810bdd0d9c0aabfe1a453a3d7459a91dc1faaa6"
        const val CRYSTAL_LEGACY_LOCATION_SHA = "355728883137963f6696793e9b5834a0155be312c8abd4d57a572c78981445d2"
        const val ANNIVERSARY_CRYSTAL_SHA = "638dfbf61aa7a6e0bf1dcf75518dd69ed9e2f038f1dc09ab318ef4bbcdc29f5c"
        const val ANNIVERSARY_CRYSTAL_JOHTO_RASTER_SHA =
            "adb9cefb64aece67c7cff271b70183af5dafa7c3e95beffd31436a7cab79a5e9"
        const val ANNIVERSARY_CRYSTAL_KANTO_RASTER_SHA =
            "074aacb3e08341b1293aa445cf4c4bc398d54e297b810b7677f8ca515f41da91"
        const val ANNIVERSARY_CRYSTAL_LOCATION_SHA =
            "7dd5ca12862111503bb473fc9cdf627a30242e3ae2b5ba1dc23cbaab5795d85b"
        const val ANNIVERSARY_CRYSTAL_ENCOUNTER_SHA =
            "8bc6b49c14234887082577358111426afd8f499661d1dd5ae56cd36a012536fb"
        const val KALOS_CRYSTAL_SHA =
            "7cd8957e47a04bf0542de5d6a65affb369704e85ce11e03022be491be7dc1050"
        const val KALOS_CRYSTAL_JOHTO_RASTER_SHA =
            "adb9cefb64aece67c7cff271b70183af5dafa7c3e95beffd31436a7cab79a5e9"
        const val KALOS_CRYSTAL_KANTO_RASTER_SHA =
            "c53b3c2e032545fa2452bbadd4a29aea8619cc852b9ed45d17d6d8475cebe5b7"
        const val KALOS_CRYSTAL_LOCATION_SHA =
            "355728883137963f6696793e9b5834a0155be312c8abd4d57a572c78981445d2"
        const val KALOS_CRYSTAL_ENCOUNTER_SHA =
            "287025214d7e1eb4c5aa43a744924b133dded32cd7cc0db3eeb6d8397aede804"
        const val GOLD97_SHA =
            "5e0c4688abd5ce2cb00d76902301791d5dfd196a99ff1e764268dffb196c50c3"
        const val SILVER97_SHA =
            "6d491ec85788e967aface80b61f91936bd84deb9239fef1d010f93962fe58828"
        const val GOLD97_JOHTO_RASTER_SHA =
            "918bdd844e7a55c84e7e1c88275ba0dcf517e51623f3d1d31908e7fece2cbdfe"
        const val GOLD97_KANTO_RASTER_SHA =
            "9ca35356ada5a30589dfa1ce8459b9043b86057a31d14f751cc7157c9687e8ea"
        const val GOLD97_LOCATION_SHA =
            "9ef198994a7b16dc395876e7f0fb98ac45ba5a23210777b99a36a2d070029af7"
        const val GOLD97_ENCOUNTER_SHA =
            "ca44fdb56e0e6d9efa35a51016cb244b49249b7b35d692eeed32360d4385ca78"
        const val SILVER97_ENCOUNTER_SHA =
            "ae7f69524f2fde1202875a4ff3037dc4380d364e93c43efec3546eabaa9fbffd"
    }
}
