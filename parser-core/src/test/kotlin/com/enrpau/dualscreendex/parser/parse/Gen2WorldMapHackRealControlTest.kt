package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.analysis.RomAnalysisSession
import com.enrpau.dualscreendex.parser.catalog.EncounterMaterializer
import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.catalog.WorldMapCatalog
import com.enrpau.dualscreendex.parser.detect.RomHeaderReader
import com.enrpau.dualscreendex.parser.io.RomImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.assertEquals
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

    @Test fun malformedOneThresholdFailsClosed() {
        val source = bronze2Bytes()
        val classifier = findOneThresholdClassifier(source)
        source[classifier.thresholdOffset] = source[classifier.shipOffset]

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

    private fun resolve(rom: RomImage): WorldMapResolution {
        val analysis = ParserOrchestrator.analyze(rom)
        val layout = requireNotNull(analysis.probes.single { it.family == analysis.selectedFamily }.resolvedLayout)
        val baseAreaIds = EncounterMaterializer.materialize(rom, layout).mapTo(linkedSetOf()) { it.id / 10 }
        return Gen2WorldMapResolver.resolve(RomAnalysisSession(rom, RomHeaderReader.read(rom)), baseAreaIds)
    }

    private fun bronze2Rom(): RomImage {
        val configured = System.getenv("DUALDEX_BRONZE2_ROM")
        assumeTrue("set DUALDEX_BRONZE2_ROM to run this real control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("Bronze2 ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also {
            assertEquals("87758fbc06a9abc73577bbc16d184bc3fb6f35d5abf22d776156629b5e5ae811", it.sha256)
        }
    }

    private fun bronze2Bytes(): ByteArray {
        val configured = System.getenv("DUALDEX_BRONZE2_ROM")
        assumeTrue("set DUALDEX_BRONZE2_ROM to run this real control", !configured.isNullOrBlank())
        return Files.readAllBytes(Path.of(requireNotNull(configured)))
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
        const val BRONZE2_JOHTO_RASTER_SHA = "6e36d20b35f904a06fec5e11750c8938b9163f2d05ccdc848bd44b16e883497c"
        const val BRONZE2_KANTO_RASTER_SHA = "17a94384a359aaa5c9179249800442388dd1042fe9956a83f1fad319c7e275f1"
        const val BRONZE2_LOCATION_SHA = "9646f17b9ca2a9559f3f2c6bf50ebac374171f8d8683ce75039f91c67329bf8a"
    }
}
