package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.catalog.RgbaSprite
import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GbTrainerAssetResolverRealControlTest {
    @Test
    fun officialRedResolvesWalkingFrame() = assertControl(controls[0])

    @Test
    fun officialBlueResolvesWalkingFrame() = assertControl(controls[1])

    @Test
    fun officialYellowResolvesWalkingFrame() = assertControl(controls[2])

    @Test
    fun officialGoldResolvesWalkingFrame() = assertControl(controls[3])

    @Test
    fun officialSilverResolvesWalkingFrame() = assertControl(controls[4])

    @Test
    fun officialCrystalResolvesBothWalkingFrames() = assertControl(controls[5])

    @Test
    fun malformedSupportedRomFailsClosed() {
        assertNull(GbTrainerAssetResolver.resolve(RomImage(ByteArray(0x8000)), EngineFamily.RED_BLUE))
        assertNull(GbTrainerAssetResolver.resolve(RomImage(ByteArray(0x8000)), EngineFamily.CRYSTAL))
    }

    private fun assertControl(control: Control) {
        val assets = requireNotNull(GbTrainerAssetResolver.resolve(realRom(control), control.family))

        assertEquals(control.frames.keys, assets.overworldAssetKeys.keys)
        assertTrue(assets.avatarAssetKeys.isEmpty())
        assertTrue(assets.badgeAssetKeys.isEmpty())
        assertEquals(control.frames.size, assets.assets.size)
        control.frames.forEach { (gender, expected) ->
            val key = assets.overworldAssetKeys.getValue(gender)
            val frame = assets.assets.getValue(key)
            assertFrame(frame, expected)
        }
    }

    private fun assertFrame(frame: RgbaSprite, expected: ExpectedFrame) {
        assertEquals(16, frame.width)
        assertEquals(16, frame.height)
        val occupied = frame.argb.count { it ushr 24 != 0 }
        assertTrue(occupied in 32..240)
        assertTrue(frame.argb.any { it == 0 })
        assertEquals(expected.argbSha256, frame.argb.argbSha256())
    }

    private fun realRom(control: Control): RomImage {
        val configured = System.getenv(control.environmentVariable)
        assumeTrue("set ${control.environmentVariable} to run this real-ROM control", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("real ROM does not exist: $path", Files.isRegularFile(path))
        return RomImage(Files.readAllBytes(path)).also { assertEquals(control.romSha256, it.sha256) }
    }

    private fun IntArray.argbSha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteBuffer.allocate(Int.SIZE_BYTES)
        forEach { color ->
            buffer.clear()
            buffer.putInt(color)
            digest.update(buffer.array())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private data class Control(
        val environmentVariable: String,
        val romSha256: String,
        val family: EngineFamily,
        val frames: Map<Int, ExpectedFrame>,
    )

    private data class ExpectedFrame(val argbSha256: String)

    private companion object {
        const val GEN1_HASH = "7791ee23f26161ad56c75a547eec3b461eb3296a0f46afe3167b9bfd16b48894"
        const val GEN2_MALE_HASH = "600b591f0234d30eb23c5aad976c5ddaa9462332eabbd38c6b0fdecde125de13"
        const val CRYSTAL_FEMALE_HASH = "239f5a51b2714fd69c3ba576934896a29ae80203091f7613941c8115f30cbf1c"
        val controls = listOf(
            Control(
                "DUALDEX_POKERED_ROM",
                "5ca7ba01642a3b27b0cc0b5349b52792795b62d3ed977e98a09390659af96b7b",
                EngineFamily.RED_BLUE,
                mapOf(0 to ExpectedFrame(GEN1_HASH)),
            ),
            Control(
                "DUALDEX_POKEBLUE_ROM",
                "2a951313c2640e8c2cb21f25d1db019ae6245d9c7121f754fa61afd7bee6452d",
                EngineFamily.RED_BLUE,
                mapOf(0 to ExpectedFrame(GEN1_HASH)),
            ),
            Control(
                "DUALDEX_POKEYELLOW_ROM",
                "8cbaa499397e4f1a679c992ea9382a2dd7942ab398b48c19829c2d9529de47bf",
                EngineFamily.YELLOW,
                mapOf(0 to ExpectedFrame(GEN1_HASH)),
            ),
            Control(
                "DUALDEX_POKEGOLD_ROM",
                "fb0016d27b1e5374e1ec9fcad60e6628d8646103b5313ca683417f52b97e7e4e",
                EngineFamily.GOLD_SILVER,
                mapOf(0 to ExpectedFrame(GEN2_MALE_HASH)),
            ),
            Control(
                "DUALDEX_POKESILVER_ROM",
                "72b190859a59623cbef6c49d601f8de52c1d2331b4f08a8d2acc17274fc19a8c",
                EngineFamily.GOLD_SILVER,
                mapOf(0 to ExpectedFrame(GEN2_MALE_HASH)),
            ),
            Control(
                "DUALDEX_POKECRYSTAL_ROM",
                "fdcc3c8c43813cf8731fc037d2a6d191bac75439c34b24ba1c27526e6acdc8a2",
                EngineFamily.CRYSTAL,
                mapOf(
                    0 to ExpectedFrame(GEN2_MALE_HASH),
                    1 to ExpectedFrame(CRYSTAL_FEMALE_HASH),
                ),
            ),
        )
    }
}
