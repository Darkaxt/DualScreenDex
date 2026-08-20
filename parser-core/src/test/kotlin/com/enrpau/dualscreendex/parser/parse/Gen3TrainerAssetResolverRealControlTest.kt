package com.enrpau.dualscreendex.parser.parse

import com.enrpau.dualscreendex.parser.io.RomImage
import com.enrpau.dualscreendex.parser.model.EngineFamily
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

class Gen3TrainerAssetResolverRealControlTest {
    @Test
    fun officialEmeraldProducesTwoPlayerPortraitsAndEightHoennBadges() {
        val configured = System.getenv("DUALDEX_OFFICIAL_EMERALD_ROM")
        assumeTrue("set DUALDEX_OFFICIAL_EMERALD_ROM to run this live-ROM regression", !configured.isNullOrBlank())
        val path = Path.of(requireNotNull(configured))
        assumeTrue("live ROM does not exist: $path", Files.isRegularFile(path))

        val catalog = requireNotNull(
            Gen3TrainerAssetResolver.resolve(
                RomImage(Files.readAllBytes(path)),
                EngineFamily.EMERALD,
            ),
        )
        assertEquals(
            mapOf(0 to "trainer/avatar/male", 1 to "trainer/avatar/female"),
            catalog.avatarAssetKeys,
        )
        assertEquals((1..8).map { "trainer/badge/$it" }, catalog.badgeAssetKeys)
        assertEquals(catalog.avatarAssetKeys.values.toSet() + catalog.badgeAssetKeys, catalog.assets.keys)
        catalog.avatarAssetKeys.values.forEach { key ->
            assertEquals(64, catalog.assets.getValue(key).width)
            assertEquals(64, catalog.assets.getValue(key).height)
        }
        catalog.badgeAssetKeys.forEach { key ->
            assertEquals(16, catalog.assets.getValue(key).width)
            assertEquals(16, catalog.assets.getValue(key).height)
        }
        assertEquals(EXPECTED_ARGB_HASHES, catalog.assets.mapValues { argbHash(it.value.argb) })
    }

    private fun argbHash(argb: IntArray): String {
        val bytes = ByteBuffer.allocate(argb.size * 4).order(ByteOrder.BIG_ENDIAN)
        argb.forEach(bytes::putInt)
        return MessageDigest.getInstance("SHA-256").digest(bytes.array()).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        val EXPECTED_ARGB_HASHES = mapOf(
            "trainer/avatar/male" to "86a27bea1436640e331f6faedbcad9a6b1af097d54c07b5fe489565dd62e8235",
            "trainer/avatar/female" to "88ef40596e677e75ba4dc105d6ea8df727232fdb806f6ee5611611230b196593",
            "trainer/badge/1" to "ee1b49619687b62d478a92054b3e75f9028cb7901f8bfd0bdd788f9dfe9d8429",
            "trainer/badge/2" to "107fa5d2a6f380e99d9c5141a6fed52ce540f32579fddcf0dffa62f3c07bf138",
            "trainer/badge/3" to "2e16817fc400e69c768e67dff1da551d0145729ecc3c1c80811873c433317330",
            "trainer/badge/4" to "a48ffbc2b0be04d200d378f37c2ffa3ef47ad1cd9ecaf7620bb0f08b1eb8c351",
            "trainer/badge/5" to "c8a979194068163caf855d5cbe7c97735690759a4f41b0feca5322f7dde828f7",
            "trainer/badge/6" to "2e408e4a5dff3bda654a06ac3c56f150e764f2ca2d6a7739937587fe17fe3cde",
            "trainer/badge/7" to "1338f8d00dc2b77552955e2436398b66825aa5c807103a0d1576e9ee27941d83",
            "trainer/badge/8" to "c5f84d82e64d66d1fd477f47a6f3fbedb358400ca10889c3d802c6351563e529",
        )
    }
}
